package com.elder.launcher.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.elder.launcher.R
import com.elder.launcher.base.BaseActivity

/**
 * 视频播放页：播放一个播放列表（单个视频 = 只有一个成员的列表）。
 * 一个视频放完自动播下一个；顶部「列表」按钮可跳转任意一集。
 */
class VideoPlayerActivity : BaseActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playlist: List<VideoEntry>
    private var currentIndex = 0
    private var playlistKey = ""
    private var pendingResumePosition = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        playlistKey = intent.getStringExtra(EXTRA_KEY) ?: ""
        playlist = Playlist.decode(intent.getStringExtra(EXTRA_PLAYLIST) ?: "[]")
        if (playlist.isEmpty()) {
            finish()
            return
        }

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_playlist).setOnClickListener { showPlaylistDialog() }

        applyOrientation()

        val playerView = findViewById<PlayerView>(R.id.player_view)
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
        })

        // 记忆播放进度：恢复上次的集数与位置
        var startIndex = 0
        if (PlayerSettings.resumeEnabled(this)) {
            startIndex = PlayerSettings.resumeIndex(this, playlistKey).coerceIn(0, playlist.size - 1)
            pendingResumePosition = PlayerSettings.resumePosition(this, playlistKey)
        }
        playItem(startIndex)
        exo.playWhenReady = true
    }

    private fun playItem(index: Int) {
        val exo = player ?: return
        if (index !in playlist.indices) {
            finish()
            return
        }
        currentIndex = index
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

    private fun applyOrientation() {
        requestedOrientation = when (PlayerSettings.orientation(this)) {
            PlayerSettings.ORIENT_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            PlayerSettings.ORIENT_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
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
