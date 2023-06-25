package fr.benichn.math3.graphics.boxes.types

import fr.benichn.math3.graphics.types.Side
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.caret.CaretPosition

data class SidedBox(val box: FormulaBox, val side: Side) {
    fun toCaretPosition() = FormulaBox.getCaretPositionFromSidedBox(this)
}