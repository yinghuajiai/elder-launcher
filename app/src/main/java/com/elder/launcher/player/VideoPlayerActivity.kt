package com.elder.launcher.player

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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.elder.launcher.R
import com.elder.launcher.base.BaseActivity

/**
 * 视频播放页：播放一个播放列表，一个视频放完自动播下一个。
 * 自定义控件（返回/标题/列表/旋转/锁定）跟随控制器显隐；
 * 锁定后隐藏进度条，双击暂停/播放，单击空白处唤出控制 2.5 秒以便解锁。
 */
class VideoPlayerActivity : BaseActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playlist: List<VideoEntry>
    private var currentIndex = 0
    private var playlistKey = ""
    private var pendingResumePosition = 0L
    private var locked = false
    private var manualOrientation = false

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

        val exo = ExoPlayer.Builder(this).build()
        playerView.player = exo
        player = exo

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    if (pendingResumePosition > 1000) {
                        exo.seekTo(pendingResumePosition)
                        pendingResumePosition = 0L
                    }
                } else if (state == Player.STATE_ENDED) {
                    playNext()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (!manualOrientation && PlayerSettings.orientation(this@VideoPlayerActivity) == PlayerSettings.ORIENT_AUTO) {
                    applyAutoOrientation(videoSize.width, videoSize.height)
                }
            }
        })

        // 自定义控件跟随 Media3 控制器显隐（未锁定时）
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (!locked) setControlsVisible(visibility == View.VISIBLE)
        })

        var startIndex = 0
        if (PlayerSettings.resumeEnabled(this)) {
            startIndex = PlayerSettings.resumeIndex(this, playlistKey).coerceIn(0, playlist.size - 1)
            pendingResumePosition = PlayerSettings.resumePosition(this, playlistKey)
        }
        playItem(startIndex)
        exo.playWhenReady = true
    }

    private fun setControlsVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        topBar.visibility = v
        bottomBar.visibility = v
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
        val exo = player ?: return
        if (index !in playlist.indices) {
            finish()
            return
        }
        currentIndex = index
        manualOrientation = false
        exo.setMediaItem(MediaItem.fromUri(Uri.parse(playlist[index].uri)))
        exo.prepare()
        findViewById<TextView>(R.id.tv_video_title).text = playlist[index].name
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
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onStop() {
        val exo = player
        if (exo != null && playlistKey.isNotEmpty() && PlayerSettings.resumeEnabled(this)) {
            PlayerSettings.saveResume(this, playlistKey, currentIndex, exo.currentPosition.coerceAtLeast(0))
        }
        exo?.release()
        player = null
        super.onStop()
    }

    companion object {
        const val EXTRA_KEY = "playlist_key"
        const val EXTRA_PLAYLIST = "playlist_json"
    }
}
