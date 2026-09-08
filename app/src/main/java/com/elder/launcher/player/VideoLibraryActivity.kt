package com.elder.launcher.player

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.elder.launcher.R
import com.elder.launcher.base.BaseActivity

/**
 * 播放器主页：视频库。支持排序（添加顺序/A-Z/手动拖动）和网络视频添加。
 */
class VideoLibraryActivity : BaseActivity() {

    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private var videos: List<VideoEntry> = emptyList()
    private lateinit var btnSort: Button
    private lateinit var btnAddNetwork: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_library)

        listView = findViewById(R.id.lv_videos)
        emptyView = findViewById(R.id.tv_empty)
        btnSort = findViewById(R.id.btn_sort)
        btnAddNetwork = findViewById(R.id.btn_add_network)
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }

        btnSort.setOnClickListener { showSortDialog() }
        btnAddNetwork.setOnClickListener { showAddNetworkDialog() }

        listView.setOnItemClickListener { _, _, position, _ ->
            val v = videos[position]
            startActivity(
                Intent(this, VideoPlayerActivity::class.java)
                    .putExtra(VideoPlayerActivity.EXTRA_KEY, v.uri)
                    .putExtra(VideoPlayerActivity.EXTRA_PLAYLIST, Playlist.encode(listOf(v)))
            )
        }

        loadAndRender()
    }

    override fun onResume() {
        super.onResume()
        loadAndRender()
    }

    private fun loadAndRender() {
        val sortMode = PlayerSettings.sortMode(this)
        val all = Playlist.decode(Playlist.encode(emptyList())) // 占位
        // 从桌面磁贴中收集所有视频条目
        val entries = collectAllVideoEntries()
        videos = if (sortMode == SortMode.MANUAL) {
            entries
        } else {
            Playlist.sorted(entries, sortMode)
        }
        render(videos)
    }

    /** 从桌面磁贴收集所有视频条目（本地 + 网络）。 */
    private fun collectAllVideoEntries(): List<VideoEntry> {
        val result = mutableListOf<VideoEntry>()
        try {
            val tiles = com.elder.launcher.desktop.DesktopApps.list(this)
            for (tile in tiles) {
                when (tile.type) {
                    com.elder.launcher.desktop.TileType.VIDEO -> {
                        result.add(VideoEntry(tile.payload, tile.label, VideoType.LOCAL))
                    }
                    com.elder.launcher.desktop.TileType.PLAYLIST -> {
                        result.addAll(Playlist.decode(tile.payload))
                    }
                    else -> {}
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_add_order),
            getString(R.string.sort_name_az),
            getString(R.string.sort_name_za)
        )
        val current = PlayerSettings.sortMode(this)
        val checked = when (current) {
            SortMode.ADD_ORDER -> 0
            SortMode.NAME_AZ -> 1
            SortMode.NAME_ZA -> 2
            SortMode.MANUAL -> 0
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sort_title))
            .setSingleChoiceItems(options, checked) { d, which ->
                val mode = when (which) {
                    0 -> SortMode.ADD_ORDER
                    1 -> SortMode.NAME_AZ
                    else -> SortMode.NAME_ZA
                }
                PlayerSettings.setSortMode(this, mode)
                d.dismiss()
                loadAndRender()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddNetworkDialog() {
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
                    Toast.makeText(this, "请输入视频地址", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val name = nameInput.text.toString().trim().ifEmpty { url }
                val entry = VideoEntry(url, name, VideoType.NETWORK)
                com.elder.launcher.desktop.DesktopApps.addPlaylist(
                    this, listOf(entry),
                    name, ""
                )
                loadAndRender()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun render(list: List<VideoEntry>) {
        videos = list
        listView.adapter = VideoAdapter(list)
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        listView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private inner class VideoAdapter(private val items: List<VideoEntry>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@VideoLibraryActivity)
                .inflate(R.layout.item_video_library, parent, false)
            val v = items[position]
            val tag = if (v.type == VideoType.NETWORK) "🌐" else "📁"
            view.findViewById<TextView>(R.id.tv_video_name).text = "$tag ${v.name}"
            view.findViewById<TextView>(R.id.tv_video_duration).text =
                if (v.type == VideoType.NETWORK) getString(R.string.video_type_network) else v.uri
            return view
        }
    }

    companion object {
        private const val TAG = "VideoLibrary"
    }
}
