package fr.benichn.math3.graphics.boxes

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.PathPainting
import fr.benichn.math3.graphics.boxes.types.Range
import fr.benichn.math3.graphics.caret.CaretPosition

class InputFormulaBox(vararg boxes: FormulaBox) : SequenceFormulaBox(*boxes) {
    val firstSingle
        get() = CaretPosition.Single(this, 0)
    val lastSingle
        get() = CaretPosition.Single(this, ch.size)

    override fun onChildRequiresDelete(b: FormulaBox): DeletionResult {
        val i = ch.indexOf(b)
        removeBoxAt(i)
        val s = CaretPosition.Single(this, i)
        return DeletionResult(s, true)
    }

    override fun shouldEnterInChild(c: FormulaBox, pos: PointF) =
        c.realBounds.run { pos.x in left..right }

    override fun deleteMultiple(indices: List<Int>) = if (indices.size == 1) {
        val i = indices[0]
        removeBoxAt(i)
        DeletionResult(CaretPosition.Single(this, i), true)
    } else DeletionResult()

    fun addFinalBoxes(i: Int, fb: FinalBoxes) : CaretPosition {
        for ((j, b) in fb.boxesBefore.union(fb.boxesAfter).withIndex()) {
            addBox(i+j, b)
        }
        return if (!fb.selectBoxesAfter && !fb.selectBoxesBefore) {
            CaretPosition.Single(this, i + fb.boxesBefore.size)
        } else {
            CaretPosition.Double(this, Range(
                if (fb.selectBoxesBefore) i else i + fb.boxesBefore.size,
                if (fb.selectBoxesAfter) i + fb.boxesBefore.size + fb.boxesAfter.size else i + fb.boxesBefore.size))
        }
    }

    override fun generateGraphics(): FormulaGraphics =
        if (ch.isEmpty()) {
            val rh = DEFAULT_TEXT_RADIUS
            val w = DEFAULT_TEXT_WIDTH
            val path = Path()
            path.moveTo(0f, rh*0.5f)
            path.lineTo(0f, rh)
            path.lineTo(w*0.25f, rh)
            path.moveTo(0f, -rh*0.5f)
            path.lineTo(0f, -rh)
            path.lineTo(w*0.25f, -rh)
            path.moveTo(w, rh*0.5f)
            path.lineTo(w, rh)
            path.lineTo(w*0.75f, rh)
            path.moveTo(w, -rh*0.5f)
            path.lineTo(w, -rh)
            path.lineTo(w*0.75f, -rh)
            val bounds = RectF(0f, -rh, w, rh)
            FormulaGraphics(
                path,
                PathPainting.Stroke(DEFAULT_LINE_WIDTH),
                Padding(w*0.2f, 0f).applyOnRect(bounds)
            )
        } else {
            super.generateGraphics()
        }
}
