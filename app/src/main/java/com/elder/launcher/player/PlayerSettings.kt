package com.elder.launcher.player

import android.content.Context

/**
 * 播放器设置（不含外观），持久化在 SharedPreferences。
 * 后续可按需继续扩展（如手势、倍速、自动连播等）。
 */
object PlayerSettings {

    private const val PREFS = "elder_player_settings"

    private const val KEY_RESUME = "resume_playback"
    private const val KEY_ORIENTATION = "orientation"

    const val ORIENT_AUTO = "auto"
    const val ORIENT_LANDSCAPE = "landscape"
    const val ORIENT_PORTRAIT = "portrait"

    /** 记忆播放进度：再次打开同一视频时从上次位置继续。 */
    fun resumeEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RESUME, true)

    fun setResumeEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RESUME, value).apply()
    }

    /** 播放方向：auto / landscape / portrait。 */
    fun orientation(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ORIENTATION, ORIENT_AUTO) ?: ORIENT_AUTO

    fun setOrientation(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ORIENTATION, value).apply()
    }

    /** 单个播放列表的续播记忆（key = 磁贴 payload / 视频 uri）。 */
    fun resumeIndex(context: Context, key: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("idx_$key", 0)

    fun resumePosition(context: Context, key: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("pos_$key", 0L)

    fun saveResume(context: Context, key: String, index: Int, positionMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("idx_$key", index).putLong("pos_$key", positionMs).apply()
    }
}
