package fr.benichn.math3.graphics.boxes.types

import fr.benichn.math3.graphics.boxes.FormulaBox

data class InitialBoxes(val boxesBefore: List<FormulaBox>, val selectedBoxes: List<FormulaBox>, val boxesAfter: List<FormulaBox>) {
    val hasSelection
        get() = selectedBoxes.isNotEmpty()
}