package com.elder.launcher

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.DragEvent
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.elder.launcher.base.BaseActivity
import com.elder.launcher.desktop.AppGridAdapter
import com.elder.launcher.desktop.DesktopApps
import com.elder.launcher.keepalive.LockState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 基础桌面（HOME）：固定时钟 + 应用图标网格 + 设置/退出入口。
 * 点击应用图标启动对应应用；长按图标拖动排序；「整理」进入编辑模式点 × 删除。
 */
class DesktopActivity : BaseActivity() {

    private lateinit var grid: GridView
    private lateinit var adapter: AppGridAdapter
    private lateinit var apps: MutableList<String>

    private var editing = false
    private var dragIndex = -1
    private var dragTarget = -1
    private var dragView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_desktop)

        findViewById<TextView>(R.id.tv_date).text =
            SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date())

        grid = findViewById(R.id.grid_apps)

        grid.setOnItemClickListener { _, _, position, _ ->
            if (editing) return@setOnItemClickListener
            if (position == apps.size) {
                startActivity(Intent(this, AppPickerActivity::class.java))
            } else {
                openApp(apps[position])
            }
        }

        // 长按图标开始拖动排序（无需先进入整理模式）
        grid.setOnItemLongClickListener { _, view, position, _ ->
            if (position !in apps.indices) return@setOnItemLongClickListener false
            startDrag(view, position)
            true
        }

        grid.setOnDragListener { _, event -> handleDrag(event) }

        // 设置入口：进入应用自身设置页
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            val i = Intent(this, MainActivity::class.java)
            i.putExtra(MainActivity.EXTRA_AS_SETTINGS, true)
            startActivity(i)
        }

        // 整理：进入/退出编辑模式（拖动排序 + 删除）
        findViewById<Button>(R.id.btn_organize).setOnClickListener {
            setEditing(!editing)
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
        reloadAdapter()
        refreshExitButton()
    }

    private fun reloadAdapter() {
        apps = DesktopApps.list(this).toMutableList()
        editing = false
        adapter = AppGridAdapter(this, apps)
        adapter.editing = editing
        adapter.onDelete = { position -> confirmDelete(position) }
        grid.adapter = adapter
        syncOrganizeUi()
    }

    private fun setEditing(value: Boolean) {
        editing = value
        adapter.editing = editing
        syncOrganizeUi()
    }

    private fun syncOrganizeUi() {
        findViewById<Button>(R.id.btn_organize).text =
            getString(if (editing) R.string.organize_done else R.string.organize)
        findViewById<TextView>(R.id.tv_edit_hint).visibility =
            if (editing) View.VISIBLE else View.GONE
    }

    private fun startDrag(view: View, position: Int) {
        dragIndex = position
        dragTarget = position
        dragView = view
        val clip = ClipData.newPlainText("", "")
        val shadow = View.DragShadowBuilder(view)
        view.visibility = View.INVISIBLE
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(clip, shadow, null, 0)
        } else {
            @Suppress("DEPRECATION")
            view.startDrag(clip, shadow, null, 0)
        }
        if (!started) {
            view.visibility = View.VISIBLE
            dragView = null
            dragIndex = -1
            dragTarget = -1
        }
    }

    private fun handleDrag(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_LOCATION -> {
                val pos = grid.pointToPosition(event.x.toInt(), event.y.toInt())
                if (pos != AdapterView.INVALID_POSITION && pos < apps.size) {
                    dragTarget = pos
                }
            }
            DragEvent.ACTION_DROP, DragEvent.ACTION_DRAG_ENDED -> finishDrag()
        }
        // 对所有事件（含 ACTION_DRAG_STARTED）都返回 true，
        // GridView 才会成为有效拖放目标，收到 LOCATION / DROP 事件完成换位。
        return true
    }

    /** 拖到目标位置落下后，把图标从原位置移动到目标位置并持久化。 */
    private fun finishDrag() {
        if (dragIndex in apps.indices && dragTarget in apps.indices && dragIndex != dragTarget) {
            val moved = apps.removeAt(dragIndex)
            apps.add(dragTarget, moved)
            DesktopApps.replace(this, apps)
        }
        dragView?.visibility = View.VISIBLE
        dragView = null
        dragIndex = -1
        dragTarget = -1
        adapter.notifyDataSetChanged()
    }

    private fun confirmDelete(position: Int) {
        if (position !in apps.indices) return
        val pkg = apps[position]
        val label = appLabel(pkg)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_app))
            .setMessage(getString(R.string.delete_confirm, label))
            .setPositiveButton(R.string.confirm) { _, _ -> deleteApp(position) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteApp(position: Int) {
        if (position !in apps.indices) return
        apps.removeAt(position)
        DesktopApps.replace(this, apps)
        adapter.notifyDataSetChanged()
    }

    private fun appLabel(pkg: String): String =
        try {
            packageManager.getApplicationInfo(pkg, 0)
                .loadLabel(packageManager).toString()
        } catch (_: Exception) {
            pkg
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
