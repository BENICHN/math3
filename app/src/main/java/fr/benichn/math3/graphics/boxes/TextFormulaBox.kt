package fr.benichn.math3.graphics.boxes

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

    override fun toWolfram(mode: Int) = when(text) {
        "ⅈ" -> "I"
        "ℼ" -> "Pi"
        "ⅇ" -> "E"
        else -> text
    }

    // override fun toSage() = when(text) {
    //     "×" -> "*"
    //     "ⅈ" -> "I"
    //     "ℼ" -> "pi"
    //     "ⅇ" -> "e"
    //     else -> text
    // }

    override fun toJson() = makeJsonObject("text") {
        addProperty("text", text)
        addProperty("big", big)
        addProperty("widthFactor", widthFactor)
    }
}

fun String.toBoxes() = map { c ->
    when (c) {
        '\n' -> SequenceFormulaBox.LineStart()
        else -> TextFormulaBox(
            c.toString()
        )
    }
}

fun FormulaBox.isNumber() = hasText { text ->
    text.isNotEmpty()
            && (text[0].isDigit() && run {
        var isDec = false
        text.substring(1).all { c ->
            if (c == '.') !isDec.also { isDec = true }
            else c.isDigit()
        }
    }
            || text[0] == '.' && text.length >= 2 && text.substring(1).all { c -> c.isDigit() })
}
fun FormulaBox.isVariable() = hasText { text ->
    text.isNotEmpty()
            && text[0].isLetter()
            && text.substring(1).all { c -> c.isLetterOrDigit() }
}
fun FormulaBox.isLetters() = hasText { text -> text.all { it.isLetter() } }

fun FormulaBox.hasText(predicate: (String) -> Boolean) = this is TextFormulaBox && predicate(text)
fun FormulaBox.hasChar(predicate: (Char) -> Boolean) = this is TextFormulaBox && text.length == 1 && predicate(text[0])