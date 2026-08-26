package com.elder.launcher.desktop

import android.content.Context

/**
 * 桌面上已添加的应用（包名列表，有序）。
 * 以逗号分隔存于 SharedPreferences，包名不含逗号，安全。
 */
object DesktopApps {

    private const val PREFS = "elder_desktop"
    private const val KEY_APPS = "apps"

    fun list(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_APPS, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",")
    }

    fun add(context: Context, pkg: String) {
        val current = list(context)
        if (current.contains(pkg)) return
        val next = current + pkg
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_APPS, next.joinToString(",")).apply()
    }

    fun remove(context: Context, pkg: String) {
        val next = list(context).filterNot { it == pkg }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_APPS, next.joinToString(",")).apply()
    }

    /** 覆盖保存整个列表（用于拖动排序后持久化新顺序）。 */
    fun replace(context: Context, apps: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_APPS, apps.joinToString(",")).apply()
    }
}
