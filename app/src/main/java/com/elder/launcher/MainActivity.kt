package com.elder.launcher

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.elder.launcher.accessibility.AccessibilitySettings
import com.elder.launcher.base.BaseActivity
import com.elder.launcher.keepalive.LockState
import com.elder.launcher.permission.PermissionDef
import com.elder.launcher.permission.PermissionHelper
import com.elder.launcher.setup.OnboardingState

/** 首次引导页：确认权限、一键授权，完成后进入桌面 */
class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 已完成引导 → 直接进入桌面
        if (OnboardingState.isDone(this)) {
            startActivity(Intent(this, DesktopActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // 开始使用：完成引导，进入桌面
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            OnboardingState.setDone(this, true)
            startActivity(Intent(this, DesktopActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.btn_all).setOnClickListener {
            requirePermissions(PermissionDef.ALL_RUNTIME) { granted, denied ->
                toast(if (granted) "全部权限已授予" else "未授予: ${denied.joinToString()}")
            }
        }
        findViewById<Button>(R.id.btn_storage).setOnClickListener {
            requirePermissions(PermissionDef.STORAGE) { g, d -> toast(if (g) "存储已授权" else "存储被拒绝") }
        }
        findViewById<Button>(R.id.btn_location).setOnClickListener {
            requirePermissions(PermissionDef.LOCATION) { g, _ -> toast(if (g) "定位已授权" else "定位被拒绝") }
        }
        findViewById<Button>(R.id.btn_phone).setOnClickListener {
            requirePermissions(PermissionDef.PHONE) { g, _ -> toast(if (g) "电话已授权" else "电话被拒绝") }
        }
        findViewById<Button>(R.id.btn_sms).setOnClickListener {
            requirePermissions(PermissionDef.SMS) { g, _ -> toast(if (g) "短信已授权" else "短信被拒绝") }
        }
        findViewById<Button>(R.id.btn_contacts).setOnClickListener {
            requirePermissions(PermissionDef.CONTACTS) { g, _ -> toast(if (g) "联系人已授权" else "联系人被拒绝") }
        }

        // 特殊权限：跳系统设置
        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            if (!PermissionHelper.canDrawOverlays(this)) PermissionHelper.openOverlaySettings(this)
            else toast("悬浮窗已授权")
        }
        findViewById<Button>(R.id.btn_usage).setOnClickListener {
            if (!PermissionHelper.hasUsageStatsPermission(this)) PermissionHelper.openUsageStatsSettings(this)
            else toast("使用统计已授权")
        }
        findViewById<Button>(R.id.btn_battery).setOnClickListener {
            if (!PermissionHelper.isIgnoringBatteryOptimizations(this)) PermissionHelper.openBatteryOptimizationSettings(this)
            else toast("已在电池白名单")
        }

        // 无障碍服务
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            if (PermissionHelper.isAccessibilityServiceEnabled(this)) toast("无障碍服务已开启")
            else PermissionHelper.openAccessibilitySettings(this)
        }
        findViewById<Button>(R.id.btn_read_notifications).setOnClickListener {
            AccessibilitySettings.setReadNotifications(this, !AccessibilitySettings.readNotifications(this))
            refreshAccessibilityState()
        }
        findViewById<Button>(R.id.btn_tap_read).setOnClickListener {
            AccessibilitySettings.setTapToRead(this, !AccessibilitySettings.tapToRead(this))
            refreshAccessibilityState()
        }

        // 锁定 / 保活
        findViewById<Button>(R.id.btn_lock_mode).setOnClickListener {
            val next = !LockState.lockEnabled(this)
            LockState.setLockEnabled(this, next)
            toast(if (next) getString(R.string.toast_lock_on) else getString(R.string.toast_lock_off))
            refreshLockState()
        }
        findViewById<Button>(R.id.btn_exit_lock).setOnLongClickListener {
            val next = !LockState.lockEnabled(this)
            LockState.setLockEnabled(this, next)
            toast(if (next) getString(R.string.toast_lock_on) else getString(R.string.toast_lock_off))
            refreshLockState()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置返回后刷新状态（悬浮窗/使用统计/无障碍类授权无回调）
        refreshSpecialPermissionState()
        refreshAccessibilityState()
        refreshLockState()
    }

    override fun onBackPressed() {
        if (LockState.lockEnabled(this)) {
            toast(getString(R.string.toast_locked_hint))
        } else {
            super.onBackPressed()
        }
    }

    private fun refreshLockState() {
        val locked = LockState.lockEnabled(this)
        findViewById<Button>(R.id.btn_lock_mode).text =
            if (locked) getString(R.string.btn_lock_mode) else getString(R.string.btn_lock_mode_off)
        findViewById<Button>(R.id.btn_exit_lock).text =
            if (locked) getString(R.string.btn_exit_lock) else getString(R.string.btn_lock)
    }

    private fun refreshSpecialPermissionState() {
        findViewById<Button>(R.id.btn_overlay).text =
            if (PermissionHelper.canDrawOverlays(this)) "悬浮窗：已授权" else "悬浮窗：未授权"
        findViewById<Button>(R.id.btn_usage).text =
            if (PermissionHelper.hasUsageStatsPermission(this)) "使用统计：已授权" else "使用统计：未授权"
        findViewById<Button>(R.id.btn_battery).text =
            if (PermissionHelper.isIgnoringBatteryOptimizations(this)) "电池白名单：已加入" else "电池白名单：未加入"
    }

    private fun refreshAccessibilityState() {
        findViewById<Button>(R.id.btn_accessibility).text =
            if (PermissionHelper.isAccessibilityServiceEnabled(this)) "无障碍服务：已开启" else "无障碍服务：未开启（点击开启）"
        findViewById<Button>(R.id.btn_read_notifications).text =
            if (AccessibilitySettings.readNotifications(this)) "通知朗读：已开启" else "通知朗读：已关闭"
        findViewById<Button>(R.id.btn_tap_read).text =
            if (AccessibilitySettings.tapToRead(this)) "点读模式：已开启" else "点读模式：已关闭"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}