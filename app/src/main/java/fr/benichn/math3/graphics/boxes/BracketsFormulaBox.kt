package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.types.Side

class BracketsFormulaBox : SeqFormulaBox(
    BracketFormulaBox(),
    InputFormulaBox(),
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
}