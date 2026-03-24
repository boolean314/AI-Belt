package com.example.ai_belt_mobile.navigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Message
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.baidu.mapapi.walknavi.WalkNavigateHelper
import com.baidu.mapapi.walknavi.adapter.IWRouteGuidanceListener
import com.baidu.mapapi.walknavi.model.IWRouteIconInfo
import com.baidu.mapapi.walknavi.model.RouteGuideKind
import com.baidu.mapapi.walknavi.model.WalkSimpleMapInfo
import com.example.ai_belt_mobile.voice.BaiduTTSManager
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.app.ActivityCompat

/**
 * 步行导航Activity
 * 用于显示导航界面和处理导航状态
 */
class WalkNaviActivity : AppCompatActivity() {

    private lateinit var walkNavigateHelper: WalkNavigateHelper
    private val ttsManager = BaiduTTSManager.getInstance()
    private lateinit var navigationManager: NavigationManager
    private lateinit var sensorManager: SensorManager

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }

            // 计算旋转矩阵
            if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
                // 计算方向角（azimuth, pitch, roll）
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                // azimuth是绕Z轴的旋转角度，范围为-π到π，0表示正北
                val azimuthInRadians = orientationAngles[0]
                val azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()

                // 转换为0-360度
                val direction = if (azimuthInDegrees < 0) azimuthInDegrees + 360 else azimuthInDegrees
                //拿到面向的角度
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 初始化导航管理器（使用单例）
        navigationManager = NavigationManager.getInstance(this)

        // 2. 初始化TTS管理器
        ttsManager.init(this)

        // 3. 获取步行导航助手实例
        walkNavigateHelper = WalkNavigateHelper.getInstance()

        // 4. 初始化传感器管理器，用于获取设备朝向
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // 注册传感器监听器
        sensorManager.registerListener(
            sensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )
        sensorManager.registerListener(
            sensorEventListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            SensorManager.SENSOR_DELAY_NORMAL
        )

        // 检查定位权限
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("WalkNaviActivity", "定位权限未授予")
            return
        }

        // 5. 创建导航视图并设置为内容视图
        val view = walkNavigateHelper.onCreate(this)
        setContentView(view)

        // 6. 设置导航状态监听器
        setRouteGuidanceListener()

        // 7. 开始步行导航
        startWalkNavi()
    }

    private var isFirstDistanceUpdate = true
    private var isFirstTimeUpdate = true

    /**
     * 设置导航状态监听器
     */
    private fun setRouteGuidanceListener() {
        walkNavigateHelper.setRouteGuidanceListener(
            this,
            object : IWRouteGuidanceListener {

                override fun onRoadGuideTextUpdate(
                    p0: CharSequence?,
                    p1: CharSequence?
                ) {
                    val text = p1?.toString() ?: p0?.toString()
                    text?.let {
                        Log.i("WalkNaviActivity111111111111111", "导航指令: $it")

                    }
                }

                //CharSequence版本
                override fun onRemainDistanceUpdate(p0: CharSequence?) {
                    Log.i("WalkNaviActivity", "剩余距离: $p0")
                    if (isFirstDistanceUpdate) {
                        ttsManager.speak("全程${p0?.toString() ?: ""}")
                        isFirstDistanceUpdate = false
                    }
                }

                //Int版本（必须写）
                override fun onRemainDistanceUpdate(p0: Int) {
                }

                //CharSequence版本
                override fun onRemainTimeUpdate(p0: CharSequence?) {
                    Log.i("WalkNaviActivity", "剩余时间: $p0")
                    if (isFirstTimeUpdate) {
                        ttsManager.speak("预计需要${p0?.toString() ?: ""}")
                        isFirstTimeUpdate = false
                    }
                }

                //Int版本（必须写）
                override fun onRemainTimeUpdate(p0: Int) {
                }


                override fun onArriveDest() {
                    Log.i("WalkNaviActivity", "已到达目的地")
                    ttsManager.speak("已到达目的地")
                }

                override fun onReRouteComplete() {
                    ttsManager.speak("路线已更新")
                }

                override fun onRouteGuideIconUpdate(p0: Drawable?) {

                }

                override fun onRouteGuideKind(p0: RouteGuideKind?) {
                    p0?.let {
                        Log.i("WalkNaviActivity", "=== 收到枚举路段动作 ===")
                        Log.i("WalkNaviActivity", "动作: ${it.name}, ordinal: ${it.ordinal}")
                        
                        // 枚举值示例 (需打印后确认):
                        // TURN_LEFT, TURN_RIGHT, KEEP_STRAIGHT 等
                        
                        /*
                        val actionCmd = when (it) {
                            RouteGuideKind.TURN_LEFT -> 1
                            RouteGuideKind.TURN_RIGHT -> 2
                            RouteGuideKind.KEEP_STRAIGHT -> 3
                            else -> 0
                        }
                        if (actionCmd > 0) {
                             // viewModel.sendBluetoothData("{\"action\": $actionCmd}")
                        }
                        */
                    }
                }

                override fun onRouteGuideIconInfoUpdate(p0: IWRouteIconInfo?) {
                    // 暂时移除对 p0 内部属性的直接访问，避免版本不兼容导致的 Unresolved reference
                    Log.i("WalkNaviActivity", "onRouteGuideIconInfoUpdate: $p0")
                }

                override fun onGpsStatusChange(p0: CharSequence?, p1: Drawable?) {}

                override fun onRouteFarAway(p0: CharSequence?, p1: Drawable?) {}

                override fun onRoutePlanYawing(p0: CharSequence?, p1: Drawable?) {}

                override fun onIndoorEnd(p0: Message?) {}

                override fun onFinalEnd(p0: Message?) {
                    // 导航结束
                    Log.i("WalkNaviActivity", "导航已结束")
                    finish()
                }

                override fun onVibrate() {}

                override fun onNaviLocationUpdate() {
                    // 位置更新回调
                    // Log.i("WalkNaviActivity", "位置更新")
                }

                override fun onSimpleMapInfoUpdate(p0: WalkSimpleMapInfo?) {
                        p0?.let {
                            Log.i("WalkNaviActivity", "=== 收到结构化地图信息 ===")

                            // 1. 获取即将到达的路口转弯动作 (枚举值或整型)
                            // 通常：1-左转, 2-右转, 3-直行... 具体请看百度文档或跑一下看日志
                            val action = try { it.javaClass.getField("action").getInt(it) } catch(e:Exception) { -1 }

                            // 2. 获取到达该路口的距离
                            val distance = try { it.javaClass.getField("distance").getInt(it) } catch(e:Exception) { -1 }

                            Log.i("WalkNaviActivity", "动作指令Action: $action, 距离Distance: $distance 米")

                            // 3. 这里就是你要写蓝牙发送逻辑的地方了！
                            // 比如：当距离小于 30 米，并且需要左转(假设action==1)时，向腰带发送指令
                            /*
                            if (distance in 1..30 && action == 1) {
                                // 调用你的 BleManager 发送数据
                                // viewModel.sendBluetoothData("{\"action\": 1}")
                            }
                            */
                        }
                }


            }
        )
    }

    /**
     * 开始步行导航
     */
    private fun startWalkNavi() {
        try {

            walkNavigateHelper.startWalkNavi(this)
            Log.i("WalkNaviActivity", "步行导航已开始")
        } catch (e: Exception) {
            Log.e("WalkNaviActivity", "开始步行导航失败", e)
            ttsManager.speak("开始步行导航失败")
        }
    }

    override fun onResume() {
        super.onResume()
        // 恢复导航
        walkNavigateHelper.resume()
    }

    override fun onPause() {
        super.onPause()
        // 暂停导航
        walkNavigateHelper.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止传感器监听
        sensorManager.unregisterListener(sensorEventListener)
        // 退出导航
        walkNavigateHelper.quit()
        // 停止导航并释放资源
        navigationManager.stopNavigation()
        Log.i("WalkNaviActivity", "导航页面已销毁，导航已停止")
    }

}