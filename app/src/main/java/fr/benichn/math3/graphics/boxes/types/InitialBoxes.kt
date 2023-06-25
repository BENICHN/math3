package fr.benichn.math3.graphics.boxes.types

import fr.benichn.math3.graphics.boxes.FormulaBox

sealed class InitialBoxes {
    data class BeforeAfter(val boxesBefore: List<FormulaBox>, val boxesAfter: List<FormulaBox>) : InitialBoxes()
    data class Selection(val boxes: List<FormulaBox>) : InitialBoxes()
}