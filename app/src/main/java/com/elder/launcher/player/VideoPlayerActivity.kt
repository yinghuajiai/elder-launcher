package com.elder.launcher.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.elder.launcher.R
import com.elder.launcher.base.BaseActivity
import com.elder.launcher.desktop.DesktopApps

/**
 * 视频播放页：播放一个播放列表，一个视频放完自动播下一个。
 * 自定义控件（返回/标题/列表/旋转/锁定）跟随控制器显隐；
 * 锁定后隐藏进度条，双击暂停/播放，单击空白处唤出控制 2.5 秒以便解锁。
 *
 * 断点续播：锁屏时释放播放器，回到前台自动重建并恢复播放。
 */
class VideoPlayerActivity : BaseActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playlist: List<VideoEntry>
    private var currentIndex = 0
    private var playlistKey = ""
    private var pendingResumePosition = 0L
    private var locked = false
    private var manualOrientation = false
    private var systemBarsVisible = true
    private var wasPlayingBeforePause = true
    private var playerReleased = false

    private lateinit var playerView: PlayerView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var touchOverlay: View
    private lateinit var btnLock: Button

    private val handler = Handler(Looper.getMainLooper())
    private val hideLockRunnable = Runnable { if (locked) setControlsVisible(false) }

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        playlistKey = intent.getStringExtra(EXTRA_KEY) ?: ""
        playlist = Playlist.decode(intent.getStringExtra(EXTRA_PLAYLIST) ?: "[]")
        if (playlist.isEmpty()) {
            finish()
            return
        }

        playerView = findViewById(R.id.player_view)
        topBar = findViewById(R.id.top_bar)
        bottomBar = findViewById(R.id.bottom_bar)
        touchOverlay = findViewById(R.id.touch_overlay)
        btnLock = findViewById(R.id.btn_lock)

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_playlist).setOnClickListener { showPlaylistDialog() }
        findViewById<Button>(R.id.btn_rotate).setOnClickListener { rotateManually() }
        btnLock.setOnClickListener { toggleLock() }

        val fromTile = intent.getBooleanExtra(EXTRA_FROM_TILE, false)
        val btnCover = findViewById<Button>(R.id.btn_cover)
        btnCover.visibility = if (fromTile) View.VISIBLE else View.GONE
        btnCover.setOnClickListener { showCoverDialog() }

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                togglePlayPause()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                showLockControls()
                return true
            }
        })
        touchOverlay.setOnTouchListener { _, ev ->
            gestureDetector.onTouchEvent(ev)
            true
        }

        applyOrientation()

        // 首次创建播放器
        var startIndex = 0
        if (PlayerSettings.resumeEnabled(this)) {
            startIndex = PlayerSettings.resumeIndex(this, playlistKey).coerceIn(0, playlist.size - 1)
            pendingResumePosition = PlayerSettings.resumePosition(this, playlistKey)
        }
        currentIndex = startIndex
        createPlayer()
    }

    // ==================== 播放器生命周期 ====================

    /** 创建并配置 ExoPlayer（首次创建 / 从后台恢复时重建）。 */
    private fun createPlayer() {
        val exo = ExoPlayer.Builder(this).build()
        playerView.player = exo
        player = exo
        playerReleased = false

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        if (pendingResumePosition > 1000) {
                            exo.seekTo(pendingResumePosition)
                            pendingResumePosition = 0L
                        }
                        if (wasPlayingBeforePause) exo.playWhenReady = true
                    }
                    Player.STATE_ENDED -> {
                        playNext()
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (!manualOrientation && PlayerSettings.orientation(this@VideoPlayerActivity) == PlayerSettings.ORIENT_AUTO) {
                    applyAutoOrientation(videoSize.width, videoSize.height)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                wasPlayingBeforePause = isPlaying || exo.playbackState == Player.STATE_BUFFERING
            }
        })

        // 自定义控件跟随 Media3 控制器显隐（未锁定时）
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (!locked) setControlsVisible(visibility == View.VISIBLE)
        })

        playItemInternal(currentIndex)
        exo.playWhenReady = wasPlayingBeforePause
    }

    private fun playItemInternal(index: Int) {
        val exo = player ?: return
        if (index !in playlist.indices) {
            finish()
            return
        }
        currentIndex = index
        manualOrientation = false
        val entry = playlist[index]
        val uri = Uri.parse(entry.uri)
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        findViewById<TextView>(R.id.tv_video_title).text = entry.name
    }

    override fun onPause() {
        super.onPause()
        saveResumeState()
    }

    override fun onResume() {
        super.onResume()
        val exo = player
        if (exo != null && !playerReleased) {
            // 播放器还在（没有走过 onStop），直接恢复
            if (PlayerSettings.autoResumeOnUnlock(this) && wasPlayingBeforePause) {
                exo.playWhenReady = true
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 如果之前 onStop 释放了播放器，在这里重建
        if (playerReleased && ::playlist.isInitialized && playlist.isNotEmpty()) {
            // 恢复续播位置
            if (PlayerSettings.resumeEnabled(this)) {
                currentIndex = PlayerSettings.resumeIndex(this, playlistKey).coerceIn(0, playlist.size - 1)
                pendingResumePosition = PlayerSettings.resumePosition(this, playlistKey)
            }
            createPlayer()
            // 自动续播
            if (PlayerSettings.autoResumeOnUnlock(this) && wasPlayingBeforePause) {
                player?.playWhenReady = true
            }
        }
    }

    override fun onStop() {
        saveResumeState()
        val exo = player
        if (exo != null) {
            exo.release()
            player = null
            playerReleased = true
        }
        super.onStop()
    }

    override fun onDestroy() {
        val exo = player
        if (exo != null) {
            exo.release()
            player = null
        }
        super.onDestroy()
    }

    private fun saveResumeState() {
        val exo = player ?: return
        if (playlistKey.isNotEmpty() && PlayerSettings.resumeEnabled(this)) {
            PlayerSettings.saveResume(this, playlistKey, currentIndex, exo.currentPosition.coerceAtLeast(0))
        }
    }

    // ==================== UI ====================

    private fun setControlsVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        topBar.visibility = v
        bottomBar.visibility = v
        setSystemBarsVisible(visible)
    }

    @Suppress("DEPRECATION")
    private fun setSystemBarsVisible(visible: Boolean) {
        systemBarsVisible = visible
        window.decorView.systemUiVisibility = if (visible) {
            View.SYSTEM_UI_FLAG_VISIBLE
        } else {
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setSystemBarsVisible(systemBarsVisible)
    }

    private fun showLockControls() {
        setControlsVisible(true)
        handler.removeCallbacks(hideLockRunnable)
        handler.postDelayed(hideLockRunnable, 2500L)
    }

    private fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    private fun toggleLock() {
        locked = !locked
        if (locked) {
            playerView.useController = false
            touchOverlay.visibility = View.VISIBLE
            setControlsVisible(false)
        } else {
            playerView.useController = true
            touchOverlay.visibility = View.GONE
            playerView.showController()
        }
        btnLock.text = getString(if (locked) R.string.btn_video_unlock else R.string.btn_video_lock)
    }

    private fun playItem(index: Int) {
        playItemInternal(index)
    }

    private fun playNext() {
        if (currentIndex + 1 < playlist.size) {
            playItem(currentIndex + 1)
        } else {
            finish()
        }
    }

    private fun applyOrientation() {
        requestedOrientation = when (PlayerSettings.orientation(this)) {
            PlayerSettings.ORIENT_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            PlayerSettings.ORIENT_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    private fun applyAutoOrientation(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        requestedOrientation = if (width > height)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun rotateManually() {
        manualOrientation = true
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (landscape)
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun showPlaylistDialog() {
        val names = playlist.mapIndexed { i, e ->
            e.name.ifEmpty { getString(R.string.playlist_unnamed, i + 1) }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.playlist_title))
            .setSingleChoiceItems(names, currentIndex) { d, which ->
                playItem(which)
                d.dismiss()
            }
            .setPositiveButton(getString(R.string.add_video)) { _, _ ->
                showAddToPlaylistDialog()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 在视频列表弹窗中追加本地或网络视频。 */
    private fun showAddToPlaylistDialog() {
        val options = arrayOf(
            getString(R.string.add_video),
            getString(R.string.add_video_network)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_dialog_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickVideoForPlaylist()
                    1 -> addNetworkVideoForPlaylist()
                }
            }
            .show()
    }

    private fun pickVideoForPlaylist() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            startActivityForResult(intent, REQ_PICK_PLAYLIST_VIDEO)
        } catch (_: Exception) {
            toast("无法打开文件选择器")
        }
    }

    private fun addNetworkVideoForPlaylist() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val urlInput = EditText(this).apply {
            hint = getString(R.string.add_video_url_hint)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.add_video_name_hint)
            setSingleLine()
        }
        container.addView(urlInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        container.addView(nameInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 24
        })

        AlertDialog.Builder(this)
            .setTitle(R.string.add_video_network)
            .setView(container)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val url = urlInput.text.toString().trim()
                if (url.isEmpty()) {
                    toast("请输入视频地址")
                    return@setPositiveButton
                }
                val name = nameInput.text.toString().trim().ifEmpty { url }
                val entry = VideoEntry(url, name, VideoType.NETWORK)
                appendToPlaylist(listOf(entry))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 将新视频条目追加到当前播放列表并更新桌面磁贴。 */
    private fun appendToPlaylist(newEntries: List<VideoEntry>) {
        val oldPayload = Playlist.encode(playlist)
        playlist = playlist + newEntries
        val newPayload = Playlist.encode(playlist)
        // 更新桌面磁贴
        val tiles = DesktopApps.list(this)
        var updated = false
        val updatedTiles = tiles.map { tile ->
            if (tile.payload == oldPayload) {
                updated = true
                tile.copy(payload = newPayload, label = buildPlaylistLabel(playlist))
            } else if (tile.type == com.elder.launcher.desktop.TileType.VIDEO && tile.payload == playlistKey) {
                // 原来是单视频，升级为列表
                updated = true
                com.elder.launcher.desktop.DesktopTile.playlist(
                    newPayload, buildPlaylistLabel(playlist), tile.cover
                )
            } else {
                tile
            }
        }
        if (updated) {
            DesktopApps.replace(this, updatedTiles)
        }
        toast("已添加 ${newEntries.size} 个视频")
    }

    private fun buildPlaylistLabel(entries: List<VideoEntry>): String {
        if (entries.isEmpty()) return ""
        val first = entries.first().name.ifEmpty { getString(R.string.playlist_unnamed, 1) }
        return if (entries.size == 1) first
        else getString(R.string.playlist_label_many, first, entries.size)
    }

    private fun showCoverDialog() {
        val options = arrayOf(
            getString(R.string.cover_auto),
            getString(R.string.cover_pick),
            getString(R.string.cover_default)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.cover_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> captureCurrentCover()
                    1 -> pickCoverImage()
                    else -> applyCover("")
                }
            }
            .show()
    }

    private fun captureCurrentCover() {
        val entry = playlist[currentIndex]
        if (entry.type == VideoType.NETWORK) {
            toast("网络视频暂不支持自动截帧")
            return
        }
        val uri = Uri.parse(entry.uri)
        Thread {
            val cover = CoverStore.captureFromVideo(this, uri) ?: ""
            runOnUiThread { applyCover(cover) }
        }.start()
    }

    private fun pickCoverImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, REQ_PICK_COVER)
        } catch (_: Exception) {
            toast("无法打开图片选择器")
        }
    }

    private fun applyCover(cover: String) {
        DesktopApps.updateCover(this, playlistKey, cover)
        toast(if (cover.isEmpty()) "已用默认图标" else "封面已更新")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_COVER) {
            if (resultCode != RESULT_OK) return
            val uri = data?.data ?: return
            Thread {
                val cover = CoverStore.importImage(this, uri) ?: ""
                runOnUiThread { applyCover(cover) }
            }.start()
            return
        }
        if (requestCode == REQ_PICK_PLAYLIST_VIDEO) {
            if (resultCode != RESULT_OK) return
            val uris = mutableListOf<android.net.Uri>()
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
                val name = queryDisplayName(u)
                entries.add(VideoEntry(u.toString(), name, VideoType.LOCAL))
            }
            appendToPlaylist(entries)
            return
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) ?: "" else ""
        } ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_KEY = "playlist_key"
        const val EXTRA_PLAYLIST = "playlist_json"
        const val EXTRA_FROM_TILE = "from_tile"
        private const val REQ_PICK_COVER = 200
        private const val REQ_PICK_PLAYLIST_VIDEO = 201
    }
}