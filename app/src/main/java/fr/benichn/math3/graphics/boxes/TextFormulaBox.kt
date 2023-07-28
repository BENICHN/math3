package fr.benichn.math3.graphics.boxes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import fr.benichn.math3.R
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.Utils
import fr.benichn.math3.graphics.boxes.types.PaintedPath
import fr.benichn.math3.graphics.boxes.types.Paints

class TextFormulaBox(text: String = "", big: Boolean = false, widthFactor: Float = 1f) : FormulaBox() {
    val dlgText = BoxProperty(this, text)
    var text by dlgText

    val dlgBig = BoxProperty(this, big)
    var big by dlgBig

    val dlgWidthFactor = BoxProperty(this, widthFactor)
    var widthFactor by dlgWidthFactor

    init {
        updateGraphics()
    }

    override fun generateGraphics(): FormulaGraphics {
        val (p, w, h) = if (big) {
            Utils.getTextPathAndSize(
                text,
                R.font.iosevka_fixed_thin_extended,
                DEFAULT_TEXT_SIZE * 2,
                widthFactor
            )
        }
        else {
                Utils.getTextPathAndSize(
                    text,
                    R.font.iosevka_fixed_extralight_extended,
                    DEFAULT_TEXT_SIZE,
                    widthFactor
                )
            }
        val bounds = RectF(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f)
        return FormulaGraphics(
            PaintedPath(
                p,
                Paints.fill()),
            bounds = bounds)
    }

    override fun toSage() = when(text) {
        "×" -> "*"
        "ⅈ" -> "I"
        "ℼ" -> "pi"
        "ⅇ" -> "e"
        else -> text
    }
}

fun FormulaBox.isDigit() = this is TextFormulaBox && text.all { c -> c.isDigit() || c == '.' }