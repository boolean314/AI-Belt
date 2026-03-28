package com.example.ai_belt_mobile.navigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviListener
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.enums.TransportType
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.AMapLaneInfo
import com.amap.api.navi.model.AMapModelCross
import com.amap.api.navi.model.AMapNaviCameraInfo
import com.amap.api.navi.model.AMapNaviCross
import com.amap.api.navi.model.AMapNaviLocation
import com.amap.api.navi.model.AMapNaviRouteNotifyData
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo
import com.amap.api.navi.model.AMapServiceAreaInfo
import com.amap.api.navi.model.AMapTravelInfo
import com.amap.api.navi.model.AimLessModeCongestionInfo
import com.amap.api.navi.model.AimLessModeStat
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.NaviLatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch


import com.autonavi.base.amap.mapcore.tools.GLConvertUtil
import com.example.ai_belt_mobile.voice.SparkChainTTSManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 导航管理器 (高德地图版)
 * 实现目标方位角（Bearing） vs. 手机朝向角（Heading）的逻辑，传给智能腰带
 */
class NavigationManager private constructor(private val context: Context) : AMapNaviListener,
    SensorEventListener {
    private val ttsManager = SparkChainTTSManager.getInstance()
    private var isNavigating = false
    private var mAMapNavi: AMapNavi? = null
    private var geocodeSearch: GeocodeSearch? = null
    private var poiSearch: PoiSearch? = null

    // 传感器相关
    private var sensorManager: SensorManager? = null
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var currentHeading: Float = 0f // 手机朝向角 (0-360)

    // 目标坐标 (用于计算Bearing)
    private var destLatLon: LatLonPoint? = null
    private var curLocation: AMapNaviLocation? = null

    // 增加一个 Flow 用于专门分发偏角数据
    private val _beltAngleFlow = MutableSharedFlow<Float>(extraBufferCapacity = 1)
    val beltAngleFlow = _beltAngleFlow.asSharedFlow()

    // 外部回调给腰带
    var beltCallback: BeltNavigationCallback? = null

    interface BeltNavigationCallback {
/**
 * 更新传送带导航状态的方法
 * @param relativeAngle 相对角度，表示目标方向与当前方向的偏移量，单位为度
 * @param distance 到目标的距离，单位为厘米
 */
        fun onBeltNavigationUpdate(relativeAngle: Float, distance: Int)
        fun onNaviTextUpdate(text: String)
        fun onNaviStart()
        fun onNaviStop()
    }

    companion object {
        @Volatile
        private var instance: NavigationManager? = null

        fun getInstance(context: Context): NavigationManager {
            return instance ?: synchronized(this) {
                instance ?: NavigationManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    fun init() {
        // 安全初始化高德导航实例
        try {
            if (mAMapNavi == null) {
                mAMapNavi = AMapNavi.getInstance(context.applicationContext)
                mAMapNavi?.addAMapNaviListener(this)
                Log.i("NavigationManager", "AMapNavi 初始化成功")
            }
        } catch (e: Exception) {
            Log.e("NavigationManager", "AMapNavi 实例化异常", e)
            e.printStackTrace()
        }

        // 初始化地理编码搜索
        try {
            if (geocodeSearch == null) {
                geocodeSearch = GeocodeSearch(context.applicationContext)
            }
        } catch (e: Exception) {
            Log.e("NavigationManager", "GeocodeSearch 实例化异常", e)
        }

        // 初始化传感器
        if (sensorManager == null) {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }
    }

    fun startWalkingNavigation(
        startLocation: Location,
        destination: String,
        onNavigationStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (mAMapNavi == null) {
            onError("导航服务未初始化")
            return
        }



        Thread {
            try {
                ttsManager.speak("正在搜索目的地")
                if (isNavigating) {
                    Handler(Looper.getMainLooper()).post { onError("导航已在进行中") }
                    return@Thread
                }

                // 构造POI搜索对象
                val query = PoiSearch.Query(destination, "", "")
                query.pageSize = 10 // 设置每页最多返回多少条poiitem
                query.pageNum = 0 // 设置查询页码

                poiSearch = PoiSearch(context, query)
                poiSearch?.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                    override fun onPoiSearched(result: PoiResult?, errorCode: Int) {
                        if (errorCode == 1000 && result != null && result.pois.isNotEmpty()) {
                            // 获取第一个POI结果
                            val poiItem = result.pois[0]
                            destLatLon = poiItem.latLonPoint

                            Log.i(
                                "NavigationManager",
                                "POI搜索成功，目标坐标: ${destLatLon?.latitude}, ${destLatLon?.longitude}"
                            )
                            Log.i("NavigationManager", "POI名称: ${poiItem.title}")
                            Log.i("NavigationManager", "POI地址: ${poiItem.snippet}")

                            val startPoint =
                                NaviLatLng(startLocation.latitude, startLocation.longitude)
                            val endPoint = NaviLatLng(destLatLon!!.latitude, destLatLon!!.longitude)
                            //渲染路线
                            mAMapNavi!!.travelInfo = AMapTravelInfo(TransportType.Walk)
                            val isSuccess =
                                mAMapNavi?.calculateWalkRoute(startPoint, endPoint) ?: false
                            if (!isSuccess) {
                                Handler(Looper.getMainLooper()).post { onError("发起算路失败") }
                                isNavigating = false
                            } else {
                                Log.i("NavigationManager", "发起步行算路请求成功")
                                isNavigating = true
                                Handler(Looper.getMainLooper()).post { onNavigationStarted() }
                            }
                        } else {
                            // 如果POI搜索失败，尝试使用地理编码
                            fallbackToGeocode(
                                startLocation,
                                destination,
                                onNavigationStarted,
                                onError
                            )
                        }
                    }

                    override fun onPoiItemSearched(
                        p0: PoiItem?,
                        p1: Int
                    ) {
                        TODO("Not yet implemented")
                    }


                })

                // 发送POI搜索请求
                poiSearch?.searchPOIAsyn()

            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { onError("导航启动失败: ${e.message}") }
                isNavigating = false
            }
        }.start()
    }

    // POI搜索失败时回退到地理编码
    private fun fallbackToGeocode(
        startLocation: Location,
        destination: String,
        onNavigationStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        Log.i("NavigationManager", "POI搜索失败，尝试使用地理编码")
        ttsManager.speak("POI搜索失败，尝试使用地理编码")

        geocodeSearch?.setOnGeocodeSearchListener(object :
            GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(p0: RegeocodeResult?, p1: Int) {}

            override fun onGeocodeSearched(result: GeocodeResult?, errorCode: Int) {
                if (errorCode == 1000 && result != null && result.geocodeAddressList.isNotEmpty()) {
                    val geocodeAddress = result.geocodeAddressList[0]
                    destLatLon = geocodeAddress.latLonPoint

                    Log.i(
                        "NavigationManager",
                        "地理编码成功，目标坐标: ${destLatLon?.latitude}, ${destLatLon?.longitude}"
                    )

                    val startPoint = NaviLatLng(startLocation.latitude, startLocation.longitude)
                    val endPoint = NaviLatLng(destLatLon!!.latitude, destLatLon!!.longitude)
                    val isSuccess = mAMapNavi?.calculateWalkRoute(startPoint, endPoint) ?: false
                    if (!isSuccess) {
                        Handler(Looper.getMainLooper()).post { onError("发起算路失败") }
                        isNavigating = false
                    } else {
                        Log.i("NavigationManager", "发起步行算路请求成功")
                        isNavigating = true
                        Handler(Looper.getMainLooper()).post { onNavigationStarted() }
                    }
                } else {
                    Handler(Looper.getMainLooper()).post { onError("地址解析失败，错误码：$errorCode") }
                    isNavigating = false
                }
            }
        })

        val query = GeocodeQuery(destination, "")
        geocodeSearch?.getFromLocationNameAsyn(query)
    }

    fun stopNavigation() {
        try {
            mAMapNavi?.stopNavi()
            isNavigating = false
            unregisterSensors()
            beltCallback?.onNaviStop()
            Log.i("NavigationManager", "导航已停止")
            ttsManager.speak("导航已停止")
        } catch (e: Exception) {
            Log.e("NavigationManager", "停止导航失败", e)
        }
    }

    fun release() {
        stopNavigation()
        mAMapNavi?.removeAMapNaviListener(this)
        mAMapNavi = null
        geocodeSearch = null
        poiSearch = null
        sensorManager = null
    }

    // --- 传感器监听 (Heading计算) ---
    private fun registerSensors() {
        sensorManager?.let {
            it.registerListener(
                this,
                it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL
            )
            it.registerListener(
                this,
                it.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    private fun unregisterSensors() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.e("NavigationManager", "传感器注销失败", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }

        if (SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerReading,
                magnetometerReading
            )
        ) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthInDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            currentHeading = if (azimuthInDegrees < 0) azimuthInDegrees + 360 else azimuthInDegrees

            // 每次Heading更新时，重新计算一次相对角度给腰带，但是这样不对吧，稍微偏一点就给腰带传太频繁了
            //先注释掉不然一直输出日志
            // calculateAndSendBeltData(0)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- 核心算法：计算Bearing与Relative Angle ---
    private var lastDistance = 0
    private fun calculateAndSendBeltData(distance: Int, nextLat: Double, nextLon: Double) {
        if (!isNavigating || curLocation == null || destLatLon == null) return
        if (distance > 0) lastDistance = distance
        // 目标方位角 Bearing (如果是沿着路线，可以用naviInfo的下一个节点)
        // 这里使用两点经纬度计算绝对角度
        val bearing = calculateBearing(
            curLocation!!.coord.latitude, curLocation!!.coord.longitude,
            nextLat, nextLon
        )
        // 相对角度 Relative Angle
        var relativeAngle = bearing - currentHeading
        if (relativeAngle < -180) relativeAngle += 360
        if (relativeAngle > 180) relativeAngle -= 360
        // 回调给腰带 (例如: relativeAngle > 0 偏右，< 0 偏左)
        beltCallback?.onBeltNavigationUpdate(relativeAngle.toFloat(), lastDistance)
        _beltAngleFlow.tryEmit(relativeAngle.toFloat())
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLonRad = Math.toRadians(lon2 - lon1)

        val y = sin(dLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)
        val brng = Math.toDegrees(atan2(y, x))
        return (brng + 360) % 360
    }

    // --- AMapNaviListener 接口实现 ---

    override fun onCalculateRouteSuccess(p0: IntArray?) {
    }

    override fun onCalculateRouteFailure(p0: Int) {
    }

    override fun onLocationChange(location: AMapNaviLocation?) {
        location?.let {
            curLocation = it
        }
    }

    //手机朝向或者位置发送变化时会触发,又好像是1s一回调
    override fun onNaviInfoUpdate(naviInfo: NaviInfo?) {
        naviInfo?.let { info ->
            val path = mAMapNavi?.naviPath
            if (path != null) {
                // 获取所有路段 (Steps)
                val steps = path.steps
                val curStepIndex = info.curStep
                // 确保索引有效且有下一段路
                if (curStepIndex < steps.size) {
                    val currentStep = steps[curStepIndex]
                    // 每一个 Step 由多个坐标点组成，最后一个点通常是转弯处（即下一路段起始点）
                    val coords = currentStep.coords
                    if (coords.isNotEmpty()) {
                        val nextNode = coords.last() // 取当前路段的终点坐标
                        val distanceToNextNode = info.curStepRetainDistance
                        // 调用计算逻辑
                        calculateAndSendBeltData(
                            distanceToNextNode,
                            nextNode.latitude,
                            nextNode.longitude
                        )
                    }
                }
            }
        }
    }

    override fun onGetNavigationText(type: Int, text: String?) {
        text?.let {
            Log.i("NavigationManager", "导航语音播报: $it")
            ttsManager.speak(it)
            beltCallback?.onNaviTextUpdate(it)
        }
    }

    override fun onEndEmulatorNavi() {}
    override fun onArriveDestination() {
        ttsManager.speak("已到达目的地附近，导航结束")
        stopNavigation()
    }

    // --- 其他必须实现但暂不处理的方法 ---
    override fun onInitNaviFailure() {}
    override fun onInitNaviSuccess() {}
    override fun onStartNavi(p0: Int) {}
    override fun onTrafficStatusUpdate() {}
    override fun onGetNavigationText(p0: String?) {}
    override fun onCalculateRouteFailure(p0: AMapCalcRouteResult?) {
        Log.e("NavigationManager", "算路失败，错误码: $p0")
        ttsManager.speak("路线规划失败")
        isNavigating = false
    }

    override fun onCalculateRouteSuccess(p0: AMapCalcRouteResult?) {
        Log.i("NavigationManager", "算路成功")
        // 获取路线数据对象
        val naviPaths = mAMapNavi?.naviPaths
        Log.i("NavigationManager", "路线数量: ${naviPaths?.size}")
        // 启动导航
        mAMapNavi?.startNavi(NaviType.GPS) // 步行导航使用GPS类型
        registerSensors()
        beltCallback?.onNaviStart()
    }

    override fun onReCalculateRouteForYaw() {}
    override fun onReCalculateRouteForTrafficJam() {}
    override fun onArrivedWayPoint(p0: Int) {}
    override fun onGpsOpenStatus(p0: Boolean) {}

    override fun updateCameraInfo(p0: Array<out AMapNaviCameraInfo>?) {}
    override fun updateIntervalCameraInfo(
        p0: AMapNaviCameraInfo?,
        p1: AMapNaviCameraInfo?,
        p2: Int
    ) {
    }
    override fun onServiceAreaUpdate(p0: Array<out AMapServiceAreaInfo>?) {}
    override fun showCross(p0: AMapNaviCross?) {}
    override fun hideCross() {}
    override fun showModeCross(p0: AMapModelCross?) {}
    override fun hideModeCross() {}
    override fun showLaneInfo(p0: Array<out AMapLaneInfo>?, p1: ByteArray?, p2: ByteArray?) {}
    override fun showLaneInfo(p0: AMapLaneInfo?) {}
    override fun hideLaneInfo() {}
    override fun notifyParallelRoad(p0: Int) {}
    override fun OnUpdateTrafficFacility(p0: Array<out AMapNaviTrafficFacilityInfo>?) {}
    override fun OnUpdateTrafficFacility(p0: AMapNaviTrafficFacilityInfo?) {}
    override fun updateAimlessModeStatistics(p0: AimLessModeStat?) {}
    override fun updateAimlessModeCongestionInfo(p0: AimLessModeCongestionInfo?) {}
    override fun onPlayRing(p0: Int) {}
    override fun onNaviRouteNotify(p0: AMapNaviRouteNotifyData?) {}
    override fun onGpsSignalWeak(p0: Boolean) {}
}