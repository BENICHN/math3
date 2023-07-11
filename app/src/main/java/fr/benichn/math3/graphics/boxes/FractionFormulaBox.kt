package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.types.Orientation
import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.types.RectPoint
import kotlin.math.max

class FractionFormulaBox(numChildren: Array<FormulaBox> = emptyArray(), denChildren: Array<FormulaBox> = emptyArray()) : FormulaBox() {
    private val bar = LineFormulaBox(Orientation.H)
    private val num = TransformerFormulaBox(InputFormulaBox(*numChildren), BoundsTransformer.Align(RectPoint.BOTTOM_CENTER))
    private val den = TransformerFormulaBox(InputFormulaBox(*denChildren), BoundsTransformer.Align(RectPoint.TOP_CENTER))
    val numerator
        get() = num.child as InputFormulaBox
    val denominator
        get() = den.child as InputFormulaBox
    init {
        addBox(bar)
        addBox(num)
        addBox(den)
        bar.range = getBarWidth()
        bar.dlgRange.connectValue(num.onBoundsChanged) { _, _ -> getBarWidth() }
        bar.dlgRange.connectValue(den.onBoundsChanged) { _, _ -> getBarWidth() }
        setChildTransform(1, BoxTransform.yOffset(-DEFAULT_TEXT_SIZE * 0.15f))
        setChildTransform(2, BoxTransform.yOffset(DEFAULT_TEXT_SIZE * 0.15f))
        updateGraphics()
    }

    override val selectBeforeDeletion: Boolean
        get() = !numerator.ch.isEmpty() || !denominator.ch.isEmpty()

    override fun onChildRequiresDelete(b: FormulaBox): DeletionResult = when (b) {
        num -> {
            if (numerator.ch.isEmpty() && denominator.ch.isEmpty()) {
                delete()
            } else {
                DeletionResult.fromSelection(this)
            }
        }
        den -> {
            delete().withFinalBoxes(numerator.ch, denominator.ch, !denominator.ch.isEmpty())
        }
        else -> delete()
    }

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        val boxes = if (ib.hasSelection) {
                ib.selectedBoxes
            } else {
                ib.boxesBefore.takeLastWhile { it !is TextFormulaBox || (it.text != "+" && it.text != "-") }
            }
        if (boxes.size == 1 && boxes[0] is BracketsInputFormulaBox) {
            (boxes[0] as BracketsInputFormulaBox).input.delete().finalBoxes.boxesBefore.forEach { numerator.addBox(it) }
        } else {
            boxes.forEach { numerator.addBox(it) }
        }
        return FinalBoxes()
    }

    override fun getInitialSingle() = if (numerator.ch.isEmpty()) {
        numerator.lastSingle
    } else {
        denominator.lastSingle
    }

    override fun findChildBox(pos: PointF): FormulaBox =
        if (bar.realBounds.run { pos.x in left..right }) {
            if (pos.y > 0) {
                den
            } else {
                num
            }
        } else {
            this
        }

    override fun generateGraphics(): FormulaGraphics { // padding ?
        val gr = super.generateGraphics()
        return FormulaGraphics(
            gr.path,
            gr.painting,
            Padding(DEFAULT_TEXT_WIDTH * 0.25f, 0f).applyOnRect(gr.bounds)
        )
    }

    private fun getBarWidth(): RangeF {
        val w = max(num.bounds.width(), den.bounds.width()) + DEFAULT_TEXT_WIDTH * 0.25f
        val r = w * 0.5f
        return RangeF(-r, r)
    }
}
