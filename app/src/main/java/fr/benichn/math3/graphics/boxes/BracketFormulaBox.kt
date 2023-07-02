package fr.benichn.math3.graphics.boxes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.PathPainting
import fr.benichn.math3.graphics.boxes.types.RangeF

class BracketFormulaBox(range: RangeF = RangeF(-DEFAULT_TEXT_RADIUS,DEFAULT_TEXT_RADIUS)) : FormulaBox() {
    val dlgRange = BoxProperty(this, range)
    var range by dlgRange

    init {
        updateGraphics()
    }

    override fun generateGraphics(): FormulaGraphics {
        val l1 = range.start + DEFAULT_TEXT_RADIUS
        val l2 = range.end - DEFAULT_TEXT_RADIUS
        val r = DEFAULT_TEXT_RADIUS * 0.9f
        val path = Path()
        path.moveTo(0f,l2)
        path.lineTo(0f,l1)
        path.rCubicTo(0f, 0f, 0f,-0.75f * r, 0.5f* r, -r)
        path.moveTo(0f,l2)
        path.rCubicTo(0f, 0f, 0f,0.75f * r, 0.5f* r, r)
        val bounds = RectF(-0.25f * DEFAULT_TEXT_RADIUS, range.start, 0.5f * DEFAULT_TEXT_RADIUS, range.end)
        return FormulaGraphics(
            path,
            PathPainting.Stroke(DEFAULT_LINE_WIDTH),
            bounds
        )
    }
}