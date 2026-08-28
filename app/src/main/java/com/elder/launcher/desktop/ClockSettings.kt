package com.elder.launcher.desktop

import android.content.Context

/**
 * 桌面时钟设置（数字 / 指针 / 双时钟），持久化在 SharedPreferences。
 */
object ClockSettings {

    private const val PREFS = "elder_clock_settings"
    private const val KEY_MODE = "mode"
    private const val KEY_SHAPE = "shape"
    private const val KEY_DIGITAL_LEFT = "digital_left"

    const val MODE_DIGITAL = "digital"
    const val MODE_ANALOG = "analog"
    const val MODE_BOTH = "both"

    const val SHAPE_CIRCLE = "circle"
    const val SHAPE_ROUNDED = "rounded"

    /** 时钟样式：数字 / 指针 / 双时钟。 */
    fun mode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_DIGITAL) ?: MODE_DIGITAL

    fun setMode(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, value).apply()
    }

    /** 指针外形：圆形 / 圆角方形。 */
    fun shape(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SHAPE, SHAPE_CIRCLE) ?: SHAPE_CIRCLE

    fun setShape(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SHAPE, value).apply()
    }

    /** 双时钟时数字时钟是否在左。 */
    fun digitalLeft(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DIGITAL_LEFT, true)

    fun setDigitalLeft(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DIGITAL_LEFT, value).apply()
    }
}
