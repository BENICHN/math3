package fr.benichn.math3.graphics.boxes.types

import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.caret.noneIfNull

data class DeletionResult(val deletedSingle: CaretPosition.Single? = null, val newPos: CaretPosition = CaretPosition.None, val finalBoxes: FinalBoxes = FinalBoxes()) {
    fun withFinalBoxes(fb: FinalBoxes) = DeletionResult(deletedSingle, newPos, fb)
    fun withFinalBoxes(boxesBefore: List<FormulaBox> = emptyList(), boxesAfter: List<FormulaBox> = emptyList(), selectBoxesBefore: Boolean = true, selectBoxesAfter: Boolean = false) =
        withFinalBoxes(
            FinalBoxes(
                boxesBefore.toList(),
                boxesAfter.toList(),
                selectBoxesBefore,
                selectBoxesAfter
            )
        )

    companion object {
        fun fromDouble(b: FormulaBox) = DeletionResult(null, CaretPosition.Double.fromBox(b).noneIfNull())
        fun fromSelection(b: FormulaBox) = DeletionResult(null, b.parentWithIndex?.let { (p, i) -> CaretPosition.DiscreteSelection(p, listOf(i)) }.noneIfNull())
    }
}