package com.elder.launcher.player

import org.json.JSONArray
import org.json.JSONObject

/** 播放列表中的单个视频。 */
data class VideoEntry(val uri: String, val name: String)

/** 播放列表的 JSON 编解码（供桌面磁贴存储与播放器传递）。 */
object Playlist {

    fun encode(entries: List<VideoEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(JSONObject().put("u", e.uri).put("n", e.name))
        }
        return arr.toString()
    }

    fun decode(json: String): List<VideoEntry> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VideoEntry(o.getString("u"), o.optString("n", ""))
        }
    } catch (_: Exception) {
        emptyList()
    }
}
