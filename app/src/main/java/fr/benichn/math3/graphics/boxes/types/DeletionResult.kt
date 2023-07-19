package fr.benichn.math3.graphics.boxes.types

import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.types.Side

data class DeletionResult(val newPos: CaretPosition? = null, val deleted: Boolean = false, val finalBoxes: FinalBoxes = FinalBoxes()) {
    fun withFinalBoxes(fb: FinalBoxes) = DeletionResult(newPos, deleted , fb)
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
        fun fromSingle(b: FormulaBox) = DeletionResult(CaretPosition.Single.fromBox(b, Side.R))
        fun fromDouble(b: FormulaBox) = DeletionResult(CaretPosition.Double.fromBox(b))
        fun fromSelection(b: FormulaBox) = DeletionResult(CaretPosition.DiscreteSelection.fromBox(b))
        fun fromSelection(vararg boxes: FormulaBox) = DeletionResult(CaretPosition.DiscreteSelection.fromBoxes(*boxes))
    }
}