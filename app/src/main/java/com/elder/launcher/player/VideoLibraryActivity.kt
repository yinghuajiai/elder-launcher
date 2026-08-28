package com.elder.launcher.player

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.elder.launcher.R
import com.elder.launcher.base.BaseActivity
import com.elder.launcher.permission.PermissionDef

/**
 * 播放器主页：列出本机视频，点击进入播放。
 * 目前是最简形态，后续（文件夹/封面/排序）在此页上迭代。
 */
class VideoLibraryActivity : BaseActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private var videos: List<Video> = emptyList()

    private data class Video(val id: Long, val title: String, val durationMs: Long) {
        val uri: String
            get() = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                .toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_library)

        listView = findViewById(R.id.lv_videos)
        emptyView = findViewById(R.id.tv_empty)
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }

        listView.setOnItemClickListener { _, _, position, _ ->
            val v = videos[position]
            startActivity(
                Intent(this, VideoPlayerActivity::class.java)
                    .putExtra(VideoPlayerActivity.EXTRA_URI, v.uri)
                    .putExtra(VideoPlayerActivity.EXTRA_TITLE, v.title)
            )
        }

        loadVideos()
    }

    private fun loadVideos() {
        requirePermissions(PermissionDef.STORAGE) { granted, _ ->
            if (granted) {
                Thread {
                    val list = queryVideos()
                    runOnUiThread { render(list) }
                }.start()
            } else {
                render(emptyList())
            }
        }
    }

    private fun queryVideos(): List<Video> {
        val result = mutableListOf<Video>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION
        )
        try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null,
                MediaStore.Video.Media.DATE_MODIFIED + " DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                while (c.moveToNext()) {
                    result.add(
                        Video(c.getLong(idCol), c.getString(titleCol) ?: "", c.getLong(durCol))
                    )
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun render(list: List<Video>) {
        videos = list
        listView.adapter = VideoAdapter(list)
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        listView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private inner class VideoAdapter(private val items: List<Video>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@VideoLibraryActivity)
                .inflate(R.layout.item_video_library, parent, false)
            val v = items[position]
            view.findViewById<TextView>(R.id.tv_video_name).text = v.title
            view.findViewById<TextView>(R.id.tv_video_duration).text = formatDuration(v.durationMs)
            return view
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
