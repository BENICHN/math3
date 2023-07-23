package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.caret.ContextMenuEntry

class DerivativeOperatorFormulaBox(type: Type = Type.BOTTOM) : TopDownFormulaBox(
    limitsPosition = LimitsPosition.RIGHT,
    limitsScale = 0.75f,
    type = type,
    middle = TextFormulaBox("∂"),
    bottom = InputFormulaBox(),
    top = InputFormulaBox()
) {
    val variable = bottom as InputFormulaBox
    val order = top as InputFormulaBox

    override fun generateContextMenu() = ContextMenu(listOf(
        ContextMenuEntry.create<DerivativeOperatorFormulaBox>(
            DerivativeOperatorFormulaBox(Type.BOTTOM)
        ) {
            it.type = Type.BOTTOM
        },
        ContextMenuEntry.create<DerivativeOperatorFormulaBox>(
            DerivativeOperatorFormulaBox(Type.BOTH)
        ) {
            it.type = Type.BOTH
        }),
        listOf(
            middle
        )
    )

    override fun getInitialSingle() = (bottom as InputFormulaBox).lastSingle

    init {
        allowedTypes = listOf(
            Type.BOTH,
            Type.BOTTOM
        )
    }
}

class DerivativeFormulaBox(type: TopDownFormulaBox.Type = TopDownFormulaBox.Type.BOTTOM) : SequenceFormulaBox(
    DerivativeOperatorFormulaBox(type),
    BracketsInputFormulaBox(type = BracketFormulaBox.Type.BRACKET)
) {
    val operator = ch[0] as DerivativeOperatorFormulaBox
    val brackets = ch[1] as BracketsInputFormulaBox
    override fun getInitialSingle() = operator.variable.lastSingle

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        brackets.input.addBoxes(ib.selectedBoxes)
        return FinalBoxes()
    }
}