package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.GestureDetectorCompat
import fr.benichn.math3.graphics.boxes.AlignFormulaBox
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.caret.BoxCaret
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.types.RectPoint

class FormulaView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var box = AlignFormulaBox(InputFormulaBox(), RectPoint.BOTTOM_CENTER)
        private set
    var caret: BoxCaret
        private set
    val offset
        get() = PointF(width * 0.5f, height - 48f)

    init {
        setWillNotDraw(false)
        box.onPictureChanged += { _, _ ->
            invalidate() }
        caret = box.createCaret()
    }
    var selectionStartSingle: CaretPosition.Single? = null
    private fun findBox(e: MotionEvent) = offset.let { box.findBox(e.x - it.x, e.y - it.y) }

    private val gestureDetector = GestureDetectorCompat(context, object : OnGestureListener {
        override fun onDown(e: MotionEvent): Boolean {
            Log.d("down", e.toString())
            return true
        }

        override fun onShowPress(e: MotionEvent) {
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val b = findBox(e)
            caret.position = b.toCaretPosition()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            selectionStartSingle = findBox(e).toSingle()
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
            return true
        }

    })

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        offset.let { canvas.translate(it.x, it.y) }
        box.drawOnCanvas(canvas)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean = selectionStartSingle?.let {
        if (e.action == MotionEvent.ACTION_MOVE) {
            Log.d("scrol", e.toString())
            val p = findBox(e).toSingle()
            selectionStartSingle?.let { sp ->
                caret.position = p?.let { FormulaBox.getSelectionFromSingles(sp, it) } ?: CaretPosition.None }
            true
        } else if (e.action == MotionEvent.ACTION_UP) {
            selectionStartSingle = null
            true
        } else {
            false
        }
    } ?: gestureDetector.onTouchEvent(e)

    companion object {
        val red = Paint().also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = 1f
            it.color = Color.RED }
    }
}