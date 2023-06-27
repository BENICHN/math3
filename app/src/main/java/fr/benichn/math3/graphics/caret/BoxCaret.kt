package fr.benichn.math3.graphics.caret

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import fr.benichn.math3.types.callback.*
import fr.benichn.math3.graphics.boxes.FormulaBox

class BoxCaret(/* val root: FormulaBox */) {
    private val dlgPosition = ObservableProperty<BoxCaret, CaretPosition>(this, CaretPosition.None) { _, _, -> notifyPictureChanged() }
    var position by dlgPosition
    val onPositionChanged = dlgPosition.onChanged

    private val dlgAbsolutePosition = ObservableProperty<BoxCaret, PointF?>(this, null) { _, _, -> notifyPictureChanged() }
    var absolutePosition by dlgAbsolutePosition
    val onAbsolutePositionChanged = dlgPosition.onChanged

    private val notifyPictureChanged = Callback<BoxCaret, Unit>(this)
    val onPictureChanged = notifyPictureChanged.Listener()

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
        val caretPaintTrans = Paint().also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = 6f
            it.color = Color.argb(127, 255, 255, 0)
        }
        val selectionPaint = Paint().also {
            it.style = Paint.Style.FILL
            it.color = Color.rgb(100, 100, 0) }

        fun drawCaretAtPos(canvas: Canvas, pos: PointF, trans: Boolean = false) {
            canvas.drawLine(
                pos.x,
                pos.y - FormulaBox.DEFAULT_TEXT_RADIUS,
                pos.x,
                pos.y + FormulaBox.DEFAULT_TEXT_RADIUS,
                if (trans) caretPaintTrans else caretPaint
            )
        }
    }
}