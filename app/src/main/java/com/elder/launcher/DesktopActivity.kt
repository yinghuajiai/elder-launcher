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
import com.elder.launcher.base.BaseActivity
import com.elder.launcher.desktop.AppGridAdapter
import com.elder.launcher.desktop.DesktopApps
import com.elder.launcher.keepalive.LockState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 基础桌面（HOME）：固定时钟 + 应用图标网格 + 设置/退出入口。
 * 点击应用图标启动对应应用；长按图标开始拖动——
 * 拖到别处松开即排序，拖到底部「删除区」松开即从桌面移除。
 */
class DesktopActivity : BaseActivity() {

    private lateinit var grid: GridView
    private lateinit var adapter: AppGridAdapter
    private lateinit var apps: MutableList<String>
    private lateinit var deleteZone: TextView

    private var dragIndex = -1
    private var dragTarget = -1
    private var deleteZoneActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_desktop)

        findViewById<TextView>(R.id.tv_date).text =
            SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date())

        grid = findViewById(R.id.grid_apps)
        deleteZone = findViewById(R.id.tv_delete_zone)

        grid.setOnItemClickListener { _, _, position, _ ->
            if (position == apps.size) {
                startActivity(Intent(this, AppPickerActivity::class.java))
            } else {
                openApp(apps[position])
            }
        }

        // 长按图标：显示删除区并开始拖动
        grid.setOnItemLongClickListener { _, view, position, _ ->
            if (position !in apps.indices) return@setOnItemLongClickListener false
            startDrag(view, position)
            true
        }

        grid.setOnDragListener { _, event -> handleGridDrag(event) }
        deleteZone.setOnDragListener { _, event -> handleDeleteZoneDrag(event) }
        // 根布局作为兜底：手指拖到空白/时钟等非目标区域松开时，也要完成清理
        findViewById<View>(R.id.root).setOnDragListener { _, event -> handleRootDrag(event) }

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
        reloadAdapter()
        refreshExitButton()
    }

    private fun reloadAdapter() {
        apps = DesktopApps.list(this).toMutableList()
        adapter = AppGridAdapter(this, apps)
        grid.adapter = adapter
    }

    // ==================== 拖动 ====================

    private fun startDrag(view: View, position: Int) {
        dragIndex = position
        dragTarget = position
        deleteZone.alpha = 1f
        val clip = ClipData.newPlainText("", "")
        val shadow = View.DragShadowBuilder(view)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(clip, shadow, null, 0)
        } else {
            @Suppress("DEPRECATION")
            view.startDrag(clip, shadow, null, 0)
        }
    }

    private fun handleGridDrag(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_LOCATION -> {
                val pos = grid.pointToPosition(event.x.toInt(), event.y.toInt())
                if (pos != AdapterView.INVALID_POSITION && pos < apps.size) {
                    dragTarget = pos
                }
            }
            DragEvent.ACTION_DROP -> finishDrag()
            DragEvent.ACTION_DRAG_ENDED -> cleanupDrag()
        }
        return true
    }

    private fun handleDeleteZoneDrag(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_ENTERED -> setDeleteZoneActive(true)
            DragEvent.ACTION_DRAG_EXITED -> setDeleteZoneActive(false)
            DragEvent.ACTION_DROP -> deleteDraggedApp()
            DragEvent.ACTION_DRAG_ENDED -> cleanupDrag()
        }
        return true
    }

    private fun handleRootDrag(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DROP, DragEvent.ACTION_DRAG_ENDED -> cleanupDrag()
        }
        return true
    }

    /** 拖到网格其它位置落下：移动到目标位置并保存。 */
    private fun finishDrag() {
        if (dragIndex in apps.indices && dragTarget in apps.indices && dragIndex != dragTarget) {
            val moved = apps.removeAt(dragIndex)
            apps.add(dragTarget, moved)
            DesktopApps.replace(this, apps)
        }
        cleanupDrag()
    }

    /** 拖到删除区落下：从桌面移除。 */
    private fun deleteDraggedApp() {
        if (dragIndex in apps.indices) {
            apps.removeAt(dragIndex)
            DesktopApps.replace(this, apps)
            toast(getString(R.string.toast_removed))
        }
        cleanupDrag()
    }

    private fun cleanupDrag() {
        dragIndex = -1
        dragTarget = -1
        deleteZone.alpha = 0f
        setDeleteZoneActive(false)
        adapter.notifyDataSetChanged()
    }

    private fun setDeleteZoneActive(active: Boolean) {
        if (deleteZoneActive == active) return
        deleteZoneActive = active
        deleteZone.setBackgroundResource(
            if (active) R.drawable.bg_delete_zone_active else R.drawable.bg_delete_zone
        )
        deleteZone.text = getString(if (active) R.string.delete_zone_active else R.string.delete_zone)
    }

    // ==================== 其它 ====================

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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onBackPressed() {
        // 桌面作为主页，不响应返回键
    }
}
