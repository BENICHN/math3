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

    var fixedX: Float? = null

    private val notifyPictureChanged = Callback<BoxCaret, Unit>(this)
    val onPictureChanged = notifyPictureChanged.Listener()

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

        fun drawCaretAtPos(canvas: Canvas, pos: PointF, trans: Boolean = false, height: Float = FormulaBox.DEFAULT_TEXT_RADIUS) {
            canvas.drawLine(
                pos.x,
                pos.y - height,
                pos.x,
                pos.y + height,
                if (trans) caretPaintTrans else caretPaint
            )
        }
    }
}