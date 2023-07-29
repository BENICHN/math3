package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

class FormulaCell(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    val inputFV = FormulaView(context)
    val outputFV = FormulaView(context).apply {
        isReadOnly = true
    }

    fun hline(height: Int, color: Int) = View(context).apply {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            height
        )
        foreground = ColorDrawable(color)
    }
    fun vline(width: Int, color: Int) = View(context).apply {
        layoutParams = LayoutParams(
            width,
            LayoutParams.MATCH_PARENT
        )
        foreground = ColorDrawable(color)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint) // !
    }

    init {
        orientation = VERTICAL
        addView(inputFV)
        addView(hline(2, Color.GRAY))
        addView(outputFV)
    }

    companion object {
        val borderPaint = Paint().apply {
            color = Color.GRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
    }
}