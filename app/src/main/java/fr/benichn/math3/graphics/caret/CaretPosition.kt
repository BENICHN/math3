package fr.benichn.math3.graphics.caret

import android.graphics.PointF
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.SequenceFormulaBox
import fr.benichn.math3.graphics.boxes.types.Range

sealed class CaretPosition {
    data object None : CaretPosition()
    data class Single(val box: InputFormulaBox, val index: Int) : CaretPosition() {
        fun getAbsPosition(): PointF {
            val y = box.accTransform.origin.y
            val x = if (box.ch.isEmpty()) {
                assert(index == 0)
                box.accRealBounds.centerX()
            } else if (index == box.ch.size) {
                box.accRealBounds.right
            } else {
                box.ch[index].accRealBounds.left
            }
            return PointF(x, y)
        }
    }
    data class Selection(val box: SequenceFormulaBox, val indexRange: Range) : CaretPosition() {
        val selectedBoxes
            get() = box.ch.subList(indexRange.start, indexRange.end).toList()
        fun contains(b: FormulaBox): Boolean {
            val i = box.deepIndexOf(b)
            return indexRange.start <= i && i < indexRange.end
        }
    }
}