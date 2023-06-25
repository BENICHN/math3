package fr.benichn.math3.graphics.boxes.types

import android.graphics.PointF
import fr.benichn.math3.graphics.boxes.InputFormulaBox

data class BoxInputCoord(val box: InputFormulaBox, val index: Int) {
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