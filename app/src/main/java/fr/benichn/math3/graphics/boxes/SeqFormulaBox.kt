package fr.benichn.math3.graphics.boxes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics

sealed class SeqFormulaBox(vararg boxes: FormulaBox) : FormulaBox() {
    init {
        updateGraphics()
        for (b in boxes) {
            addBox(b)
        }
    }

    override fun addBox(i: Int, b: FormulaBox) {
        super.addBox(i, b)
        setChildTransform(
            i,
            BoxTransform.xOffset((if (i == 0) 0f else ch[i - 1].run { transform.origin.x + bounds.right }) - b.bounds.left)
        )
        connect(b.onBoundsChanged) { s, e ->
            val j = b.indexInParent!!
            offsetFrom(j, e.old.left - e.new.left)
            offsetFrom(j + 1, e.new.right - e.old.right)
        }
        listenChildBoundsChange(i)
        offsetFrom(i + 1, b.bounds.width())
        updateGraphics()
    }
    override fun removeBoxAt(i: Int) {
        val b = ch[i]
        super.removeBoxAt(i)
        offsetFrom(i, -b.bounds.width())
        updateGraphics()
    }

    private fun offsetFrom(i: Int, l: Float) {
        for (j in i until ch.size) {
            modifyChildTransform(j) { it * BoxTransform.xOffset(l) }
        }
    }
}

class SequenceFormulaBox(vararg boxes: FormulaBox) : SeqFormulaBox(*boxes)
class MutableSequenceFormulaBox(vararg boxes: FormulaBox) : SeqFormulaBox(*boxes) {
    public override fun addBox(i: Int, b: FormulaBox) = super.addBox(i, b)
    public override fun addBox(b: FormulaBox) = super.addBox(b)
    public override fun removeBoxAt(i: Int) = super.removeBoxAt(i)
    public override fun removeBox(b: FormulaBox) = super.removeBox(b)
    public override fun removeLastBox() = super.removeLastBox()
}