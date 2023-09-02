package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.Utils.toBoxes
import fr.benichn.math3.graphics.Utils.scale
import fr.benichn.math3.graphics.boxes.SequenceFormulaBox.Child.Companion.ign
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaBoxDeserializer
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
                (bottom as SequenceFormulaBox).clearBoxes()
                applyType()
            }
        }
    }
    var operatorType: Type by dlgOperatorType

    val dlgOperator = BoxProperty(this, operator)
    var operator by dlgOperator

    override fun getInitialSingle() =
        bottom.ch.firstNotNullOfOrNull {
            if (it is InputFormulaBox && !it.isFilled) it.lastSingle else null
        } ?: if (operatorType == Type.BOUNDS && !top.isFilled) (top as InputFormulaBox).lastSingle else null

    init {
        allowedTypes = listOf()
        applyType()
    }

    override fun generateContextMenu() = ContextMenu(
        ContextMenuEntry.create<DiscreteOperatorFormulaBox>(
            DiscreteOperatorFormulaBox(operator, Type.BOUNDS)
        ) {
            it.operatorType = Type.BOUNDS
            null
        },
        ContextMenuEntry.create<DiscreteOperatorFormulaBox>(
            DiscreteOperatorFormulaBox(operator, Type.LIST)
        ) {
            it.operatorType = Type.LIST
            null
        },
        ContextMenuEntry.create<DiscreteOperatorFormulaBox>(
            DiscreteOperatorFormulaBox(operator, Type.INDEFINITE)
        ) {
            it.operatorType = Type.INDEFINITE
            null
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

    override fun toJson() = makeJsonObject("discr_op") {
        addProperty("operator", operator)
        addProperty("type", operatorType.toString())
        add("variable", bottom.ch[1].toJson())
        when (operatorType) {
            Type.LIST -> {
                add("list", (bottom.ch[3] as BracketsInputFormulaBox).input.toJson())
            }
            Type.BOUNDS -> {
                add("lower", bottom.ch[3].toJson())
                add("upper", top.toJson())
            }
            Type.INDEFINITE -> {
            }
        }
    }
}

class DiscreteOperationFormulaBox(operator: String, operatorType: DiscreteOperatorFormulaBox.Type = DiscreteOperatorFormulaBox.Type.BOUNDS) : SequenceFormulaBox(
    DiscreteOperatorFormulaBox(operator, operatorType),
    InputFormulaBox()
) {
    val operator = ch[1] as DiscreteOperatorFormulaBox
    val operand = ch[2] as InputFormulaBox

    init {
        updateGraphics()
    }

    override fun getInitialSingle() = operator.getInitialSingle() ?: operand.getInitialSingle()

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        operand.addBoxes(ib.selectedBoxes)
        return FinalBoxes()
    }

    override fun generateGraphics() = super.generateGraphics().withBounds { r ->
        Padding(0f, 0f, DEFAULT_TEXT_WIDTH * 0.25f, 0f).applyOnRect(r)
    }

    override fun toWolfram(mode: Int): String {
        val name = when (operator.operator) {
            "∑" -> "Sum"
            "∏" -> "Product"
            else -> throw UnsupportedOperationException()
        }
        return when (operator.operatorType) {
            DiscreteOperatorFormulaBox.Type.LIST ->
                "$name[${operand.toWolfram(mode)}, {${operator.bottom.ch[1].toWolfram(mode)}, ${operator.bottom.ch[3].toWolfram(mode)}}]"
            DiscreteOperatorFormulaBox.Type.BOUNDS ->
                "$name[${operand.toWolfram(mode)}, {${operator.bottom.ch[1].toWolfram(mode)}, ${operator.bottom.ch[3].toWolfram(mode)}, ${operator.top.toWolfram(mode)}}]"
            DiscreteOperatorFormulaBox.Type.INDEFINITE ->
                "$name[${operand.toWolfram(mode)}, ${operator.bottom.toWolfram(mode)}]"
        }
    }

    // override fun toSage(): String {
    //     val name = when (operator.operator) {
    //         "∑" -> "sum"
    //         "∏" -> "product"
    //         else -> throw UnsupportedOperationException()
    //     }
    //     return when (operator.operatorType) {
    //         DiscreteOperatorFormulaBox.Type.LIST ->
    //             "$name([${operand.toSage()} for ${operator.bottom.ch[0].toSage()} in [${(operator.bottom.ch[2] as BracketsInputFormulaBox).input.toSage()}]])"
    //         DiscreteOperatorFormulaBox.Type.BOUNDS ->
    //             "$name(${operand.toSage()}, ${operator.bottom.ch[0].toSage()}, ${operator.bottom.ch[2].toSage()}, ${operator.top.toSage()})"
    //         DiscreteOperatorFormulaBox.Type.INDEFINITE -> TODO()
    //     }
    // }

    override fun toJson() = makeJsonObject("discr") {
        add("operator", operator.toJson())
        add("operand", operand.toJson())
    }

    companion object {
        init {
            deserializers.add(FormulaBoxDeserializer("discr") {
                val op = getAsJsonObject("operator")
                val type = DiscreteOperatorFormulaBox.Type.valueOf(op["type"].asString)
                DiscreteOperationFormulaBox(op["operator"].asString, type).apply {
                    operand.addBoxes(getAsJsonArray("operand").toBoxes())
                    operator.bottom.ch[1].addBoxes(op.getAsJsonArray("variable").toBoxes())
                    when (type) {
                        DiscreteOperatorFormulaBox.Type.LIST -> {
                            (operator.bottom.ch[3] as BracketsInputFormulaBox).input.addBoxes(op.getAsJsonArray("list").toBoxes())
                        }
                        DiscreteOperatorFormulaBox.Type.BOUNDS -> {
                            operator.bottom.ch[3].addBoxes(op.getAsJsonArray("lower").toBoxes())
                            operator.top.addBoxes(op.getAsJsonArray("upper").toBoxes())
                        }
                        DiscreteOperatorFormulaBox.Type.INDEFINITE -> {
                        }
                    }
                }
            })
        }
    }
}