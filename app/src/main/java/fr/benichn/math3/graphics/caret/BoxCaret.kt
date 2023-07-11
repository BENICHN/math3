package fr.benichn.math3.graphics.caret

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import androidx.core.graphics.minus
import fr.benichn.math3.graphics.Utils.Companion.corners
import fr.benichn.math3.graphics.Utils.Companion.l2
import fr.benichn.math3.types.callback.*
import fr.benichn.math3.graphics.boxes.FormulaBox
import kotlin.math.pow

class BoxCaret(/* val root: FormulaBox */) {
    private val dlgPosition = ObservableProperty<BoxCaret, CaretPosition>(this, CaretPosition.None) { _, _, -> notifyPictureChanged() }
    var position by dlgPosition
    val onPositionChanged = dlgPosition.onChanged

    private val dlgAbsolutePosition = ObservableProperty<BoxCaret, PointF?>(this, null) { _, _, -> notifyPictureChanged() }
    var absolutePosition by dlgAbsolutePosition
    val onAbsolutePositionChanged = dlgPosition.onChanged

    var fixedAbsPos: PointF? = null

    private val notifyPictureChanged = Callback<BoxCaret, Unit>(this)
    val onPictureChanged = notifyPictureChanged.Listener()

    fun preDrawOnCanvas(canvas: Canvas) {
        when (val p = position) {
            is CaretPosition.Double -> {
                canvas.drawRect(p.bounds, selectionPaint)
            }
            is CaretPosition.GridSelection -> {
                canvas.drawRect(p.bounds, selectionPaint)
            }
            is CaretPosition.DiscreteSelection -> {
                p.bounds.forEach { r -> canvas.drawRect(r, selectionPaint) }
            }
            else -> {}
        }
    }

    fun postDrawOnCanvas(canvas: Canvas) {
        val p = position

        fun drawBar(pos: PointF, radius: Float, transparent: Boolean) {
            canvas.drawLine(
                pos.x,
                pos.y-radius,
                pos.x,
                pos.y+radius,
                if (transparent) caretPaintTrans else caretPaint
            )
        }

        fun drawBall(pos: PointF) {
            canvas.drawCircle(pos.x, pos.y, BALL_RADIUS, ballPaint)
        }

        when (p) {
            is CaretPosition.Single -> {
                val pos = p.getAbsPosition()
                drawBar(
                    pos,
                    p.radius,
                    absolutePosition != null
                )
            }
            is CaretPosition.Double -> {
                val r = p.bounds
                fun drawSelectionEnding(x: Float) {
                    canvas.drawLine(x, r.top, x, r.bottom, caretPaint)
                }
                fixedAbsPos?.also {
                    val ap = absolutePosition!!
                    val x = if (ap.x > it.x) r.left else r.right
                    drawSelectionEnding(x)
                } ?: run {
                    drawSelectionEnding(r.left)
                    drawSelectionEnding(r.right)
                }
            }
            is CaretPosition.GridSelection -> {
                val r = p.bounds
                fixedAbsPos?.also {
                    val cs = corners(r)
                    val mc = cs.maxBy { c -> l2(c - it) }
                    cs.filter { c -> c != mc }.forEach { c -> drawBall(c) }
                } ?: run {
                    for (c in corners(r)) {
                        drawBall(c)
                    }
                }
            }
            else -> { }
        }
        absolutePosition?.also { ap ->
            when (p) {
                is CaretPosition.Single, is CaretPosition.None -> {
                    drawBar(
                        ap,
                        (p as? CaretPosition.Single)?.radius ?: FormulaBox.DEFAULT_TEXT_RADIUS,
                        false)
                }
                is CaretPosition.Double -> {
                    drawBar(
                        ap,
                        p.bounds.height() * 0.5f,
                        false
                    )
                }
                is CaretPosition.DiscreteSelection -> { }
                is CaretPosition.GridSelection -> {
                    drawBall(ap)
                }
            }
        }
    }

    companion object {
        val ballPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.rgb(255, 255, 0)
        }
        val caretPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.rgb(255, 255, 0)
        }
        val caretPaintTrans = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.argb(127, 255, 255, 0)
        }
        val selectionPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.rgb(100, 100, 0) }

        const val BALL_RADIUS = FormulaBox.DEFAULT_LINE_WIDTH * 3
        val SINGLE_MAX_TOUCH_DIST_SQ = (FormulaBox.DEFAULT_TEXT_WIDTH * 0.5f).pow(2)
        val SELECTION_MAX_TOUCH_DIST_SQ = (FormulaBox.DEFAULT_TEXT_WIDTH * 0.25f).pow(2)
        val BALL_MAX_TOUCH_DIST_SQ = SELECTION_MAX_TOUCH_DIST_SQ * 4
    }
}