package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.Padding

class OperationFormulaBox(operator: FormulaBox = FormulaBox(),
                          type: ScriptFormulaBox.Type = ScriptFormulaBox.Type.BOTH,
                          below: FormulaBox = FormulaBox(),
                          above: FormulaBox = FormulaBox(),
                          vararg expression: FormulaBox) : SequenceFormulaBox(
    BigOperatorFormulaBox(operator, below, above).apply { padding = Padding(DEFAULT_TEXT_WIDTH * 0.25f, 0f) },
    *expression) {
    val bigOperator = ch[0] as BigOperatorFormulaBox

    val dlgType = BoxProperty(this, type).also {
        bigOperator.dlgType.connectTo(it)
    }
    var type by dlgType

    override fun generateGraphics() = super.generateGraphics().withBounds { r -> Padding(DEFAULT_TEXT_WIDTH * 0.25f, 0f, 0f, 0f).applyOnRect(r) }
}