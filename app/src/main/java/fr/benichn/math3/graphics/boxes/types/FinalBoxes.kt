package fr.benichn.math3.graphics.boxes.types

import fr.benichn.math3.graphics.boxes.FormulaBox

data class FinalBoxes(val boxesBefore: List<FormulaBox> = emptyList(), val boxesAfter: List<FormulaBox> = emptyList(), val selectBoxesBefore: Boolean = true, val selectBoxesAfter: Boolean = false) {
    val isEmpty
        get() = boxesBefore.isEmpty() && boxesAfter.isEmpty()
}