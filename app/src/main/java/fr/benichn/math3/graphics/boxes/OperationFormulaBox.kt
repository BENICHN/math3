package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.boxes.types.Padding

open class OperationFormulaBox(
    bigOperator: BigOperatorFormulaBox,
    vararg expression: FormulaBox) : SequenceFormulaBox(
        bigOperator.apply { padding = Padding(DEFAULT_TEXT_WIDTH * 0.25f, 0f) },
        *expression) {
    val bigOperator = ch[0] as BigOperatorFormulaBox

    override fun generateGraphics() = super.generateGraphics().withBounds { r -> Padding(0f, 0f, DEFAULT_TEXT_WIDTH * 0.25f, 0f).applyOnRect(r) }
}