package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import fr.benichn.math3.graphics.boxes.AlignFormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.caret.BoxCaret
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.types.RectPoint

class FormulaView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var box = AlignFormulaBox(InputFormulaBox(), RectPoint.BOTTOM_CENTER).also { it.createCaret() }
    val offset
        get() = PointF(width * 0.5f, height - 48f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        offset.let { canvas.translate(it.x, it.y) }
        box.drawOnCanvas(canvas)
    }

    init {
        setWillNotDraw(false)
        box.onPictureChanged += { _, _ ->
            invalidate() }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) {
            val b = offset.let { box.findBox(e.x - it.x, e.y - it.y) }
            // b?.box?.alert()
            // Log.d("clic", "${e.x}, ${e.y - height*0.5f}, $b")
            Log.d("coord", "$b ~ ${b.toCaretPosition()}")
            box.isSelected = false
            box.caret!!.position = b.toCaretPosition()
        }
        return super.onTouchEvent(e)
    }

    companion object {
        val red = Paint().also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = 1f
            it.color = Color.RED }
    }
}