package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.types.Side

class BracketsFormulaBox(vararg boxes: FormulaBox) : SeqFormulaBox(
    BracketFormulaBox(),
    InputFormulaBox(*boxes),
    BracketFormulaBox(side = Side.R)
) {
    val leftBracket = ch[0] as BracketFormulaBox
    val input = ch[1] as InputFormulaBox
    val rightBracket = ch[2] as BracketFormulaBox
    init {
        leftBracket.dlgRange.connectValue(input.onBoundsChanged, input.bounds) { r -> RangeF.fromRectV(r) }
        rightBracket.dlgRange.connectValue(input.onBoundsChanged, input.bounds) { r -> RangeF.fromRectV(r) }
        listenChildBoundsChange(rightBracket)
    }

    override val selectBeforeDeletion: Boolean
        get() = true

    override fun getInitialSingle() = input.lastSingle

    override fun onChildRequiresDelete(b: FormulaBox) = when (b) {
        input -> {
            delete().withFinalBoxes(input.ch.toList())
        }
        else -> super.onChildRequiresDelete(b)
    }

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        when (ib) {
            is InitialBoxes.Selection -> {
                ib.boxes.forEach { input.addBox(it) }
            }
            else -> { }
        }
        return FinalBoxes()
    }
}