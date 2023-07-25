package fr.benichn.math3.graphics.boxes.types

import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.types.Side

data class DeletionResult(val newPos: CaretPosition? = null, val deleted: Boolean = false, val finalBoxesOwner: FormulaBox? = null) {
    val finalBoxes = finalBoxesOwner?.run { getFinalBoxes() } ?: FinalBoxes()
    fun withFinalBoxes(owner: FormulaBox?) = DeletionResult(newPos, deleted , owner)

    companion object {
        fun fromSingle(b: FormulaBox) = DeletionResult(CaretPosition.Single.fromBox(b, Side.R))
        fun fromDouble(b: FormulaBox) = DeletionResult(CaretPosition.Double.fromBox(b))
        fun fromSelection(b: FormulaBox) = DeletionResult(CaretPosition.DiscreteSelection.fromBox(b))
        fun fromSelection(vararg boxes: FormulaBox) = DeletionResult(CaretPosition.DiscreteSelection.fromBoxes(*boxes))
    }
}