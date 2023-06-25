package fr.benichn.math3.graphics.caret

import fr.benichn.math3.graphics.boxes.types.BoxInputCoord

sealed class CaretPosition {
    data object None : CaretPosition()
    data class Single(val ic: BoxInputCoord) : CaretPosition()
}