package fr.benichn.math3.graphics.caret

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import fr.benichn.math3.types.callback.*
import fr.benichn.math3.graphics.boxes.FormulaBox

class BoxCaret(/* val root: FormulaBox */) {
    var position: CaretPosition = CaretPosition.None
        set(value) {
            val old = field
            field = value
            notifyPositionChanged(old, value)
        }

    private val notifyPositionChanged = VCC<BoxCaret, CaretPosition>(this)
    val onPositionChanged = notifyPositionChanged.Listener()

    // fun select(b: FormulaBox) {
    //     val s = FormulaBox.getSelectionFromBox(b)
    //     val newPos = when (val p = position) {
    //         is CaretPosition.Selection -> {
    //             s?.let {
    //                 FormulaBox.mergeSelections(p, it)
    //             }
    //         }
    //         else -> {
    //             s
    //         }
    //     }  ?: CaretPosition.None
    //     position = newPos
    // }

    // fun unSelect(b: FormulaBox) {
    //
    // }

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