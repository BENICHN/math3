package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.types.callback.ValueChangedEvent

open class SequenceFormulaBox(vararg boxes: FormulaBox) : FormulaBox() {
    init {
        updateGraphics()
        addBoxes(*boxes)
    }

    override fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
        val j = ch.indexOf(b)
        offsetFrom(j, e.old.left - e.new.left)
        offsetFrom(j + 1, e.new.right - e.old.right)
        updateGraphics()
    }

    final override fun addBox(i: Int, b: FormulaBox) {
        super.addBox(i, b)
        setChildTransform(
            i,
            BoxTransform.xOffset((if (i == 0) 0f else ch[i - 1].run { transform.origin.x + bounds.right }) - b.bounds.left)
        )
        offsetFrom(i + 1, b.bounds.width())
        updateGraphics()
    }

    final override fun removeBoxAt(i: Int) {
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

    override fun findChildBox(pos: PointF): FormulaBox {
        for (c in ch) {
            if (pos.x <= c.realBounds.right) {
                return c
            }
        }
        return if (ch.isEmpty()) this else ch.last()
    }
}