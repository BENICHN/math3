package fr.benichn.math3.graphics.caret

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import fr.benichn.math3.types.callback.*
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.types.BoxInputCoord

class BoxCaret(val root: FormulaBox) {
    var position: CaretPosition = CaretPosition.None
        set(value) {
            val old = value
            field = value
            onPositionChanged(old, value)
        }

    val onPositionChanged = VCC<BoxCaret, CaretPosition>(this)

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