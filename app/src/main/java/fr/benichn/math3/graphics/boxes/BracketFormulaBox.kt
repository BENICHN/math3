package fr.benichn.math3.graphics.boxes

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.PathPainting
import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.types.Side

class BracketFormulaBox(range: RangeF = RangeF(-DEFAULT_TEXT_RADIUS,DEFAULT_TEXT_RADIUS), type: Type = Type.BRACE, side: Side = Side.L) : FormulaBox() {
    val dlgRange = BoxProperty(this, range)
    var range by dlgRange

    val dlgType = BoxProperty(this, type)
    var type by dlgType

    val dlgSide = BoxProperty(this, side)
    var side by dlgSide

    enum class Type {
        BRACE,
        BAR,
        BRACKET,
        FLOOR,
        CEIL,
        CURLY,
        CHEVRON
    }

    init {
        updateGraphics()
    }

    override fun generateGraphics(): FormulaGraphics {
        val l1 = range.start + DEFAULT_TEXT_RADIUS
        val l2 = range.end - DEFAULT_TEXT_RADIUS
        val r = DEFAULT_TEXT_RADIUS * 0.9f
        val path = Path()
        when (type) {
            Type.BRACE -> {
                path.moveTo(0f,l2)
                path.lineTo(0f,l1)
                path.rCubicTo(0f, 0f, 0f,-0.75f * r, 0.5f* r, -r)
                path.moveTo(0f,l2)
                path.rCubicTo(0f, 0f, 0f,0.75f * r, 0.5f* r, r)
            }
            Type.BAR -> {
                path.moveTo(0.25f * r, range.start)
                path.lineTo(0.25f * r, range.end)
            }
            Type.BRACKET -> {
                path.moveTo(0.65f * r, range.start)
                path.rLineTo(-0.5f * r, 0f)
                path.lineTo(0.15f * r, range.end)
                path.rLineTo(0.5f* r, 0f)
            }
            Type.FLOOR -> {
                path.moveTo(0.15f * r, range.start)
                path.lineTo(0.15f * r, range.end)
                path.rLineTo(0.5f* r, 0f)
            }
            Type.CEIL -> {
                path.moveTo(0.65f * r, range.start)
                path.rLineTo(-0.5f * r, 0f)
                path.lineTo(0.15f * r, range.end)
            }
            Type.CURLY -> {

            }
            Type.CHEVRON -> {
                path.moveTo(0.5f*r, range.start)
                path.lineTo(0f, range.center)
                path.lineTo(0.5f*r, range.end)
            }
        }
        if (side == Side.R) {
            path.transform(Matrix().apply {
                postScale(-1f, 1f)
                postTranslate(0.25f* r, 0f)
            })
        }
        val bounds = RectF(-0.25f * DEFAULT_TEXT_RADIUS, range.start, 0.5f * DEFAULT_TEXT_RADIUS, range.end)
        return FormulaGraphics(
            path,
            PathPainting.Stroke(DEFAULT_LINE_WIDTH),
            bounds
        )
    }
}