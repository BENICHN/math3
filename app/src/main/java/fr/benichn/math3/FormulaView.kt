package fr.benichn.math3

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

class FormulaView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var box = SequenceFormulaBox().also {
        it.addBox(FractionFormulaBox())
        it.onPictureChanged += { _, _ ->
            invalidate()
        }
    }
    init {
        setWillNotDraw(false)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.translate(0f, height*0.5f)
        box.drawOnCanvas(canvas)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) {
            val b = box.findBox(e.x, e.y - height*0.5f)?.box
            b?.alert()
            Log.d("clic", "${e.x}, ${e.y - height*0.5f}, $b")
            Log.d("coord", "${b?.coord} ~ ${b?.seqCoord}")
        }
        return super.onTouchEvent(e)
    }

    companion object {
        val red = Paint().also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = 1f
            it.color = Color.RED }
        val cyan = Paint().also {
            it.style = Paint.Style.FILL
            it.color = Color.CYAN }
    }
}