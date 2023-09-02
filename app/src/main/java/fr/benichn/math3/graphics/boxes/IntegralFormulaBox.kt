package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.Utils.toBoxes
import fr.benichn.math3.graphics.Utils.scale
import fr.benichn.math3.graphics.boxes.SequenceFormulaBox.Child.Companion.ign
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaBoxDeserializer
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.caret.ContextMenuEntry

class IntegralOperatorFormulaBox(type: Type = Type.BOTH) : TopDownFormulaBox(
    limitsPosition = LimitsPosition.RIGHT,
    limitsScale = 0.75f,
    type = type,
    middle = TextFormulaBox("∫", true, 0.5f),
    bottom = InputFormulaBox(),
    top = InputFormulaBox()
) {
    val lower = bottom as InputFormulaBox
    val upper = top as InputFormulaBox

    init {
        allowedTypes = listOf(
            Type.BOTH,
            Type.NONE
        )
    }

    override fun generateContextMenu() = ContextMenu(
        ContextMenuEntry.create<IntegralOperatorFormulaBox>(
            IntegralOperatorFormulaBox(Type.BOTH)
        ) {
            it.type = Type.BOTH
            null
        },
        ContextMenuEntry.create<IntegralOperatorFormulaBox>(
            IntegralOperatorFormulaBox(Type.NONE)
        ) {
            it.type = Type.NONE
            null
        },
        trigger = { pos ->
            middle.realBounds.scale(
                0.5f,
                0.33f
            ).contains(pos.x, pos.y)
        })

    override fun getInitialSingle() = when (type) {
        Type.BOTH -> if (upper.ch.size == 1) upper.lastSingle else lower.lastSingle
        else -> null
    }
}

class IntegralFormulaBox(type: TopDownFormulaBox.Type = TopDownFormulaBox.Type.BOTH) : SequenceFormulaBox(
    IntegralOperatorFormulaBox(type) ign false,
    InputFormulaBox() ign false,
    TextFormulaBox("ⅆ") ign true,
    InputFormulaBox() ign false
) {
    val operator = ch[1] as IntegralOperatorFormulaBox
    val integrand = ch[2] as InputFormulaBox
    val variable = ch[4] as InputFormulaBox

    init {
        updateGraphics()
    }

    override fun getInitialSingle() = if (integrand.ch.size == 1) integrand.lastSingle else operator.getInitialSingle()

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        integrand.addBoxes(ib.selectedBoxes)
        return FinalBoxes()
    }

    override fun generateGraphics() = super.generateGraphics().withBounds { r ->
        Padding(0f, 0f, DEFAULT_TEXT_WIDTH * 0.25f, 0f).applyOnRect(r)
    }

    override fun toWolfram(mode: Int) = when (operator.type) {
        TopDownFormulaBox.Type.NONE -> "Integrate[${integrand.toWolfram(mode)}, ${variable.toWolfram(mode)}]"
        TopDownFormulaBox.Type.BOTH -> "Integrate[${integrand.toWolfram(mode)}, {${variable.toWolfram(mode)}, ${operator.bottom.toWolfram(mode)}, ${operator.top.toWolfram(mode)}}]"
        else -> throw UnsupportedOperationException()
    }

    // override fun toSage() = when (operator.type) {
    //     TopDownFormulaBox.Type.NONE -> "integrate(${integrand.toSage()}, ${variable.toSage()})"
    //     TopDownFormulaBox.Type.BOTH -> "integrate(${integrand.toSage()}, ${variable.toSage()}, ${operator.bottom.toSage()}, ${operator.top.toSage()})"
    //     else -> throw UnsupportedOperationException()
    // }

    override fun toJson() = makeJsonObject("int") {
        add("operator", operator.toJson())
        add("integrand", integrand.toJson())
        add("variable", variable.toJson())
    }

    companion object {
        init {
            deserializers.add(FormulaBoxDeserializer("deriv") {
                val op = getAsJsonObject("operator")
                val type = TopDownFormulaBox.Type.valueOf(op["type"].asString)
                IntegralFormulaBox(type).apply {
                    integrand.addBoxes(getAsJsonArray("integrand").toBoxes())
                    variable.addBoxes(getAsJsonArray("variable").toBoxes())
                    if (type.hasTop) {
                        operator.upper.addBoxes(op.getAsJsonArray("top").toBoxes())
                        operator.lower.addBoxes(op.getAsJsonArray("bottom").toBoxes())
                    }
                }
            })
        }
    }
}