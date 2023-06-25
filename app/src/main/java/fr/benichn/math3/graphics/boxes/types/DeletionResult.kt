package fr.benichn.math3.graphics.boxes.types

import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.caret.CaretPosition

data class DeletionResult(val newPos: CaretPosition = CaretPosition.None, val finalBoxes: FinalBoxes = FinalBoxes()) {
    fun withFinalBoxes(fb: FinalBoxes) = DeletionResult(newPos, fb)
    fun withFinalBoxes(boxesBefore: List<FormulaBox> = emptyList(), boxesAfter: List<FormulaBox> = emptyList(), selectBoxesBefore: Boolean = true, selectBoxesAfter: Boolean = false) =
        withFinalBoxes(
            FinalBoxes(
                boxesBefore.toList(),
                boxesAfter.toList(),
                selectBoxesBefore,
                selectBoxesAfter
            )
        )
}