package fr.benichn.math3.graphics.caret

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.minus
import fr.benichn.math3.graphics.Utils.corners
import fr.benichn.math3.graphics.Utils.l2
import fr.benichn.math3.graphics.Utils.leftBar
import fr.benichn.math3.graphics.Utils.rightBar
import fr.benichn.math3.types.callback.*
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.Paints

class BoxCaret {
    private val dlgPositions = ObservableProperty<BoxCaret, List<CaretPosition>>(this, listOf()) { _, _, -> notifyPictureChanged() }
    var positions by dlgPositions
    val onPositionsChanged = dlgPositions.onChanged

    private val notifyPictureChanged = Callback<BoxCaret, Unit>(this)
    val onPictureChanged = notifyPictureChanged.Listener()

    fun preDrawOnCanvas(canvas: Canvas) {
        for (p in positions) {
            when (p) {
                is CaretPosition.Double -> {
                    p.rects.forEach { r -> canvas.drawRect(r, selectionPaint) }
                }
                is CaretPosition.GridSelection -> {
                    canvas.drawRect(p.bounds, selectionPaint)
                }
                is CaretPosition.DiscreteSelection -> {
                    p.rects.forEach { r -> canvas.drawRect(r, selectionPaint) }
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
        fun drawBar(bar: RectF, transparent: Boolean) {
            canvas.drawLine(
                bar.left,
                bar.top,
                bar.left,
                bar.bottom,
                if (transparent) caretPaintTrans else caretPaint
            )
        }
        fun drawBall(pos: PointF, transparent: Boolean) {
            canvas.drawCircle(pos.x, pos.y, BALL_RADIUS, if (transparent) ballPaintTrans else ballPaint)
        }

        for (p in positions) {
            when (p) {
                is CaretPosition.Single -> {
                    drawBar(p.barRect, p.absPos != null)
                    p.absPos?.let { ap ->
                        drawBar(
                            ap,
                            p.barRect.height() * 0.5f,
                            false
                        )
                    }
                }
                is CaretPosition.Double -> {
                    p.rects.let { rs ->
                        if (rs.isNotEmpty()) {
                            val leftBar = rs.first().leftBar()
                            val rightBar = rs.last().rightBar()
                            drawBar(leftBar, p.absPos != null)
                            drawBar(rightBar, p.absPos != null)
                            p.absPos?.let { ap ->
                                drawBar(
                                    ap,
                                    rightBar.height() * 0.5f,
                                    false
                                )
                            }
                        } else {
                            p.absPos?.let { ap ->
                                drawBar(
                                    ap,
                                    FormulaBox.DEFAULT_TEXT_RADIUS,
                                    false
                                )
                            }
                        }
                    }
                }
                is CaretPosition.GridSelection -> {
                    val r = p.bounds
                    for (c in corners(r)) {
                        drawBall(c, p.absPos != null)
                    }
                    p.absPos?.let { ap ->
                        drawBall(ap, false)
                    }
                }
                else -> { }
            }
        }
    }

    companion object {
        val ballPaint = Paints.fill(Color.rgb(255, 255, 0))
        val ballPaintTrans = Paints.fill(Color.argb(127, 255, 255, 0))
        val caretPaint = Paints.stroke(FormulaBox.DEFAULT_LINE_WIDTH + 2f, Color.rgb(255, 255, 0))
        val caretPaintTrans = Paints.stroke(FormulaBox.DEFAULT_LINE_WIDTH + 2f, Color.argb(127, 255, 255, 0))
        val selectionPaint = Paints.fill(Color.rgb(100, 100, 0))

        const val BALL_RADIUS = FormulaBox.DEFAULT_LINE_WIDTH * 3
        const val SINGLE_MAX_TOUCH_DIST = FormulaBox.DEFAULT_TEXT_WIDTH * 0.5f
        const val SINGLE_MAX_TOUCH_DIST_SQ = SINGLE_MAX_TOUCH_DIST * SINGLE_MAX_TOUCH_DIST
        val singleBarPadding = Padding(SINGLE_MAX_TOUCH_DIST)
        const val SELECTION_MAX_TOUCH_DIST = FormulaBox.DEFAULT_TEXT_WIDTH * 0.25f
        val selectionBarPadding = Padding(SELECTION_MAX_TOUCH_DIST)
        const val BALL_MAX_TOUCH_DIST_SQ = SELECTION_MAX_TOUCH_DIST * SELECTION_MAX_TOUCH_DIST * 4
    }
}