package com.elder.launcher.keepalive

import android.content.Context

/**
 * 锁定/保活模式开关。
 * 开启后：开机自启（BootReceiver）+ 无障碍服务保持前台（离开即拉回）。
 * 默认开启，便于长辈设备"装好即锁"；退出由应用内按钮控制。
 */
object LockState {

    private const val PREFS = "elder_lock"
    private const val KEY_LOCK_ENABLED = "lock_enabled"

    fun lockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOCK_ENABLED, true)

    fun setLockEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LOCK_ENABLED, value).apply()
    }
}
