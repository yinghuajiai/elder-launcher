package com.elder.launcher

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.DragEvent
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import com.elder.launcher.base.BaseActivity
import com.elder.launcher.desktop.AnalogClockView
import com.elder.launcher.desktop.AppGridAdapter
import com.elder.launcher.desktop.ClockSettings
import com.elder.launcher.desktop.DesktopApps
import com.elder.launcher.desktop.DesktopSettings
import com.elder.launcher.desktop.DesktopTile
import com.elder.launcher.desktop.TileType
import com.elder.launcher.keepalive.LockState
import com.elder.launcher.lunar.LunarCalendar
import com.elder.launcher.player.CoverStore
import com.elder.launcher.player.Playlist
import com.elder.launcher.player.VideoEntry
import com.elder.launcher.player.VideoPlayerActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 基础桌面（HOME）：固定时钟 + 磁贴网格（应用/视频）+ 设置/退出入口。
 * 点击磁贴启动应用或播放视频；长按拖动排序 / 拖到删除区移除。
 * 「+」可选择添加应用或视频。
 */
class DesktopActivity : BaseActivity() {

    private lateinit var grid: GridView
    private lateinit var adapter: AppGridAdapter
    private lateinit var tiles: MutableList<DesktopTile>
    private lateinit var deleteZone: TextView

    private var dragIndex = -1
    private var dragTarget = -1
    private var deleteZoneActive = false
    private var pendingCoverEntries: List<VideoEntry>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_desktop)

        updateDateTime()
        renderClock()

        grid = findViewById(R.id.grid_apps)
        deleteZone = findViewById(R.id.tv_delete_zone)

        grid.setOnItemClickListener { _, _, position, _ ->
            if (position == tiles.size) {
                showAddDialog()
            } else {
                openTile(tiles[position])
            }
        }

        grid.setOnItemLongClickListener { _, view, position, _ ->
            if (position !in tiles.indices) return@setOnItemLongClickListener false
            startDrag(view, position)
            true
        }

        grid.setOnDragListener { _, event -> handleGridDrag(event) }
        deleteZone.setOnDragListener { _, event -> handleDeleteZoneDrag(event) }
        findViewById<View>(R.id.root).setOnDragListener { _, event -> handleRootDrag(event) }

        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            val i = Intent(this, MainActivity::class.java)
            i.putExtra(MainActivity.EXTRA_AS_SETTINGS, true)
            startActivity(i)
        }

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
        updateDateTime()
        renderClock()
        reloadAdapter()
        refreshExitButton()
    }

    private fun updateDateTime() {
        findViewById<TextView>(R.id.tv_date).text =
            SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date())
        findViewById<TextView>(R.id.tv_lunar).text = LunarCalendar.todayText()
    }

    /** 按设置渲染时钟区：数字 / 指针 / 双时钟。 */
    private fun renderClock() {
        val container = findViewById<LinearLayout>(R.id.clock_container)
        container.removeAllViews()
        val mode = ClockSettings.mode(this)
        val shape = ClockSettings.shape(this)
        val digitalLeft = ClockSettings.digitalLeft(this)

        when (mode) {
            ClockSettings.MODE_ANALOG -> container.addView(analogClock(shape, 220f))
            ClockSettings.MODE_BOTH -> {
                val digital = digitalClock(42f)
                val analog = analogClock(shape, 150f)
                if (digitalLeft) {
                    container.addView(digital)
                    container.addView(analog)
                } else {
                    container.addView(analog)
                    container.addView(digital)
                }
            }
            else -> container.addView(digitalClock(72f))
        }
    }

    private fun digitalClock(sizeSp: Float): TextClock = TextClock(this).apply {
        format12Hour = "a h:mm"
        format24Hour = "HH:mm"
        setTextColor(0xFF1A5F7A.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTypeface(Typeface.DEFAULT_BOLD)
        gravity = android.view.Gravity.CENTER
    }

    private fun analogClock(shape: String, sizeDp: Float): AnalogClockView {
        val size = (sizeDp * resources.displayMetrics.density).toInt()
        return AnalogClockView(this).apply {
            this.shape = if (shape == ClockSettings.SHAPE_ROUNDED)
                AnalogClockView.Shape.ROUNDED_SQUARE else AnalogClockView.Shape.CIRCLE
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun reloadAdapter() {
        tiles = DesktopApps.list(this).toMutableList()
        adapter = AppGridAdapter(this, tiles, DesktopSettings.showAddTile(this))
        grid.adapter = adapter
    }

    // ==================== 磁贴点击 ====================

    private fun openTile(tile: DesktopTile) {
        when (tile.type) {
            TileType.APP -> openApp(tile.payload)
            TileType.VIDEO -> startPlayer(listOf(VideoEntry(tile.payload, tile.label)), tile.payload)
            TileType.PLAYLIST -> startPlayer(Playlist.decode(tile.payload), tile.payload)
        }
    }

    private fun startPlayer(entries: List<VideoEntry>, key: String) {
        startActivity(
            Intent(this, VideoPlayerActivity::class.java)
                .putExtra(VideoPlayerActivity.EXTRA_KEY, key)
                .putExtra(VideoPlayerActivity.EXTRA_PLAYLIST, Playlist.encode(entries))
                .putExtra(VideoPlayerActivity.EXTRA_FROM_TILE, true)
        )
    }

    private fun openApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "无法打开该应用", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 添加 ====================

    private fun showAddDialog() {
        val options = arrayOf(
            getString(R.string.add_app),
            getString(R.string.add_video)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.add_dialog_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, AppPickerActivity::class.java))
                    1 -> pickVideo()
                }
            }
            .show()
    }

    private fun pickVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            startActivityForResult(intent, REQ_PICK_VIDEO)
        } catch (_: Exception) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_COVER) {
            val entries = pendingCoverEntries
            pendingCoverEntries = null
            if (resultCode == RESULT_OK && entries != null) {
                val uri = data?.data
                if (uri != null) {
                    Thread {
                        val cover = CoverStore.importImage(this, uri) ?: ""
                        runOnUiThread { addPlaylist(entries, cover) }
                    }.start()
                    return
                }
            }
            if (entries != null) addPlaylist(entries, "")
            return
        }
        if (requestCode != REQ_PICK_VIDEO) return
        if (resultCode != RESULT_OK) return

        val uris = mutableListOf<Uri>()
        val clip = data?.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        } else {
            data?.data?.let { uris.add(it) }
        }
        if (uris.isEmpty()) return

        val entries = mutableListOf<VideoEntry>()
        for (u in uris) {
            try {
                contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            entries.add(VideoEntry(u.toString(), queryDisplayName(u)))
        }
        promptCover(entries)
    }

    private fun promptCover(entries: List<VideoEntry>) {
        val options = arrayOf(
            getString(R.string.cover_auto),
            getString(R.string.cover_pick),
            getString(R.string.cover_default)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.cover_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val uri = Uri.parse(entries.first().uri)
                        Thread {
                            val cover = CoverStore.captureFromVideo(this, uri) ?: ""
                            runOnUiThread { addPlaylist(entries, cover) }
                        }.start()
                    }
                    1 -> pickCoverImage(entries)
                    else -> addPlaylist(entries, "")
                }
            }
            .show()
    }

    private fun pickCoverImage(entries: List<VideoEntry>) {
        pendingCoverEntries = entries
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, REQ_PICK_COVER)
        } catch (_: Exception) {
            pendingCoverEntries = null
            addPlaylist(entries, "")
        }
    }

    private fun addPlaylist(entries: List<VideoEntry>, cover: String) {
        DesktopApps.addPlaylist(this, entries, buildPlaylistLabel(entries), cover)
        reloadAdapter()
    }

    private fun buildPlaylistLabel(entries: List<VideoEntry>): String {
        if (entries.isEmpty()) return ""
        val first = entries.first().name.ifEmpty { getString(R.string.playlist_unnamed, 1) }
        return if (entries.size == 1) first
        else getString(R.string.playlist_label_many, first, entries.size)
    }

    private fun queryDisplayName(uri: Uri): String = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) ?: "" else ""
        } ?: ""
    } catch (_: Exception) {
        ""
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
                if (pos != AdapterView.INVALID_POSITION && pos < tiles.size) {
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
            DragEvent.ACTION_DROP -> deleteDraggedTile()
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

    private fun finishDrag() {
        if (dragIndex in tiles.indices && dragTarget in tiles.indices && dragIndex != dragTarget) {
            val moved = tiles.removeAt(dragIndex)
            tiles.add(dragTarget, moved)
            DesktopApps.replace(this, tiles)
        }
        cleanupDrag()
    }

    private fun deleteDraggedTile() {
        if (dragIndex in tiles.indices) {
            tiles.removeAt(dragIndex)
            DesktopApps.replace(this, tiles)
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

    private fun refreshExitButton() {
        findViewById<Button>(R.id.btn_exit_lock).text =
            if (LockState.lockEnabled(this)) getString(R.string.btn_exit_lock) else getString(R.string.btn_lock)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onBackPressed() {
        // 桌面作为主页，不响应返回键
    }

    companion object {
        private const val REQ_PICK_VIDEO = 100
        private const val REQ_PICK_COVER = 101
    }
}
