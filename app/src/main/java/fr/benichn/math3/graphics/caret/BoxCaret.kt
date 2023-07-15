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
    private val dlgPositions = ObservableProperty<BoxCaret, List<CaretPosition>>(this, listOf()) { _, _, -> notifyPictureChanged() }
    var positions by dlgPositions
    val onPositionsChanged = dlgPositions.onChanged

    val uniquePosition
        get() = if (positions.size == 1) positions[0] else null
    // var movingPosition: Int? = null

    private val notifyPictureChanged = Callback<BoxCaret, Unit>(this)
    val onPictureChanged = notifyPictureChanged.Listener()

    fun preDrawOnCanvas(canvas: Canvas) {
        for (p in positions) {
            when (p) {
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
    }

    fun postDrawOnCanvas(canvas: Canvas) {
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

        for ((i, p) in positions.withIndex()) {
            when (p) {
                is CaretPosition.Single -> {
                    val pos = p.getAbsPosition()
                    drawBar(
                        pos,
                        p.radius,
                        p.absPos != null
                    )
                }
                is CaretPosition.Double -> {
                    val r = p.bounds
                    fun drawSelectionEnding(x: Float) {
                        canvas.drawLine(x, r.top, x, r.bottom, caretPaint)
                    }
                    p.absPos?.let { ap -> p.fixedAbsPos?.also { fp ->
                        val x = if (ap.x > fp.x) r.left else r.right
                        drawSelectionEnding(x)
                    } } ?: run {
                        drawSelectionEnding(r.left)
                        drawSelectionEnding(r.right)
                    }
                }
                is CaretPosition.GridSelection -> {
                    val r = p.bounds
                    if (p.fixedAbsPos != null) {
                        val cs = corners(r)
                        val mc = cs.maxBy { c -> l2(c - p.fixedAbsPos) }
                        cs.filter { c -> c != mc }.forEach { c -> drawBall(c) }
                    } else {
                        for (c in corners(r)) {
                            drawBall(c)
                        }
                    }
                }
                else -> { }
            }
            p.absPos?.let { ap ->
                when (p) {
                    is CaretPosition.Single -> {
                        drawBar(
                            ap,
                            p.radius,
                            false
                        )
                    }

                    is CaretPosition.Double -> {
                        drawBar(
                            ap,
                            p.bounds.height() * 0.5f,
                            false
                        )
                    }

                    is CaretPosition.GridSelection -> {
                        drawBall(ap)
                    }

                    else -> {}
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
            strokeWidth = FormulaBox.DEFAULT_LINE_WIDTH + 2f
            color = Color.rgb(255, 255, 0)
        }
        val caretPaintTrans = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = FormulaBox.DEFAULT_LINE_WIDTH + 2f
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