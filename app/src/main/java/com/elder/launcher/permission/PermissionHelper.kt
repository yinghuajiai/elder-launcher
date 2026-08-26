package com.elder.launcher.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.usage.UsageStatsManager
import android.view.accessibility.AccessibilityManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** 权限统一入口：危险权限 + 特殊权限（悬浮窗/使用统计/电池优化） */
object PermissionHelper {

    // ==================== 危险权限：检查 ====================
    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun deniedList(context: Context, permissions: Array<String>): List<String> =
        permissions.filterNot { isGranted(context, it) }

    fun hasAll(context: Context, permissions: Array<String>): Boolean =
        deniedList(context, permissions).isEmpty()

    // ==================== 危险权限：请求 ====================
    // 结果由 Activity.onRequestPermissionsResult 分发（见 BaseActivity）
    fun request(activity: Activity, permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    // ==================== 特殊权限：悬浮窗 ====================
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ==================== 特殊权限：使用情况访问 ====================
    fun hasUsageStatsPermission(context: Context): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000, now)
        return !stats.isNullOrEmpty()
    }

    fun openUsageStatsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ==================== 特殊权限：电池优化白名单（保活） ====================
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ==================== 特殊权限：无障碍服务 ====================
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return services.any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ==================== 特殊权限：自启动（厂商 ROM，重启后自动拉起无障碍） ====================
    fun openAutoStartSettings(context: Context) {
        val pm = context.packageManager
        val candidates = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        )
        for (cn in candidates) {
            val intent = Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                try {
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {
                }
            }
        }
        // 兜底：跳应用详情页
        val detail = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(detail)
    }

    // ==================== 特殊权限：未知来源安装（API 26+，26~29 需要） ====================
    fun canRequestPackageInstalls(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    fun openPackageInstallerSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}