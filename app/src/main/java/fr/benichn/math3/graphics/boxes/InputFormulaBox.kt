package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.boxes.types.BoxInputCoord
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.types.Side
import fr.benichn.math3.graphics.boxes.types.SidedBox

class InputFormulaBox(vararg boxes: FormulaBox) : SequenceFormulaBox(*boxes) {
    public override fun addBox(i: Int, b: FormulaBox) = super.addBox(i, b)
    public override fun addBox(b: FormulaBox) = super.addBox(b)
    public override fun removeBoxAt(i: Int) = super.removeBoxAt(i)
    public override fun removeBox(b: FormulaBox) = super.removeBox(b)
    public override fun removeLastBox() = super.removeLastBox()

    override fun findChildBox(absX: Float, absY: Float): FormulaBox {
        for (c in ch) {
            if (absX < c.accRealBounds.right) {
                return c
            }
        }
        return if (ch.isEmpty()) this else ch.last()
    }

    override fun onChildRequiresDelete(b: FormulaBox): DeletionResult {
        val i = ch.indexOf(b)
        removeBoxAt(i)
        return DeletionResult(BoxInputCoord(this, i))
    }

    override fun getInitialCaretPos(): SidedBox {
        return if (ch.isEmpty()) SidedBox(this, Side.R) else SidedBox(ch.last(), Side.R)
    }

    fun addFinalBoxes(i: Int, fb: FinalBoxes) : Int {
        var j = i
        for (b in fb.boxesBefore) {
            addBox(j, b)
            j++
            if (fb.selectBoxesBefore) {
                b.isSelected = true
            }
        }
        for (b in fb.boxesAfter) {
            addBox(j, b)
            j++
            if (fb.selectBoxesAfter) {
                b.isSelected = true
            }
        }
        return i + fb.boxesBefore.size
    }
}
