package com.elder.launcher.desktop

/**
 * 桌面磁贴：统一表示「应用」与「视频」两类可添加条目。
 * - APP   payload = 应用包名
 * - VIDEO payload = 视频 Uri（content://），label = 显示名
 * 后续可继续扩展类型（如联系人、快捷指令等）。
 */
enum class TileType { APP, VIDEO, PLAYLIST }

data class DesktopTile(
    val type: TileType,
    val payload: String,
    val label: String = ""
) {
    companion object {
        fun app(pkg: String) = DesktopTile(TileType.APP, pkg)
        fun video(uri: String, label: String = "") = DesktopTile(TileType.VIDEO, uri, label)
        fun playlist(payload: String, label: String) = DesktopTile(TileType.PLAYLIST, payload, label)
    }
}
