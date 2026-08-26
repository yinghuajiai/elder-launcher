package com.elder.launcher.setup

import android.content.Context

/**
 * 首次引导状态：判断用户是否已完成「开始」授权流程。
 * 完成前展示引导页（权限授权），完成后直接进入桌面。
 */
object OnboardingState {

    private const val PREFS = "elder_onboarding"
    private const val KEY_DONE = "done"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DONE, false)

    fun setDone(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DONE, value).apply()
    }
}
