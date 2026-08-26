package com.elder.launcher

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.elder.launcher.accessibility.AccessibilitySettings
import com.elder.launcher.base.BaseActivity
import com.elder.launcher.keepalive.LockState
import com.elder.launcher.permission.PermissionDef
import com.elder.launcher.permission.PermissionHelper
import com.elder.launcher.setup.OnboardingState

/**
 * 首次引导页 + 设置页（二合一）。
 * 权限按「危险权限 / 特殊权限 / 无障碍 / 锁定」分组，默认折叠，点击分组头展开查看；
 * 底部「开始使用 / 返回桌面」按钮固定常驻，无需滚动即可操作。
 */
class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val asSettings = intent.getBooleanExtra(EXTRA_AS_SETTINGS, false)

        // 已完成引导且非设置模式 → 直接进入桌面
        if (OnboardingState.isDone(this) && !asSettings) {
            startActivity(Intent(this, DesktopActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // 设置模式下标题与按钮语义调整
        if (asSettings) {
            findViewById<TextView>(R.id.title_onboarding).text = getString(R.string.title_settings)
            findViewById<Button>(R.id.btn_start).text = getString(R.string.btn_back_desktop)
        }

        // 开始使用：完成引导，进入桌面；设置模式下则返回桌面
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            if (asSettings) {
                finish()
            } else {
                OnboardingState.setDone(this, true)
                startActivity(Intent(this, DesktopActivity::class.java))
                finish()
            }
        }

        bindGroup(R.id.group_runtime_header, R.id.group_runtime_body)
        bindGroup(R.id.group_special_header, R.id.group_special_body)
        bindGroup(R.id.group_accessibility_header, R.id.group_accessibility_body)
        bindGroup(R.id.group_lock_header, R.id.group_lock_body)

        // ---- 危险权限 ----
        findViewById<Button>(R.id.btn_all).setOnClickListener {
            requirePermissions(PermissionDef.ALL_RUNTIME) { granted, _ ->
                toast(if (granted) "全部权限已授予" else "仍有权限未授予")
                refreshRuntime()
            }
        }
        findViewById<Button>(R.id.btn_storage).setOnClickListener {
            requirePermissions(PermissionDef.STORAGE) { _, _ -> refreshRuntime() }
        }
        findViewById<Button>(R.id.btn_location).setOnClickListener {
            requirePermissions(PermissionDef.LOCATION) { _, _ -> refreshRuntime() }
        }
        findViewById<Button>(R.id.btn_phone).setOnClickListener {
            requirePermissions(PermissionDef.PHONE) { _, _ -> refreshRuntime() }
        }
        findViewById<Button>(R.id.btn_sms).setOnClickListener {
            requirePermissions(PermissionDef.SMS) { _, _ -> refreshRuntime() }
        }
        findViewById<Button>(R.id.btn_contacts).setOnClickListener {
            requirePermissions(PermissionDef.CONTACTS) { _, _ -> refreshRuntime() }
        }

        // ---- 特殊权限：跳系统设置 ----
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
        findViewById<Button>(R.id.btn_autostart).setOnClickListener {
            PermissionHelper.openAutoStartSettings(this)
            toast("请在自启动列表中允许「长辈桌面」")
        }

        // ---- 无障碍服务 ----
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            if (PermissionHelper.isAccessibilityServiceEnabled(this)) toast("无障碍服务已开启")
            else PermissionHelper.openAccessibilitySettings(this)
        }
        findViewById<Button>(R.id.btn_read_notifications).setOnClickListener {
            AccessibilitySettings.setReadNotifications(this, !AccessibilitySettings.readNotifications(this))
            refreshAccessibility()
        }
        findViewById<Button>(R.id.btn_tap_read).setOnClickListener {
            AccessibilitySettings.setTapToRead(this, !AccessibilitySettings.tapToRead(this))
            refreshAccessibility()
        }

        // ---- 锁定 / 保活 ----
        findViewById<Button>(R.id.btn_lock_mode).setOnClickListener {
            val next = !LockState.lockEnabled(this)
            LockState.setLockEnabled(this, next)
            toast(if (next) getString(R.string.toast_lock_on) else getString(R.string.toast_lock_off))
            refreshLock()
        }
        findViewById<Button>(R.id.btn_exit_lock).setOnLongClickListener {
            val next = !LockState.lockEnabled(this)
            LockState.setLockEnabled(this, next)
            toast(if (next) getString(R.string.toast_lock_on) else getString(R.string.toast_lock_off))
            refreshLock()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置返回后刷新状态（悬浮窗/使用统计/无障碍类授权无回调）
        refreshRuntime()
        refreshSpecial()
        refreshAccessibility()
        refreshLock()
    }

    override fun onBackPressed() {
        if (LockState.lockEnabled(this)) {
            toast(getString(R.string.toast_locked_hint))
        } else {
            super.onBackPressed()
        }
    }

    // ==================== 分组展开 / 折叠 ====================

    private fun bindGroup(headerId: Int, bodyId: Int) {
        findViewById<TextView>(headerId).setOnClickListener {
            val body = findViewById<View>(bodyId)
            val show = body.visibility != View.VISIBLE
            body.visibility = if (show) View.VISIBLE else View.GONE
            refreshAll()
        }
    }

    private fun refreshAll() {
        refreshRuntime()
        refreshSpecial()
        refreshAccessibility()
        refreshLock()
    }

    private fun expanded(bodyId: Int): Boolean =
        findViewById<View>(bodyId).visibility == View.VISIBLE

    private fun headerText(expanded: Boolean, title: String, status: String): String =
        (if (expanded) "▾ " else "▸ ") + title + " · " + status

    // ==================== 状态刷新 ====================

    private fun refreshRuntime() {
        val groups = listOf(
            PermissionDef.STORAGE, PermissionDef.LOCATION, PermissionDef.PHONE,
            PermissionDef.SMS, PermissionDef.CONTACTS
        )
        val granted = groups.count { PermissionHelper.hasAll(this, it) }

        findViewById<TextView>(R.id.group_runtime_header).text =
            headerText(expanded(R.id.group_runtime_body), getString(R.string.title_runtime), "已授权 $granted/5")

        findViewById<Button>(R.id.btn_storage).text = permText("存储权限", PermissionDef.STORAGE)
        findViewById<Button>(R.id.btn_location).text = permText("定位权限", PermissionDef.LOCATION)
        findViewById<Button>(R.id.btn_phone).text = permText("电话权限", PermissionDef.PHONE)
        findViewById<Button>(R.id.btn_sms).text = permText("短信权限", PermissionDef.SMS)
        findViewById<Button>(R.id.btn_contacts).text = permText("联系人权限", PermissionDef.CONTACTS)
    }

    private fun permText(label: String, perms: Array<String>): String =
        label + if (PermissionHelper.hasAll(this, perms)) "：已授权" else "：未授权（点击申请）"

    private fun refreshSpecial() {
        val overlay = PermissionHelper.canDrawOverlays(this)
        val usage = PermissionHelper.hasUsageStatsPermission(this)
        val battery = PermissionHelper.isIgnoringBatteryOptimizations(this)
        val granted = listOf(overlay, usage, battery).count { it }

        findViewById<TextView>(R.id.group_special_header).text =
            headerText(expanded(R.id.group_special_body), getString(R.string.title_special), "已开启 $granted/3")

        findViewById<Button>(R.id.btn_overlay).text =
            if (overlay) "悬浮窗：已授权" else "悬浮窗：未授权"
        findViewById<Button>(R.id.btn_usage).text =
            if (usage) "使用统计：已授权" else "使用统计：未授权"
        findViewById<Button>(R.id.btn_battery).text =
            if (battery) "电池白名单：已加入" else "电池白名单：未加入"
        findViewById<Button>(R.id.btn_autostart).text = "自启动：需手动确认"
    }

    private fun refreshAccessibility() {
        val enabled = PermissionHelper.isAccessibilityServiceEnabled(this)
        val status = if (enabled) "已开启" else "未开启"

        findViewById<TextView>(R.id.group_accessibility_header).text =
            headerText(expanded(R.id.group_accessibility_body), getString(R.string.title_accessibility), status)

        findViewById<Button>(R.id.btn_accessibility).text =
            if (enabled) "无障碍服务：已开启" else "无障碍服务：未开启（点击开启）"
        findViewById<Button>(R.id.btn_read_notifications).text =
            if (AccessibilitySettings.readNotifications(this)) "通知朗读：已开启" else "通知朗读：已关闭"
        findViewById<Button>(R.id.btn_tap_read).text =
            if (AccessibilitySettings.tapToRead(this)) "点读模式：已开启" else "点读模式：已关闭"
    }

    private fun refreshLock() {
        val locked = LockState.lockEnabled(this)
        findViewById<TextView>(R.id.group_lock_header).text =
            headerText(expanded(R.id.group_lock_body), getString(R.string.title_lock), if (locked) "已开启" else "已关闭")
        findViewById<Button>(R.id.btn_lock_mode).text =
            if (locked) getString(R.string.btn_lock_mode) else getString(R.string.btn_lock_mode_off)
        findViewById<Button>(R.id.btn_exit_lock).text =
            if (locked) getString(R.string.btn_exit_lock) else getString(R.string.btn_lock)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        /** 以设置页模式打开（跳过引导跳转）。 */
        const val EXTRA_AS_SETTINGS = "as_settings"
    }
}
