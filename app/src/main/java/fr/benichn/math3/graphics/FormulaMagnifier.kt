package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Size
import android.util.SizeF
import android.view.View
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import androidx.core.graphics.times
import androidx.core.graphics.toRect
import androidx.core.graphics.withClip
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.types.callback.ObservableProperty

class FormulaMagnifier(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var box by ObservableProperty(this, FormulaBox()).apply {
        onChanged += { _, _ ->
            invalidate()
        }
    }

    var absPos by ObservableProperty(this, PointF()).apply {
        onChanged += { _, _ ->
            computeRealOrigin()
            invalidate()
        }
    }

    var origin by ObservableProperty(this, PointF()).apply {
        onChanged += { _, _ ->
            computeRealOrigin()
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeRealOrigin()
        super.onSizeChanged(w, h, oldw, oldh)
    }

    private var realOrigin = PointF()

    private fun computeRealOrigin() {
        val o = origin + absPos
        realOrigin = PopupView.getPopupPositionF(
            SizeF(width.toFloat(), height.toFloat()),
            SizeF(MAGNIFIER_W, MAGNIFIER_H),
            RectF(o.x, o.y - MAGNIFIER_DY, o.x, o.y + MAGNIFIER_DY),
            RectPoint.TOP_LEFT
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.translate(realOrigin.x, realOrigin.y)
        canvas.drawPath(magnifierPath, backgroundPaint)
        canvas.drawPath(magnifierPath, FormulaView.magnifierBorder)
        canvas.withClip(magnifierPath) {
            val ro = realOrigin + PointF(MAGNIFIER_W, MAGNIFIER_H) * 0.5f - absPos
            val o = ro-realOrigin
            canvas.translate(o.x, o.y)
            canvas.scale(FormulaBox.MAGNIFIER_FACTOR, FormulaBox.MAGNIFIER_FACTOR, absPos.x, absPos.y)
            box.drawOnCanvas(canvas)
        }
    }

    companion object {
        const val MAGNIFIER_DY = FormulaBox.DEFAULT_TEXT_RADIUS * 4
        const val MAGNIFIER_W = FormulaBox.DEFAULT_TEXT_WIDTH * 6
        const val MAGNIFIER_H = FormulaBox.DEFAULT_TEXT_SIZE * 1.5f
        val magnifierPath = Path().apply {
            val r = RectF(0f, 0f, MAGNIFIER_W, MAGNIFIER_H)
            addRoundRect(r, FormulaBox.MAGNIFIER_RADIUS, FormulaBox.MAGNIFIER_RADIUS, Path.Direction.CCW)
        }
        val backgroundPaint = Paints.fill(FormulaView.defaultBackgroundColor)
    }
}