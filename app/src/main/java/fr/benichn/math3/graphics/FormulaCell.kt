package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import fr.benichn.math3.Utils.Companion.dp
import fr.benichn.math3.graphics.boxes.IntegralFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox

class FormulaCell(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    val inputFV = FormulaView(context)
    val outputFV = FormulaView(context).apply {
        // isReadOnly = true
        scale = 0.8f
        magneticScale = 0.8f
        input.addBoxes(IntegralFormulaBox().apply { integrand.addBoxes(TextFormulaBox("output")) })
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
        canvas.drawLine(0f, 0f, width.toFloat(), 0f, borderPaint)
        canvas.drawLine(0f, height.toFloat(), width.toFloat(), height.toFloat(), borderPaint)
    }

    val fvs = listOf(inputFV, outputFV)
    private var currentFV: FormulaView? = null

    private fun getFV(x: Float, y: Float) = fvs.firstOrNull { fv ->
        val r = Rect()
        fv.getDrawingRect(r)
        offsetDescendantRectToMyCoords(fv, r)
        r.contains(x.toInt(), y.toInt())
    }

    private var syncScales = false

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        // fvs.forEach { fv -> fv.dispatchTouchEvent(e) }
        // return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentFV = getFV(e.x, e.y)
                if (currentFV == inputFV) Log.d("fv", "inp")
                if (currentFV == outputFV) Log.d("fv", "otp")
            }
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                syncScales = (1 until e.pointerCount).any { i ->
                    val fv = getFV(e.getX(i), e.getY(i))
                    fv != null && fv != currentFV
                }
            }
        }
        return currentFV?.let { fv ->
            val r = Rect()
            fv.getDrawingRect(r)
            offsetDescendantRectToMyCoords(fv, r)
            e.offsetLocation(-r.left.toFloat(), -r.top.toFloat())
            fv.dispatchTouchEvent(MotionEvent.obtain(e))
        } ?: super.dispatchTouchEvent(e)
    }

    init {
        orientation = VERTICAL
        inputFV.onScaleChanged += { s, e ->
            if (syncScales && currentFV == s) {
                val ratio = e.new / e.old
                outputFV.scale *= ratio
            }
        }
        outputFV.onScaleChanged += { s, e ->
            if (syncScales && currentFV == s) {
                val ratio = e.new / e.old
                inputFV.scale *= ratio
            }
        }
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