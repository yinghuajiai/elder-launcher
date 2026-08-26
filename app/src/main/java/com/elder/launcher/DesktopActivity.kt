package com.elder.launcher

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import com.elder.launcher.base.BaseActivity
import com.elder.launcher.desktop.AppGridAdapter
import com.elder.launcher.desktop.DesktopApps
import com.elder.launcher.keepalive.LockState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 基础桌面（HOME）：固定时钟 + 应用图标网格 + 设置/退出入口。
 * 点击「添加」磁贴进入应用选择器；点击应用图标启动对应应用。
 */
class DesktopActivity : BaseActivity() {

    private lateinit var grid: GridView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_desktop)

        findViewById<TextView>(R.id.tv_date).text =
            SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date())

        grid = findViewById(R.id.grid_apps)
        grid.setOnItemClickListener { _, _, position, _ ->
            val apps = DesktopApps.list(this)
            if (position == apps.size) {
                startActivity(Intent(this, AppPickerActivity::class.java))
            } else {
                openApp(apps[position])
            }
        }

        // 设置入口：进入应用自身设置页
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            val i = Intent(this, MainActivity::class.java)
            i.putExtra(MainActivity.EXTRA_AS_SETTINGS, true)
            startActivity(i)
        }

        // 退出锁定：长按切换
        findViewById<Button>(R.id.btn_exit_lock).setOnLongClickListener {
            val next = !LockState.lockEnabled(this)
            LockState.setLockEnabled(this, next)
            Toast.makeText(
                this,
                if (next) getString(R.string.toast_lock_on) else getString(R.string.toast_lock_off),
                Toast.LENGTH_SHORT
            ).show()
            refreshExitButton()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        grid.adapter = AppGridAdapter(this, DesktopApps.list(this))
        refreshExitButton()
    }

    private fun refreshExitButton() {
        findViewById<Button>(R.id.btn_exit_lock).text =
            if (LockState.lockEnabled(this)) getString(R.string.btn_exit_lock) else getString(R.string.btn_lock)
    }

    private fun openApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "无法打开该应用", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        // 桌面作为主页，不响应返回键
    }
}
