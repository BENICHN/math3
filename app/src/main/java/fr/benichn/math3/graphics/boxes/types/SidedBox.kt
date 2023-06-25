package fr.benichn.math3.graphics.boxes.types

import fr.benichn.math3.graphics.types.Side
import fr.benichn.math3.graphics.boxes.FormulaBox

data class SidedBox(val box: FormulaBox, val side: Side) {
    fun toInputCoord() : BoxInputCoord? = FormulaBox.getBoxInputCoord(this)
}