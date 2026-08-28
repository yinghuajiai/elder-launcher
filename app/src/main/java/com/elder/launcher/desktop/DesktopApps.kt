package com.elder.launcher.desktop

import android.content.Context
import com.elder.launcher.player.Playlist
import com.elder.launcher.player.VideoEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * 桌面上已添加的磁贴（应用 + 视频），有序，JSON 序列化存于 SharedPreferences。
 * 兼容旧版「逗号分隔包名」格式：首次读取时自动迁移。
 */
object DesktopApps {

    private const val PREFS = "elder_desktop"
    private const val KEY_TILES = "tiles"
    private const val KEY_APPS_LEGACY = "apps"

    fun list(context: Context): List<DesktopTile> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TILES, null)
        if (raw != null) {
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    DesktopTile(
                        TileType.valueOf(o.getString("t")),
                        o.getString("p"),
                        o.optString("l", "")
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        // 迁移旧版逗号分隔的包名
        val legacy = prefs.getString(KEY_APPS_LEGACY, "") ?: ""
        val tiles = if (legacy.isEmpty()) emptyList()
        else legacy.split(",").map { DesktopTile.app(it) }
        if (tiles.isNotEmpty()) prefs.edit().putString(KEY_TILES, encode(tiles)).apply()
        return tiles
    }

    fun addApp(context: Context, pkg: String) {
        val current = list(context)
        if (current.any { it.type == TileType.APP && it.payload == pkg }) return
        persist(context, current + DesktopTile.app(pkg))
    }

    fun addVideo(context: Context, uri: String, label: String = "") {
        val current = list(context)
        if (current.any { it.type == TileType.VIDEO && it.payload == uri }) return
        persist(context, current + DesktopTile.video(uri, label))
    }

    fun addPlaylist(context: Context, entries: List<VideoEntry>, label: String) {
        val payload = Playlist.encode(entries)
        val current = list(context)
        if (current.any { it.type == TileType.PLAYLIST && it.payload == payload }) return
        persist(context, current + DesktopTile.playlist(payload, label))
    }

    fun containsPkg(context: Context, pkg: String): Boolean =
        list(context).any { it.type == TileType.APP && it.payload == pkg }

    fun containsVideo(context: Context, uri: String): Boolean =
        list(context).any { it.type == TileType.VIDEO && it.payload == uri }

    /** 覆盖保存整个列表（拖动排序 / 删除后持久化）。 */
    fun replace(context: Context, tiles: List<DesktopTile>) = persist(context, tiles)

    private fun persist(context: Context, tiles: List<DesktopTile>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TILES, encode(tiles)).apply()
    }

    private fun encode(tiles: List<DesktopTile>): String {
        val arr = JSONArray()
        for (t in tiles) {
            arr.put(JSONObject().put("t", t.type.name).put("p", t.payload).put("l", t.label))
        }
        return arr.toString()
    }
}
