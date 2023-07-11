package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.types.Side

class BracketsInputFormulaBox(vararg boxes: FormulaBox) : SequenceFormulaBox(
    BracketFormulaBox(side = Side.L),
    InputFormulaBox(*boxes),
    BracketFormulaBox(side = Side.R)
) {
    val leftBracket = ch[0] as BracketFormulaBox
    val input = ch[1] as InputFormulaBox
    val rightBracket = ch[2] as BracketFormulaBox
    init {
        leftBracket.dlgRange.connectValue(input.onBoundsChanged, input.bounds) { r -> RangeF.fromRectV(r) }
        rightBracket.dlgRange.connectValue(input.onBoundsChanged, input.bounds) { r -> RangeF.fromRectV(r) }
    }

    override val selectBeforeDeletion: Boolean
        get() = true

    override fun getInitialSingle() = input.lastSingle

    override fun onChildRequiresDelete(b: FormulaBox) = when (b) {
        input -> {
            delete().withFinalBoxes(input.ch.toList())
        }
        else -> delete()
    }

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        ib.selectedBoxes.forEach { input.addBox(it) }
        return FinalBoxes()
    }
}

class BracketsSequenceFormulaBox(vararg boxes: FormulaBox) : SequenceFormulaBox(
    BracketFormulaBox(side = Side.L),
    SequenceFormulaBox(*boxes),
    BracketFormulaBox(side = Side.R)
) {
    val leftBracket = ch[0] as BracketFormulaBox
    val sequence = ch[1] as SequenceFormulaBox
    val rightBracket = ch[2] as BracketFormulaBox
    init {
        leftBracket.dlgRange.connectValue(sequence.onBoundsChanged, sequence.bounds) { r -> RangeF.fromRectV(r) }
        rightBracket.dlgRange.connectValue(sequence.onBoundsChanged, sequence.bounds) { r -> RangeF.fromRectV(r) }
    }
}