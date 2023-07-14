package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.boxes.types.Padding

class OperationFormulaBox(operator: FormulaBox = FormulaBox(),
                          below: FormulaBox = FormulaBox(),
                          above: FormulaBox = FormulaBox(),
                          vararg expression: FormulaBox) : SequenceFormulaBox(
    BigOperatorFormulaBox(operator, below, above).apply { padding = Padding(DEFAULT_TEXT_WIDTH * 0.5f, 0f) },
    *expression) {
    val bigOperator = ch[0]
}