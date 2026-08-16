package com.elder.launcher.base

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import com.elder.launcher.permission.PermissionHelper

/**
 * 所有 Activity 基类：统一"检查→请求→回调"分发。
 * 子类只需：
 *   requirePermissions(PermissionDef.LOCATION) { granted, denied -> ... }
 * 特殊权限（无弹窗类）请直接走 PermissionHelper.openXxxSettings()。
 */
abstract class BaseActivity : AppCompatActivity() {

    private var permissionCallback: ((Boolean, List<String>) -> Unit)? = null
    private var requestCounter = 0
    private val baseRequestCode = 0xE100

    /** 检查并请求一组权限；已全部授权则直接回调 granted=true */
    protected fun requirePermissions(
        permissions: Array<String>,
        callback: (granted: Boolean, denied: List<String>) -> Unit
    ) {
        val denied = PermissionHelper.deniedList(this, permissions)
        if (denied.isEmpty()) {
            callback(true, emptyList())
            return
        }
        permissionCallback = callback
        PermissionHelper.request(this, denied.toTypedArray(), baseRequestCode + requestCounter++)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val cb = permissionCallback ?: return
        permissionCallback = null
        val denied = permissions.filterIndexed { i, _ ->
            grantResults[i] != PackageManager.PERMISSION_GRANTED
        }
        cb(denied.isEmpty(), denied)
    }
}