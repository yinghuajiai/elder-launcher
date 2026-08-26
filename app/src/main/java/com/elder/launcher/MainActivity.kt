package com.elder.launcher

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.elder.launcher.accessibility.AccessibilitySettings
import com.elder.launcher.base.BaseActivity
import com.elder.launcher.desktop.DesktopSettings
import com.elder.launcher.keepalive.LockState
import com.elder.launcher.permission.PermissionDef
import com.elder.launcher.permission.PermissionHelper
import com.elder.launcher.setup.OnboardingState

/**
 * 引导页 + 设置页（二合一），采用「手机设置」式两级列表：
 * 父列表（权限管理 / 桌面设置 / 锁定 / 关于）→ 点进去是子列表。
 * 数据结构驱动，后续加设置项只需往 parents() 里加一个 ParentDef。
 */
class MainActivity : BaseActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: SettingsAdapter

    private var asSettings = false
    private var currentParent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        asSettings = intent.getBooleanExtra(EXTRA_AS_SETTINGS, false)

        // 已完成引导且非设置模式 → 直接进入桌面
        if (OnboardingState.isDone(this) && !asSettings) {
            startActivity(Intent(this, DesktopActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.lv_settings)
        adapter = SettingsAdapter()
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.onClick?.invoke()
        }

        findViewById<Button>(R.id.btn_back).setOnClickListener { onBackAction() }
        findViewById<Button>(R.id.btn_start).setOnClickListener { finishOnboarding() }
        findViewById<Button>(R.id.btn_exit_lock).setOnLongClickListener {
            toggleLock()
            true
        }

        showTop()
    }

    override fun onResume() {
        super.onResume()
        if (::listView.isInitialized) refresh()
    }

    override fun onBackPressed() {
        if (currentParent != null) {
            showTop()
        } else if (asSettings) {
            super.onBackPressed()
        }
        // 引导模式顶层：停留本页，不可直接退出
    }

    // ==================== 导航 ====================

    private fun showTop() {
        currentParent = null
        render()
    }

    private fun showParent(id: String) {
        currentParent = id
        render()
    }

    private fun onBackAction() {
        if (currentParent != null) {
            showTop()
        } else if (asSettings) {
            finish()
        }
    }

    private fun render() {
        val parent = parents().firstOrNull { it.id == currentParent }
        if (parent == null) {
            adapter.submit(parentItems())
            setTopBar(
                if (asSettings) getString(R.string.title_settings) else getString(R.string.title_onboarding),
                showBack = asSettings
            )
        } else {
            adapter.submit(parent.items())
            setTopBar(parent.title, showBack = true)
        }
        findViewById<Button>(R.id.btn_start).visibility =
            if (asSettings) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.btn_exit_lock).text =
            if (LockState.lockEnabled(this)) getString(R.string.btn_exit_lock) else getString(R.string.btn_lock)
    }

    private fun setTopBar(title: String, showBack: Boolean) {
        findViewById<TextView>(R.id.tv_title).text = title
        findViewById<Button>(R.id.btn_back).visibility = if (showBack) View.VISIBLE else View.GONE
    }

    private fun refresh() = render()

    private fun finishOnboarding() {
        OnboardingState.setDone(this, true)
        startActivity(Intent(this, DesktopActivity::class.java))
        finish()
    }

    // ==================== 数据 ====================

    private data class SettingsItem(
        val title: String,
        val subtitle: String = "",
        val trailing: String = "",
        val onClick: (() -> Unit)? = null
    )

    private data class ParentDef(
        val id: String,
        val title: String,
        val subtitle: () -> String,
        val items: () -> List<SettingsItem>
    )

    private class SettingsAdapter : BaseAdapter() {
        private val items = mutableListOf<SettingsItem>()

        fun submit(list: List<SettingsItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): SettingsItem = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_setting, parent, false)
            val item = items[position]
            view.findViewById<TextView>(R.id.tv_item_title).text = item.title
            val sub = view.findViewById<TextView>(R.id.tv_item_subtitle)
            sub.text = item.subtitle
            sub.visibility = if (item.subtitle.isEmpty()) View.GONE else View.VISIBLE
            view.findViewById<TextView>(R.id.tv_item_trailing).text = item.trailing
            return view
        }
    }

    private fun parents(): List<ParentDef> = listOf(
        ParentDef("permissions", getString(R.string.parent_permissions), { permissionSummary() }, { permissionItems() }),
        ParentDef("desktop", getString(R.string.parent_desktop), { getString(R.string.parent_desktop_sub) }, { desktopItems() }),
        ParentDef("lock", getString(R.string.title_lock), { lockStatus() }, { lockItems() }),
        ParentDef("about", getString(R.string.parent_about), { appVersion() }, { aboutItems() })
    )

    private fun parentItems(): List<SettingsItem> = parents().map {
        SettingsItem(it.title, it.subtitle(), "›") { showParent(it.id) }
    }

    private fun permissionSummary(): String {
        val granted = runtimeGroups().count { PermissionHelper.hasAll(this, it) }
        return getString(R.string.status_granted) + " $granted/5"
    }

    private fun runtimeGroups(): List<Array<String>> = listOf(
        PermissionDef.STORAGE, PermissionDef.LOCATION, PermissionDef.PHONE,
        PermissionDef.SMS, PermissionDef.CONTACTS
    )

    private fun lockStatus(): String =
        if (LockState.lockEnabled(this)) getString(R.string.status_on) else getString(R.string.status_off)

    private fun permissionItems(): List<SettingsItem> {
        val on = getString(R.string.status_on)
        val off = getString(R.string.status_off)
        return listOf(
            SettingsItem(getString(R.string.btn_all_runtime)) { requestAll() },
            permItem(getString(R.string.btn_storage), PermissionDef.STORAGE),
            permItem(getString(R.string.btn_location), PermissionDef.LOCATION),
            permItem(getString(R.string.btn_phone), PermissionDef.PHONE),
            permItem(getString(R.string.btn_sms), PermissionDef.SMS),
            permItem(getString(R.string.btn_contacts), PermissionDef.CONTACTS),
            specialItem(getString(R.string.item_overlay), PermissionHelper.canDrawOverlays(this)) {
                PermissionHelper.openOverlaySettings(this)
            },
            specialItem(getString(R.string.item_usage), PermissionHelper.hasUsageStatsPermission(this)) {
                PermissionHelper.openUsageStatsSettings(this)
            },
            specialItem(getString(R.string.item_battery), PermissionHelper.isIgnoringBatteryOptimizations(this)) {
                PermissionHelper.openBatteryOptimizationSettings(this)
            },
            SettingsItem(getString(R.string.item_autostart), getString(R.string.status_manual)) {
                PermissionHelper.openAutoStartSettings(this)
            },
            SettingsItem(getString(R.string.item_accessibility), if (PermissionHelper.isAccessibilityServiceEnabled(this)) on else off) {
                if (PermissionHelper.isAccessibilityServiceEnabled(this)) toast(getString(R.string.status_on))
                else PermissionHelper.openAccessibilitySettings(this)
            },
            SettingsItem(getString(R.string.item_read_notifications), if (AccessibilitySettings.readNotifications(this)) on else off) {
                AccessibilitySettings.setReadNotifications(this, !AccessibilitySettings.readNotifications(this))
                refresh()
            },
            SettingsItem(getString(R.string.item_tap_read), if (AccessibilitySettings.tapToRead(this)) on else off) {
                AccessibilitySettings.setTapToRead(this, !AccessibilitySettings.tapToRead(this))
                refresh()
            }
        )
    }

    private fun permItem(label: String, perms: Array<String>): SettingsItem =
        SettingsItem(
            label,
            "",
            if (PermissionHelper.hasAll(this, perms)) getString(R.string.status_granted) else getString(R.string.status_not_granted)
        ) {
            requirePermissions(perms) { _, _ -> refresh() }
        }

    private fun specialItem(label: String, granted: Boolean, open: () -> Unit): SettingsItem =
        SettingsItem(
            label,
            "",
            if (granted) getString(R.string.status_on) else getString(R.string.status_off)
        ) {
            if (!granted) open()
        }

    private fun desktopItems(): List<SettingsItem> = listOf(
        SettingsItem(
            getString(R.string.setting_show_add_tile),
            getString(R.string.setting_show_add_tile_desc),
            if (DesktopSettings.showAddTile(this)) getString(R.string.status_on) else getString(R.string.status_off)
        ) {
            DesktopSettings.setShowAddTile(this, !DesktopSettings.showAddTile(this))
            refresh()
        },
        SettingsItem(
            getString(R.string.setting_add_app),
            getString(R.string.setting_add_app_desc)
        ) {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }
    )

    private fun lockItems(): List<SettingsItem> = listOf(
        SettingsItem(
            getString(R.string.lock_mode_title),
            getString(R.string.lock_mode_desc),
            lockStatus()
        ) { toggleLock() }
    )

    private fun aboutItems(): List<SettingsItem> = listOf(
        SettingsItem(getString(R.string.about_app), "", getString(R.string.app_name)),
        SettingsItem(getString(R.string.about_version), "", appVersion())
    )

    // ==================== 动作 ====================

    private fun toggleLock() {
        val next = !LockState.lockEnabled(this)
        LockState.setLockEnabled(this, next)
        toast(if (next) getString(R.string.toast_lock_on) else getString(R.string.toast_lock_off))
        refresh()
    }

    private fun requestAll() {
        requirePermissions(PermissionDef.ALL_RUNTIME) { granted, _ ->
            toast(if (granted) getString(R.string.toast_all_granted) else getString(R.string.toast_all_failed))
            refresh()
        }
    }

    private fun appVersion(): String =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        /** 以设置页模式打开（跳过引导跳转）。 */
        const val EXTRA_AS_SETTINGS = "as_settings"
    }
}
