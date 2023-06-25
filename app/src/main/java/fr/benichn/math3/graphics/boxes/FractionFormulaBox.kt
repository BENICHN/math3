package fr.benichn.math3.graphics.boxes

import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.types.Orientation
import fr.benichn.math3.graphics.boxes.types.Range
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.boxes.types.SidedBox
import fr.benichn.math3.graphics.caret.CaretPosition
import kotlin.math.max

class FractionFormulaBox(numChildren: Array<FormulaBox> = emptyArray(), denChildren: Array<FormulaBox> = emptyArray()) : FormulaBox() {
    private val bar = LineFormulaBox(Orientation.H)
    private val num = AlignFormulaBox(InputFormulaBox(*numChildren), RectPoint.BOTTOM_CENTER)
    private val den = AlignFormulaBox(InputFormulaBox(*denChildren), RectPoint.TOP_CENTER)
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
        listenChildBoundsChange(num)
        listenChildBoundsChange(den)
        updateGraphics()
    }

    override val selectBeforeDeletion: Boolean
        get() = !numerator.ch.isEmpty() || !denominator.ch.isEmpty()

    override fun onChildRequiresDelete(b: FormulaBox): DeletionResult = when (b) {
        num -> {
            if (isSelected || (numerator.ch.isEmpty() && denominator.ch.isEmpty())) {
                delete()
            } else {
                isSelected = true
                DeletionResult(getCaretPositionFromSidedBox(this))
            }
        }
        den -> {
            delete().withFinalBoxes(numerator.ch, denominator.ch)
        }
        else -> super.onChildRequiresDelete(b)
    }

    override fun addInitialBoxes(ib: InitialBoxes) {
        val boxes = when (ib) {
            is InitialBoxes.BeforeAfter -> {
                ib.boxesBefore.takeLastWhile { it !is TextFormulaBox || (it.text != "+" && it.text != "-") }
            }
            is InitialBoxes.Selection -> {
                ib.boxes
            }
        }
        for (b in boxes) {
            numerator.addBox(b)
        }
    }

    override fun getInitialCaretPos(): SidedBox = if (numerator.ch.isEmpty()) {
        numerator.getInitialCaretPos()
    } else {
        denominator.getInitialCaretPos()
    }

    override fun findChildBox(absX: Float, absY: Float): FormulaBox =
        if (bar.accRealBounds.let { it.left <= absX && absX <= it.right }) {
            if (absY > accTransform.origin.y) {
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
            gr.paint,
            RectF(gr.bounds.left - DEFAULT_TEXT_WIDTH * 0.25f, gr.bounds.top, gr.bounds.right + DEFAULT_TEXT_WIDTH * 0.25f, gr.bounds.bottom)
        )
    }

    private fun getBarWidth(): Range {
        val w = max(num.bounds.width(), den.bounds.width()) + DEFAULT_TEXT_WIDTH * 0.25f
        val r = w * 0.5f
        return Range(-r, r)
    }
}
