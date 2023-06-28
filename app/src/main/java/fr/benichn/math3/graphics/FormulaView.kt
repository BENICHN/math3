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
        caret.onPositionChanged += { _, e ->
            when (e.new) {
                is CaretPosition.None, is CaretPosition.Single -> {
                    if (fixedXOnDown != null) {
                        isMovingCaret = false
                        fixedXOnDown = null
                        selectionModificationStart = null
                    }
                    selectionStartSingle = null
                    caret.fixedX = null
                    caret.absolutePosition = null
                }
                else -> { }
            }
        }
    }
    var caretPosOnDown: CaretPosition.Single? = null
    var fixedXOnDown: Float? = null
    var selectionModificationStart: CaretPosition.Single? = null
    var isMovingCaret = false
    var selectionStartSingle: CaretPosition.Single? = null
    private fun getRootPos(e: MotionEvent) = offset.run { PointF(e.x - x, e.y - y) }
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
                    p.bounds.apply {
                        if (Utils.squareDistFromLineToPoint(right, top, bottom, pos.x, pos.y) < MAX_TOUCH_DIST_SQ) {
                            fixedXOnDown = left
                            selectionModificationStart = p.leftSingle
                        } else if (Utils.squareDistFromLineToPoint(left, top, bottom, pos.x, pos.y) < MAX_TOUCH_DIST_SQ) {
                            fixedXOnDown = right
                            selectionModificationStart = p.rightSingle
                        } else if (contains(pos.x, pos.y)) {

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
        canvas.drawColor(backgroundPaint.color)
        offset.apply { canvas.translate(x, y) }
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
        val red = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.RED }
        val backgroundPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        val magnifierBorder = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.LTGRAY
        }

        val MAX_TOUCH_DIST_SQ = 18f.pow(2)
    }
}