package fr.benichn.math3.graphics.boxes

import com.google.gson.JsonObject
import fr.benichn.math3.Utils.toBox
import fr.benichn.math3.Utils.toBoxes
import fr.benichn.math3.graphics.Utils.scale
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaBoxDeserializer
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
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

    init {
        allowedTypes = listOf(
            Type.BOTH,
            Type.BOTTOM
        )
    }

    override fun generateContextMenu() = ContextMenu(listOf(
        ContextMenuEntry.create<DerivativeOperatorFormulaBox>(
            DerivativeOperatorFormulaBox(Type.BOTTOM)
        ) {
            it.type = Type.BOTTOM
            null
        },
        ContextMenuEntry.create<DerivativeOperatorFormulaBox>(
            DerivativeOperatorFormulaBox(Type.BOTH)
        ) {
            it.type = Type.BOTH
            null
        }),
        trigger = { pos ->
            middle.realBounds.scale(
                1f,
                1f
            ).contains(pos.x, pos.y)
        }
    )

    override fun getInitialSingle() = (bottom as InputFormulaBox).lastSingle
}

class DerivativeFormulaBox(type: TopDownFormulaBox.Type = TopDownFormulaBox.Type.BOTTOM) : SequenceFormulaBox(
    DerivativeOperatorFormulaBox(type),
    BracketsInputFormulaBox(type = BracketFormulaBox.Type.BRACKET)
) {
    val operator = ch[1] as DerivativeOperatorFormulaBox
    val brackets = ch[2] as BracketsInputFormulaBox
    override fun getInitialSingle() = operator.variable.lastSingle

    init {
        updateGraphics()
    }

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        brackets.input.addBoxes(ib.selectedBoxes)
        return FinalBoxes()
    }

    override fun toWolfram() = "D[${brackets.input.toWolfram()}, " +
            when (operator.type) {
                TopDownFormulaBox.Type.BOTTOM -> operator.variable.toWolfram()
                TopDownFormulaBox.Type.BOTH -> "{${operator.variable.toWolfram()}, ${operator.order.toWolfram()}}"
                else -> throw UnsupportedOperationException()
            } + "]"

    override fun toSage() = "diff(${brackets.input.toSage()}, " +
            when (operator.type) {
                TopDownFormulaBox.Type.BOTTOM -> operator.variable.toSage()
                TopDownFormulaBox.Type.BOTH -> "var(${operator.variable.toSage()}), ${operator.order.toSage()}"
                else -> throw UnsupportedOperationException()
            } + ")"

    override fun toJson() = makeJsonObject("deriv") {
        add("operator", operator.toJson())
        add("input", brackets.input.toJson())
    }

    companion object {
        init {
            deserializers.add(FormulaBoxDeserializer("deriv") {
                val op = getAsJsonObject("operator")
                val type = TopDownFormulaBox.Type.valueOf(op["type"].asString)
                DerivativeFormulaBox(type).apply {
                    brackets.input.addBoxes(getAsJsonArray("input").toBoxes())
                    if (type.hasTop) operator.order.addBoxes(op.getAsJsonArray("top").toBoxes())
                    operator.variable.addBoxes(op.getAsJsonArray("bottom").toBoxes())
                }
            })
        }
    }
}