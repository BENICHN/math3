package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.Utils.toBoxes
import fr.benichn.math3.graphics.Utils.append
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaBoxDeserializer
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.types.Orientation
import fr.benichn.math3.graphics.boxes.types.RangeF
import kotlin.math.max

class FractionFormulaBox(
    numeratorBoxes: List<FormulaBox> = listOf(),
    denominatorBoxes: List<FormulaBox> = listOf()
) : TopDownFormulaBox(
    middle = LineFormulaBox(Orientation.H),
    bottom = InputFormulaBox(denominatorBoxes),
    top = InputFormulaBox(numeratorBoxes),
    revertTopDown = true
) {
    private val bar = middle as LineFormulaBox
    val denominator = bottom as InputFormulaBox
    val numerator = top as InputFormulaBox
    init {
        bottomContainer.apply {
            modifyTransformers { it.append(BoundsTransformer.Constant(BoxTransform.yOffset(DEFAULT_TEXT_SIZE * 0.15f))) }
        }
        topContainer.apply {
            modifyTransformers { it.append(BoundsTransformer.Constant(BoxTransform.yOffset(DEFAULT_TEXT_SIZE * -0.15f))) }
        }
        bar.range = getBarWidth()
        bar.dlgRange.connectValue(topContainer.onBoundsChanged) { _, _ -> getBarWidth() }
        bar.dlgRange.connectValue(bottomContainer.onBoundsChanged) { _, _ -> getBarWidth() }
        updateGraphics()
    }

    override fun getFinalBoxes() = FinalBoxes(numerator.chr, denominator.chr, denominator.ch.size > 1)

    override fun onChildRequiresDelete(b: FormulaBox, vararg anticipation: FormulaBox): DeletionResult = when (b) {
        topContainer -> {
            deleteIfNotFilled()
        }
        bottomContainer -> {
            delete().withFinalBoxes(this)
        }
        else -> delete()
    }

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        val boxes = if (ib.hasSelection) {
                ib.selectedBoxes
            } else {
                ib.boxesBefore.takeLastWhile { !it.hasText { s -> s == "+" || s == "-" } }
            }
        if (boxes.size == 1 && boxes[0] is BracketsInputFormulaBox) {
            (boxes[0] as BracketsInputFormulaBox).input.also {
                numerator.addBoxes(it.chr)
                it.delete()
            }
        } else {
            numerator.addBoxes(boxes)
        }
        return FinalBoxes()
    }

    override fun getInitialSingle() = if (numerator.ch.size == 1) {
        numerator.lastSingle
    } else {
        denominator.lastSingle
    }

    // override fun findChildBox(pos: PointF): FormulaBox =
    //     if (bar.realBounds.run { pos.x in left..right }) {
    //         if (pos.y > 0) {
    //             den
    //         } else {
    //             num
    //         }
    //     } else {
    //         this
    //     }

    override fun generateGraphics() = super.generateGraphics().withBounds { r ->
        Padding(DEFAULT_TEXT_WIDTH * 0.25f, 0f).applyOnRect(r)
    }

    private fun getBarWidth(): RangeF {
        val w = max(topContainer.bounds.width(), bottomContainer.bounds.width()) + DEFAULT_TEXT_WIDTH * 0.25f
        val r = w * 0.5f
        return RangeF(-r, r)
    }

    override fun toWolfram(mode: Int) = "(${numerator.toWolfram(mode)})/(${denominator.toWolfram(mode)})"
    // override fun toSage() = "(${numerator.toSage()})/(${denominator.toSage()})"

    override fun toJson() = makeJsonObject("frac") {
        add("numerator", numerator.toJson())
        add("denominator", denominator.toJson())
    }
}
