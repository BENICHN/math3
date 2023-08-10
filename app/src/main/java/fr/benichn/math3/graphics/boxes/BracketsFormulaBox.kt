package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.boxes.SequenceFormulaBox.Child.Companion.ign
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.types.Side

class BracketsInputFormulaBox(vararg boxes: FormulaBox, type: BracketFormulaBox.Type = BracketFormulaBox.Type.BRACE) : BracketsSequenceFormulaBox(
    InputFormulaBox(*boxes),
    type = type
) {
    val input = sequence.ch[1] as InputFormulaBox
    init {
        leftBracket.dlgRange.connectValue(input.onBoundsChanged, input.bounds) { r -> RangeF.fromRectV(r) }
        rightBracket.dlgRange.connectValue(input.onBoundsChanged, input.bounds) { r -> RangeF.fromRectV(r) }
        leftBracket.dlgType.connectTo(dlgType)
        rightBracket.dlgType.connectTo(dlgType)
        updateGraphics()
    }

    override val isFilled: Boolean
        get() = false

    override fun getFinalBoxes() = FinalBoxes(input.chr)

    override fun getInitialSingle() = input.lastSingle

    override fun onChildRequiresDelete(b: FormulaBox, vararg anticipation: FormulaBox) = when (b) {
        sequence -> {
            delete().withFinalBoxes(this)
        }
        else -> delete()
    }

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        input.addBoxes(ib.selectedBoxes)
        return FinalBoxes()
    }
}

open class BracketsSequenceFormulaBox(vararg boxes: FormulaBox, type: BracketFormulaBox.Type = BracketFormulaBox.Type.BRACE) : SequenceFormulaBox(
    BracketFormulaBox(side = Side.L) ign true,
    SequenceFormulaBox(*boxes) ign false,
    BracketFormulaBox(side = Side.R) ign true
) {
    val dlgType = BoxProperty(this, type)
    var type by dlgType

    val leftBracket = ch[1] as BracketFormulaBox
    val sequence = ch[2] as SequenceFormulaBox
    val rightBracket = ch[3] as BracketFormulaBox
    init {
        leftBracket.dlgRange.connectValue(sequence.onBoundsChanged, sequence.bounds) { r -> RangeF.fromRectV(r) }
        rightBracket.dlgRange.connectValue(sequence.onBoundsChanged, sequence.bounds) { r -> RangeF.fromRectV(r) }
        leftBracket.dlgType.connectValue(dlgType.onChanged, type) { t -> t }
        rightBracket.dlgType.connectValue(dlgType.onChanged, type) { t -> t }
        updateGraphics()
    }

    override fun toWolfram() = when (type) {
        BracketFormulaBox.Type.BAR -> "Abs[${sequence.toWolfram()}]"
        BracketFormulaBox.Type.FLOOR -> "Floor[${sequence.toWolfram()}]"
        BracketFormulaBox.Type.CEIL -> "Ceiling[${sequence.toWolfram()}]"
        else -> "${leftBracket.toWolfram()}${sequence.toWolfram()}${rightBracket.toWolfram()}"
    }

    override fun toSage() = when (type) {
        BracketFormulaBox.Type.BAR -> "abs(${sequence.toSage()})"
        BracketFormulaBox.Type.FLOOR -> "floor(${sequence.toSage()})"
        BracketFormulaBox.Type.CEIL -> "ceil(${sequence.toSage()})"
        else -> "${leftBracket.toSage()}${sequence.toSage()}${rightBracket.toSage()}"
    }
}