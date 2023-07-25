package fr.benichn.math3.graphics.boxes

import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.PaintedPath
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.boxes.types.Range
import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.caret.CaretPosition

class InputFormulaBox(vararg boxes: FormulaBox) : SequenceFormulaBox(*boxes) {
    val firstSingle
        get() = CaretPosition.Single(this, 0)
    val lastSingle
        get() = CaretPosition.Single(this, ch.size)

    init {
        updateGraphics()
    }

    override fun onChildRequiresDelete(b: FormulaBox, vararg anticipation: FormulaBox) =
        if (anticipation.isEmpty()) {
            val i = ch.indexOf(b)
            removeBoxAt(i)
            val s = CaretPosition.Single(this, i)
            DeletionResult(s, true)
        } else {
            DeletionResult.fromSelection(*anticipation)
        }

    override val isFilled: Boolean
        get() = ch.isNotEmpty()

    override fun clear() {
        removeAllBoxes()
        super.clear()
    }

    override fun shouldEnterInChild(c: FormulaBox, pos: PointF) =
        c.realBounds.run { pos.x in left..right }

    // override fun deleteMultiple(indices: List<Int>) = if (indices.size == 1) {
    //     val i = indices[0]
    //     removeBoxAt(i)
    //     DeletionResult(CaretPosition.Single(this, i), true)
    // } else DeletionResult()

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

    override fun generateGraphics() = if (ch.isEmpty()) {
        val rx = DEFAULT_TEXT_WIDTH * 0.25f
        val ry = DEFAULT_TEXT_RADIUS * 0.5f
        val bds = RectF(0f, -DEFAULT_TEXT_RADIUS, DEFAULT_TEXT_WIDTH, DEFAULT_TEXT_RADIUS)
        val path = Path()
        path.moveTo(bds.left+rx, bds.top)
        path.rLineTo(-rx, 0f)
        path.rLineTo(0f, ry)
        path.moveTo(bds.left+rx, bds.bottom)
        path.rLineTo(-rx, 0f)
        path.rLineTo(0f, -ry)
        path.moveTo(bds.right-rx, bds.top)
        path.rLineTo(rx, 0f)
        path.rLineTo(0f, ry)
        path.moveTo(bds.right-rx, bds.bottom)
        path.rLineTo(rx, 0f)
        path.rLineTo(0f, -ry)
        FormulaGraphics(
            PaintedPath(
                path,
                Paints.stroke(DEFAULT_LINE_WIDTH)),
            bounds = Padding(bds.right*0.2f, 0f).applyOnRect(bds)
        )
    } else super.generateGraphics()
}
