package com.elder.launcher.desktop

import android.content.Context

/**
 * 桌面显示相关设置，持久化在 SharedPreferences。
 */
object DesktopSettings {

    private const val PREFS = "elder_desktop_settings"
    private const val KEY_SHOW_ADD_TILE = "show_add_tile"

    /** 是否在桌面显示「+」添加磁贴（默认显示）。 */
    fun showAddTile(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_ADD_TILE, true)

    fun setShowAddTile(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_ADD_TILE, value).apply()
    }
}
