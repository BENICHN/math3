package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.types.Side

class FunctionFormulaBox(text: String = "", type: BracketFormulaBox.Type = BracketFormulaBox.Type.BRACE) : SequenceFormulaBox( // !
    TextFormulaBox(),
    BracketsInputFormulaBox()
) {
    val textBox = ch[0] as TextFormulaBox
    val brackets = ch[1] as BracketsInputFormulaBox

    val dlgType = BoxProperty(this, type)
    var type by dlgType

    val dlgText = BoxProperty(this, text)
    val text by dlgText

    init {
        textBox.dlgText.connectTo(dlgText)
        brackets.dlgType.connectTo(dlgType)
        updateGraphics()
    }

    override fun getInitialSingle() = brackets.input.lastSingle

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        brackets.input.addBoxes(ib.selectedBoxes)
        return FinalBoxes()
    }

    override fun onChildRequiresDelete(b: FormulaBox, vararg anticipation: FormulaBox) = when(b) {
        brackets -> {
            deleteIfNotFilled(anticipation = arrayOf(textBox))
        }
        else -> delete()
    }

    override fun getFinalBoxes() = FinalBoxes(boxesAfter = brackets.input.ch.toList())

    override fun deleteMultiple(boxes: List<FormulaBox>) = if (boxes == listOf(textBox)) {
        delete().withFinalBoxes(this)
    } else throw UnsupportedOperationException()

    override fun generateGraphics() = super.generateGraphics().withBounds { r ->
        Padding(0.25f * DEFAULT_TEXT_WIDTH, 0f).applyOnRect(r)
    }
}