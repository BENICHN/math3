package fr.benichn.math3.graphics.boxes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.types.Orientation
import fr.benichn.math3.graphics.boxes.types.RangeF

class LineFormulaBox(orientation: Orientation = Orientation.V,
                     range: RangeF = RangeF(-DEFAULT_TEXT_RADIUS,DEFAULT_TEXT_RADIUS)
) : FormulaBox() {
    val dlgOrientation = BoxProperty(this, orientation)
    var orientation by dlgOrientation

    val dlgRange = BoxProperty(this, range)
    var range by dlgRange

    init {
        paint.color = Color.WHITE
        paint.strokeWidth = DEFAULT_LINE_WIDTH
        paint.style = Paint.Style.STROKE
        updateGraphics()
    }

    override fun generateGraphics(): FormulaGraphics = when (orientation) {
        Orientation.H -> {
            FormulaGraphics(
                Path().apply {
                    moveTo(range.start, 0f)
                    lineTo(range.end, 0f)
                },
                paint,
                RectF(range.start, 0f, range.end, 0f)
            )
        }

        Orientation.V -> {
            FormulaGraphics(
                Path().apply {
                    moveTo(0f, range.start)
                    lineTo(0f, range.end)
                },
                paint,
                RectF(0f, range.start, 0f, range.end)
            )
        }
    }
}