package com.elder.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elder.launcher.DesktopActivity
import com.elder.launcher.MainActivity
import com.elder.launcher.keepalive.LockState
import com.elder.launcher.setup.OnboardingState

/**
 * 开机自启：重启后自动回到桌面（锁定态）。
 * 覆盖主流机型开机广播，保证"不管怎么重启，都回到本应用"。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!LockState.lockEnabled(context)) return
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_PRESENT,
            ACTION_QUICKBOOT_POWERON -> launch(context)
        }
    }

    private fun launch(context: Context) {
        val target = if (OnboardingState.isDone(context)) DesktopActivity::class.java else MainActivity::class.java
        val i = Intent(context, target).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(i)
    }

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
