package com.elder.launcher.desktop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 指针时钟：圆形或圆角方形表盘，表盘内标 1~12 数字，含时针 / 分针 / 秒针，每秒刷新。
 * 数字标注方便长辈对照「指针指向几」判断几点。
 */
class AnalogClockView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Shape { CIRCLE, ROUNDED_SQUARE }

    var shape: Shape = Shape.CIRCLE
        set(value) {
            field = value
            invalidate()
        }

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFDFBF6.toInt()
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = 0xFF1A5F7A.toInt()
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A5F7A.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        strokeCap = Paint.Cap.ROUND
        color = 0xFF1A5F7A.toInt()
    }
    private val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
        color = 0xFF1A5F7A.toInt()
    }
    private val secondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        strokeCap = Paint.Cap.ROUND
        color = 0xFFC0392B.toInt()
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF1A5F7A.toInt()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(tick)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(tick)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(4f)
        if (radius <= 0) return

        // 表盘
        if (shape == Shape.CIRCLE) {
            canvas.drawCircle(cx, cy, radius, facePaint)
            canvas.drawCircle(cx, cy, radius, borderPaint)
        } else {
            val corner = dp(18f)
            val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawRoundRect(rect, corner, corner, facePaint)
            canvas.drawRoundRect(rect, corner, corner, borderPaint)
        }

        // 表盘数字 1~12
        numberPaint.textSize = radius * 0.26f
        val numRadius = radius * 0.78f
        for (i in 1..12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            val x = cx + (cos(angle) * numRadius).toFloat()
            val y = cy + (sin(angle) * numRadius).toFloat()
            val baseline = y - (numberPaint.ascent() + numberPaint.descent()) / 2f
            canvas.drawText(i.toString(), x, baseline, numberPaint)
        }

        // 指针（藏在数字圈内）
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR).toFloat()
        val minute = now.get(Calendar.MINUTE).toFloat()
        val second = now.get(Calendar.SECOND).toFloat()

        drawHand(canvas, cx, cy, (hour + minute / 60f) * 30f, radius * 0.42f, hourPaint)
        drawHand(canvas, cx, cy, (minute + second / 60f) * 6f, radius * 0.56f, minutePaint)
        drawHand(canvas, cx, cy, second * 6f, radius * 0.64f, secondPaint)

        canvas.drawCircle(cx, cy, dp(4f), centerPaint)
    }

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, angleDeg: Float, length: Float, paint: Paint) {
        val angle = Math.toRadians((angleDeg - 90).toDouble())
        canvas.drawLine(
            cx, cy,
            cx + (cos(angle) * length).toFloat(),
            cy + (sin(angle) * length).toFloat(),
            paint
        )
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
