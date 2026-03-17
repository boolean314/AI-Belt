package com.example.ai_belt_mobile.base

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.util.TypedValue
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    enum class FontLevel(val progress: Int, val scale: Float) {
        SMALL(0, 0.9f),
        NORMAL(1, 1.0f),
        LARGE(2, 1.15f);

        companion object {
            fun fromProgress(p: Int): FontLevel = when (p) {
                0 -> SMALL
                2 -> LARGE
                else -> NORMAL
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val level = getSavedFontLevel(newBase)
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = level.scale
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    protected fun getCurrentFontLevel(): FontLevel {
        val name = prefs(this).getString(KEY_LEVEL, FontLevel.NORMAL.name)
        return FontLevel.valueOf(name ?: FontLevel.NORMAL.name)
    }

    protected fun applyFontLevel(level: FontLevel) {
        if (level == getCurrentFontLevel()) return
        prefs(this).edit().putString(KEY_LEVEL, level.name).apply()
        recreate()
    }

    //把指定控件字体固定为“标准下的视觉大小”
    protected fun freezeTextSize(vararg views: TextView) {
        val fontScale = resources.configuration.fontScale.takeIf { it > 0f } ?: 1f
        views.forEach { tv ->
            val currentPx = tv.textSize
            val fixedPx = currentPx / fontScale
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, fixedPx)
        }
    }

    private fun getSavedFontLevel(context: Context): FontLevel {
        val name = prefs(context).getString(KEY_LEVEL, FontLevel.NORMAL.name)
        return FontLevel.valueOf(name ?: FontLevel.NORMAL.name)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "display_pref"
        private const val KEY_LEVEL = "font_level"
    }
}
/*
* 使用说明（BaseActivity）
*
* 1) 继承方式
*    - 让页面 Activity 继承 BaseActivity，而不是直接继承 AppCompatActivity。
*    - 这样会在 attachBaseContext 中自动读取并应用字体缩放配置。
*
* 2) 字体档位
*    - FontLevel 提供三档：
*      SMALL(0, 0.9f)、NORMAL(1, 1.0f)、LARGE(2, 1.15f)
*    - 可通过 FontLevel.fromProgress(progress) 与 SeekBar 的 0/1/2 对应。
*
* 3) 切换字体大小
*    - 调用 applyFontLevel(level) 保存档位并刷新页面（recreate）。
*    - 若目标档位与当前档位相同，不会重复刷新。
*
* 4) 读取当前档位
*    - 调用 getCurrentFontLevel() 获取当前已生效的字体档位。
*
* 5) 固定局部文字大小
*    - 对不希望随全局缩放变化的 TextView，调用 freezeTextSize(tv1, tv2, ...)。
*    - 该方法会按当前 fontScale 反算并固定为“标准视觉大小”。
*
* 6) 存储说明
*    - SharedPreferences: display_pref
*    - Key: font_level
*    - 默认值：NORMAL
*/