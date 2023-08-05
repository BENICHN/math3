package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.plus
import androidx.core.graphics.withClip
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.types.callback.ObservableProperty

class FormulaMagnifier(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var box by ObservableProperty(this, FormulaBox()).apply {
        onChanged += { _, _ ->
            invalidate()
        }
    }

    var absPos by ObservableProperty(this, PointF()).apply {
        onChanged += { _, _ ->
            invalidate()
        }
    }

    var origin by ObservableProperty(this, PointF()).apply {
        onChanged += { _, _ ->
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.translate((origin+absPos).x, (origin+absPos).y- FormulaBox.DEFAULT_TEXT_RADIUS *4)
        canvas.drawPath(magnifierPath, FormulaView.backgroundPaint)
        canvas.drawPath(magnifierPath, FormulaView.magnifierBorder)
        canvas.withClip(magnifierPath) {
            canvas.translate(-absPos.x, -absPos.y)
            canvas.scale(FormulaBox.MAGNIFIER_FACTOR, FormulaBox.MAGNIFIER_FACTOR, absPos.x, absPos.y)
            box.drawOnCanvas(canvas)
        }
    }

    companion object {
        val magnifierPath = Path().apply {
            val rx = FormulaBox.DEFAULT_TEXT_WIDTH * 3
            val ry = FormulaBox.DEFAULT_TEXT_SIZE * 0.75f
            val r = RectF(-rx, -ry, rx, ry)
            addRoundRect(r, FormulaBox.MAGNIFIER_RADIUS, FormulaBox.MAGNIFIER_RADIUS, Path.Direction.CCW)
        }
    }
}