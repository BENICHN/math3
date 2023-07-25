package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import fr.benichn.math3.graphics.Utils.Companion.scale
import fr.benichn.math3.graphics.boxes.SequenceChild.Companion.ign
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
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
        },
        ContextMenuEntry.create<IntegralOperatorFormulaBox>(
            IntegralOperatorFormulaBox(Type.NONE)
        ) {
            it.type = Type.NONE
        },
        trigger = { pos ->
            middle.realBounds.scale(
                0.5f,
                0.33f
            ).contains(pos.x, pos.y)
        })

    override fun getInitialSingle() = when (type) {
        Type.BOTH -> if (upper.ch.isEmpty()) upper.lastSingle else lower.lastSingle
        else -> null
    }
}

class IntegralFormulaBox(type: TopDownFormulaBox.Type = TopDownFormulaBox.Type.BOTH) : SequenceFormulaBox(
    IntegralOperatorFormulaBox(type) ign false,
    InputFormulaBox() ign false,
    TextFormulaBox("ⅆ") ign true,
    InputFormulaBox() ign false
) {
    val operator = ch[0] as IntegralOperatorFormulaBox
    val integrand = ch[1] as InputFormulaBox
    val variable = ch[3] as InputFormulaBox

    init {
        updateGraphics()
    }

    override fun getInitialSingle() = if (integrand.ch.isEmpty()) integrand.lastSingle else operator.getInitialSingle()

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        integrand.addBoxes(ib.selectedBoxes)
        return FinalBoxes()
    }

    override fun generateGraphics() = super.generateGraphics().withBounds { r ->
        Padding(0f, 0f, DEFAULT_TEXT_WIDTH * 0.25f, 0f).applyOnRect(r)
    }
}