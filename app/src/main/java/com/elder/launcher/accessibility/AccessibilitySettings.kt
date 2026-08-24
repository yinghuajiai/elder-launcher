package com.elder.launcher.accessibility

import android.content.Context

/**
 * 无障碍朗读/点读的开关配置，持久化在 SharedPreferences。
 * 服务与权限中心共用同一份配置，保证两者状态一致。
 */
object AccessibilitySettings {

    private const val PREFS = "elder_accessibility"
    private const val KEY_READ_NOTIFICATIONS = "read_notifications"
    private const val KEY_TAP_TO_READ = "tap_to_read"

    fun readNotifications(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_READ_NOTIFICATIONS, true)

    fun setReadNotifications(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_READ_NOTIFICATIONS, value).apply()
    }

    fun tapToRead(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_TAP_TO_READ, true)

    fun setTapToRead(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TAP_TO_READ, value).apply()
    }
}
