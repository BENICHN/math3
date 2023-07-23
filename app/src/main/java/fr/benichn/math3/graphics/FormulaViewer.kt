package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.types.RectPoint

class FormulaViewer(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var box = TransformerFormulaBox(transformer = BoundsTransformer.Align(RectPoint.CENTER)).also {
        it.onBoundsChanged += { _, e ->
            val r = e.new
            layout(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
        }
        it.onPictureChanged += { _, _ ->
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        box.drawOnCanvas(canvas)
    }
}