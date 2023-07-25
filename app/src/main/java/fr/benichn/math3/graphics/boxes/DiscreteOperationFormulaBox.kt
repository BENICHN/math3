package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.Utils.Companion.scale
import fr.benichn.math3.graphics.boxes.SequenceChild.Companion.ign
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.caret.ContextMenuEntry

class DiscreteOperatorFormulaBox(operator: String, operatorType: Type = Type.BOUNDS) : TopDownFormulaBox(
    limitsPosition = LimitsPosition.CENTER,
    limitsScale = 0.75f,
    type = if (operatorType == Type.BOUNDS) TopDownFormulaBox.Type.BOTH else TopDownFormulaBox.Type.BOTTOM,
    middle = TextFormulaBox(operator, true),
    bottom = SequenceFormulaBox(),
    top = InputFormulaBox()
) {
    val dlgOperatorType = BoxProperty(this, operatorType).apply {
        onChanged += { _, e ->
            if (e.changed) {
                type = TopDownFormulaBox.Type.NONE
                bottom.removeAllBoxes()
                applyType()
            }
        }
    }
    var operatorType: Type by dlgOperatorType

    val dlgOperator = BoxProperty(this, operator)
    var operator by dlgOperator

    init {
        allowedTypes = listOf()
        applyType()
    }

    override fun generateContextMenu() = ContextMenu(
        ContextMenuEntry.create<DiscreteOperatorFormulaBox>(
            DiscreteOperatorFormulaBox(operator, Type.BOUNDS)
        ) {
            it.operatorType = Type.BOUNDS
        },
        ContextMenuEntry.create<DiscreteOperatorFormulaBox>(
            DiscreteOperatorFormulaBox(operator, Type.LIST)
        ) {
            it.operatorType = Type.LIST
        },
        ContextMenuEntry.create<DiscreteOperatorFormulaBox>(
            DiscreteOperatorFormulaBox(operator, Type.INDEFINITE)
        ) {
            it.operatorType = Type.INDEFINITE
        },
        trigger = { pos ->
            middle.realBounds.scale(
                0.5f,
                0.33f
            ).contains(pos.x, pos.y)
        },
    )

    private fun applyType() = when (operatorType) {
        Type.LIST -> {
            (bottom as SequenceFormulaBox).addBoxes(
                InputFormulaBox() ign false,
                TextFormulaBox("∈") ign true,
                BracketsInputFormulaBox(type = BracketFormulaBox.Type.CURLY) ign false
            )
            type = TopDownFormulaBox.Type.BOTTOM
        }
        Type.BOUNDS -> {
            (bottom as SequenceFormulaBox).addBoxes(
                InputFormulaBox() ign false,
                TextFormulaBox("=") ign true,
                InputFormulaBox() ign false
            )
            type = TopDownFormulaBox.Type.BOTH
        }
        Type.INDEFINITE -> {
            (bottom as SequenceFormulaBox).addBoxes(
                InputFormulaBox()
            )
            type = TopDownFormulaBox.Type.BOTTOM
        }
    }

    enum class Type {
        LIST,
        BOUNDS,
        INDEFINITE
    }
}

class DiscreteOperationFormulaBox(operator: String, operatorType: DiscreteOperatorFormulaBox.Type = DiscreteOperatorFormulaBox.Type.BOUNDS) : SequenceFormulaBox(
    DiscreteOperatorFormulaBox(operator, operatorType),
    InputFormulaBox()
) {
    val operator = ch[0] as DiscreteOperatorFormulaBox
    val operand = ch[1] as InputFormulaBox

    init {
        updateGraphics()
    }

    override fun getInitialSingle() = if (operand.ch.isEmpty()) operand.lastSingle else operator.getInitialSingle()

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        operand.addBoxes(ib.selectedBoxes)
        return FinalBoxes()
    }

    override fun generateGraphics() = super.generateGraphics().withBounds { r ->
        Padding(0f, 0f, DEFAULT_TEXT_WIDTH * 0.25f, 0f).applyOnRect(r)
    }
}