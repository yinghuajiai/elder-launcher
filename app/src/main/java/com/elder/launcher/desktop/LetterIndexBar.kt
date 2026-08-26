package com.elder.launcher.desktop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** 右侧 A-Z 字母索引条：点击或滑动时回调选中的字母。 */
class LetterIndexBar(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var letters: List<Char> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var onLetterSelected: ((Char) -> Unit)? = null
    var onTouchEnded: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A5F7A.toInt()
        textSize = 14f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (letters.isEmpty()) return
        val n = letters.size
        val cellH = height.toFloat() / n
        val baseline = (paint.ascent() + paint.descent()) / 2f
        for (i in letters.indices) {
            val cy = cellH * i + cellH / 2f
            canvas.drawText(letters[i].toString(), width / 2f, cy - baseline, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val n = letters.size
                if (n > 0 && height > 0) {
                    val index = ((event.y / height) * n).toInt().coerceIn(0, n - 1)
                    onLetterSelected?.invoke(letters[index])
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onTouchEnded?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
