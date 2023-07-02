package fr.benichn.math3.graphics.boxes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.Utils
import fr.benichn.math3.graphics.boxes.types.PathPainting

class TextFormulaBox(text: String = "") : FormulaBox() {
    val dlgText = BoxProperty(this, text)
    var text by dlgText

    init {
        updateGraphics()
    }

    override fun generateGraphics(): FormulaGraphics {
        val (p, w, h) = Utils.getTextPathAndSize(DEFAULT_TEXT_SIZE, text)
        val bounds = RectF(0f, -h * 0.5f, w, h * 0.5f)
        return FormulaGraphics(p, PathPainting.Fill, bounds)
    }
}
