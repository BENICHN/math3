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
import kotlin.math.pow

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
    var caretPosOnDown: CaretPosition.Single? = null
    var fixedXOnDown: Float? = null
    var selectionModificationStart: CaretPosition.Single? = null
    var isMovingCaret = false
    var selectionStartSingle: CaretPosition.Single? = null
    private fun getRootPos(e: MotionEvent) = offset.let { PointF(e.x - it.x, e.y - it.y) }
    private fun findBox(e: MotionEvent) = box.findBox(getRootPos(e))

    private val gestureDetector = GestureDetectorCompat(context, object : OnGestureListener {
        override fun onDown(e: MotionEvent): Boolean {
            val s = findBox(e).toSingle()
            val p = caret.position
            when {
                p == s -> {
                    caretPosOnDown = s
                }
                p is CaretPosition.Selection && p.isMutable -> {
                    val pos = getRootPos(e)
                    p.bounds.also {
                        if (Utils.squareDistFromLineToPoint(it.right, it.top, it.bottom, pos.x, pos.y) < MAX_TOUCH_DIST_SQ) {
                            fixedXOnDown = it.left
                            selectionModificationStart = p.leftSingle
                        } else if (Utils.squareDistFromLineToPoint(it.left, it.top, it.bottom, pos.x, pos.y) < MAX_TOUCH_DIST_SQ) {
                            fixedXOnDown = it.right
                            selectionModificationStart = p.rightSingle
                        }
                    }
                }
                else -> { }
            }
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
            if (!isMovingCaret) {
                caretPosOnDown = null
                selectionStartSingle = findBox(e).toSingle()
            }
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

    override fun onTouchEvent(e: MotionEvent): Boolean =
        selectionStartSingle?.let { sp ->
            when (e.action) {
                MotionEvent.ACTION_MOVE -> {
                    if (caret.fixedX == null) {
                        caret.fixedX = sp.getAbsPosition().x
                    }
                    caret.position = findBox(e).toSingle()?.let { p -> CaretPosition.Selection.fromSingles(sp, p) } ?: CaretPosition.None
                    caret.absolutePosition = getRootPos(e)
                }
                MotionEvent.ACTION_UP -> {
                    selectionStartSingle = null
                    caret.absolutePosition = null
                    caret.fixedX = null
                }
                else -> {
                }
            }
            true
        } ?: caretPosOnDown?.let { cp ->
            when (e.action) {
                MotionEvent.ACTION_MOVE -> {
                    val p = findBox(e).toCaretPosition()
                    if (p != caretPosOnDown) isMovingCaret = true
                    caret.position = p
                    caret.absolutePosition = getRootPos(e)
                }
                MotionEvent.ACTION_UP -> {
                    isMovingCaret = false
                    caretPosOnDown = null
                    caret.absolutePosition = null
                }
                else -> {
                }
            }
            true
        } ?: fixedXOnDown?.let { x ->
            when (e.action) {
                MotionEvent.ACTION_MOVE -> {
                    isMovingCaret = true
                    if (caret.fixedX == null) {
                        caret.fixedX = x
                    }
                    caret.position = findBox(e).toSingle()?.let { p -> CaretPosition.Selection.fromSingles(selectionModificationStart!!, p) } ?: CaretPosition.None
                    caret.absolutePosition = getRootPos(e)
                }
                MotionEvent.ACTION_UP -> {
                    isMovingCaret = false
                    fixedXOnDown = null
                    selectionModificationStart = null
                    caret.absolutePosition = null
                    caret.fixedX = null
                }
                else -> {
                }
            }
            true
        } ?: gestureDetector.onTouchEvent(e)

    companion object {
        val red = Paint().also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = 1f
            it.color = Color.RED }

        val MAX_TOUCH_DIST_SQ = 18f.pow(2)
    }
}