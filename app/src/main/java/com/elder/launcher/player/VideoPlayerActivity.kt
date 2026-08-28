package com.elder.launcher.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.elder.launcher.R
import com.elder.launcher.base.BaseActivity

/**
 * 视频播放页：用 Media3/ExoPlayer 播放单个视频。
 * 只取播放逻辑，外观简洁（系统控制器 + 返回键）。
 */
class VideoPlayerActivity : BaseActivity() {

    private var player: ExoPlayer? = null
    private var uri: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        uri = intent.getStringExtra(EXTRA_URI) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        findViewById<TextView>(R.id.tv_video_title).text = title
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }

        applyOrientation()

        val playerView = findViewById<PlayerView>(R.id.player_view)
        val exo = ExoPlayer.Builder(this).build()
        playerView.player = exo
        player = exo

        exo.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // 准备好后按需续播
                if (state == Player.STATE_READY && PlayerSettings.resumeEnabled(this@VideoPlayerActivity)) {
                    val pos = PlayerSettings.lastPosition(this@VideoPlayerActivity, uri)
                    if (pos > 1000) exo.seekTo(pos)
                }
            }
        })
        exo.prepare()
        exo.playWhenReady = true
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
        if (exo != null && uri.isNotEmpty() && PlayerSettings.resumeEnabled(this)) {
            val pos = exo.currentPosition.coerceAtLeast(0)
            PlayerSettings.savePosition(this, uri, pos)
        }
        exo?.release()
        player = null
        super.onStop()
    }

    companion object {
        const val EXTRA_URI = "video_uri"
        const val EXTRA_TITLE = "video_title"
    }
}
