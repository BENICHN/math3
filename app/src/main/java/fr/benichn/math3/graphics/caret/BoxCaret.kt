package fr.benichn.math3.graphics.caret

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import fr.benichn.math3.types.callback.*
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.types.BoxInputCoord

class BoxCaret(val root: FormulaBox) {
    var position: BoxInputCoord? = null
        set(value) {
            field = value
            onPictureChanged(Unit)
        }

    val onPictureChanged = Callback<BoxCaret, Unit>(this)
    fun drawOnCanvas(canvas: Canvas) {
        if (root.selectedChildren.isEmpty()) {
            position?.also {
                val p = it.getAbsPosition()
                canvas.drawLine(
                    p.x,
                    p.y - FormulaBox.DEFAULT_TEXT_RADIUS,
                    p.x,
                    p.y + FormulaBox.DEFAULT_TEXT_RADIUS,
                    caretPaint
                )
            }
        }
    }

    companion object {
        val caretPaint = Paint().also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = 6f
            it.color = Color.rgb(255, 255, 0)
        }
        val selectionPaint = Paint().also {
            it.style = Paint.Style.FILL
            it.color = Color.rgb(100, 100, 0) }
    }
}