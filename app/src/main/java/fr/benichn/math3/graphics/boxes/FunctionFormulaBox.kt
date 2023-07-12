package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.types.Side

class FunctionFormulaBox(text: String = "", type: BracketFormulaBox.Type = BracketFormulaBox.Type.BRACE) : SequenceFormulaBox(
    TextFormulaBox(),
    BracketFormulaBox(side = Side.L),
    InputFormulaBox(),
    BracketFormulaBox(side = Side.R)
) {
    val textBox = ch[0] as TextFormulaBox
    val leftBracket = ch[1] as BracketFormulaBox
    val input = ch[2] as InputFormulaBox
    val rightBracket = ch[3] as BracketFormulaBox

    val dlgType = BoxProperty(this, type)
    var type by dlgType

    val dlgText = BoxProperty(this, text)
    val text by dlgText

    init {
        textBox.dlgText.connectTo(dlgText)
        leftBracket.dlgRange.connectValue(input.onBoundsChanged, input.bounds) { r -> RangeF.fromRectV(r) }
        rightBracket.dlgRange.connectValue(input.onBoundsChanged, input.bounds) { r -> RangeF.fromRectV(r) }
        leftBracket.dlgType.connectTo(dlgType)
        rightBracket.dlgType.connectTo(dlgType)
    }

    override val selectBeforeDeletion: Boolean
        get() = input.ch.isNotEmpty()

    override fun getInitialSingle() = input.lastSingle

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        input.addBoxes(ib.selectedBoxes)
        return FinalBoxes()
    }

    override fun onChildRequiresDelete(b: FormulaBox) = when {
        b == input && input.ch.isNotEmpty() -> {
            DeletionResult(CaretPosition.DiscreteSelection(this, listOf(0, 1, 3)))
        }
        else -> delete()
    }

    override fun deleteMultiple(indices: List<Int>) = if (indices == listOf(0, 1, 3)) {
        delete().withFinalBoxes(boxesAfter = input.ch.toList())
    } else throw UnsupportedOperationException()

    override fun generateGraphics() = super.generateGraphics().withBounds { r ->
        Padding(0.25f * DEFAULT_TEXT_WIDTH, 0f).applyOnRect(r)
    }
}