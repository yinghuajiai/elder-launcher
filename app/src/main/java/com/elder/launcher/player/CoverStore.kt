package com.elder.launcher.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 磁贴封面管理：从视频截帧、导入图片、读取封面。
 * 封面统一压缩保存到 app 私有目录 filesDir/covers/，路径存进磁贴。
 */
object CoverStore {

    private const val DIR = "covers"
    private const val MAX_SIZE = 240

    fun coverDir(context: Context): File {
        val dir = File(context.filesDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 从视频截取一帧保存为封面，返回绝对路径；失败返回 null。 */
    @Suppress("DEPRECATION")
    fun captureFromVideo(context: Context, uri: Uri): String? = try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val frame = mmr.getFrameAtTime(1_000_000) ?: mmr.getFrameAtTime(-1)
        mmr.release()
        if (frame == null) null else save(context, scale(frame))
    } catch (_: Exception) {
        null
    }

    /** 把用户选的图片导入为封面，返回绝对路径；失败返回 null。 */
    fun importImage(context: Context, uri: Uri): String? = try {
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
        if (bmp == null) null else save(context, scale(bmp))
    } catch (_: Exception) {
        null
    }

    fun load(path: String): Bitmap? = try {
        BitmapFactory.decodeFile(path)
    } catch (_: Exception) {
        null
    }

    private fun scale(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= MAX_SIZE && h <= MAX_SIZE) return bmp
        val s = MAX_SIZE.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bmp, (w * s).toInt(), (h * s).toInt(), true)
    }

    private fun save(context: Context, bmp: Bitmap): String {
        val file = File(coverDir(context), "c_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return file.absolutePath
    }
}
