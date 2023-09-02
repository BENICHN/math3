package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.Utils.toBoxes
import fr.benichn.math3.graphics.boxes.SequenceFormulaBox.Child.Companion.ign
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaBoxDeserializer
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.types.Side

class BracketsInputFormulaBox(boxes: List<FormulaBox>, type: BracketFormulaBox.Type = BracketFormulaBox.Type.BRACE) : BracketsSequenceFormulaBox(
    InputFormulaBox(boxes),
    type = type
) {
    constructor(vararg boxes: FormulaBox, type: BracketFormulaBox.Type = BracketFormulaBox.Type.BRACE) : this(boxes.asList(), type)
    val input = sequence.ch[1] as InputFormulaBox
    init {
        leftBracket.dlgRange.connectValue(input.onBoundsChanged, input.bounds) { r -> RangeF.fromRectV(r) }
        rightBracket.dlgRange.connectValue(input.onBoundsChanged, input.bounds) { r -> RangeF.fromRectV(r) }
        leftBracket.dlgType.connectTo(dlgType)
        rightBracket.dlgType.connectTo(dlgType)
        updateGraphics()
    }

    override val isFilled: Boolean
        get() = false

    override fun getFinalBoxes() = FinalBoxes(input.chr)

    override fun getInitialSingle() = input.lastSingle

    override fun onChildRequiresDelete(b: FormulaBox, vararg anticipation: FormulaBox) = when (b) {
        sequence -> {
            delete().withFinalBoxes(this)
        }
        else -> delete()
    }

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        input.addBoxes(ib.selectedBoxes)
        return FinalBoxes()
    }

    override fun toJson() = makeJsonObject("brackets") {
        addProperty("type", type.toString())
        add("input", input.toJson())
    }

    companion object {
        init {
            deserializers.add(FormulaBoxDeserializer("brackets") {
                BracketsInputFormulaBox(
                    getAsJsonArray("input").toBoxes(),
                    BracketFormulaBox.Type.valueOf(get("type").asString)
                )
            })
        }
    }
}

open class BracketsSequenceFormulaBox(boxes: List<FormulaBox>, type: BracketFormulaBox.Type = BracketFormulaBox.Type.BRACE, updGr: Boolean = true) : SequenceFormulaBox(
    BracketFormulaBox(side = Side.L) ign true,
    SequenceFormulaBox(boxes) ign false,
    BracketFormulaBox(side = Side.R) ign true
) {
    constructor(vararg boxes: FormulaBox, type: BracketFormulaBox.Type = BracketFormulaBox.Type.BRACE, updGr: Boolean = true) : this(boxes.asList(), type, updGr)
    val dlgType = BoxProperty(this, type)
    var type by dlgType

    val leftBracket = ch[1] as BracketFormulaBox
    val sequence = ch[2] as SequenceFormulaBox
    val rightBracket = ch[3] as BracketFormulaBox
    init {
        leftBracket.dlgRange.connectValue(sequence.onBoundsChanged, sequence.bounds) { r -> RangeF.fromRectV(r) }
        rightBracket.dlgRange.connectValue(sequence.onBoundsChanged, sequence.bounds) { r -> RangeF.fromRectV(r) }
        leftBracket.dlgType.connectValue(dlgType.onChanged, type) { t -> t }
        rightBracket.dlgType.connectValue(dlgType.onChanged, type) { t -> t }
        if (updGr) updateGraphics()
    }

    override fun toWolfram(mode: Int) = when (type) {
        BracketFormulaBox.Type.BAR -> "Abs[${sequence.toWolfram(mode)}]"
        BracketFormulaBox.Type.FLOOR -> "Floor[${sequence.toWolfram(mode)}]"
        BracketFormulaBox.Type.CEIL -> "Ceiling[${sequence.toWolfram(mode)}]"
        else -> "${leftBracket.toWolfram(mode)}${sequence.toWolfram(mode)}${rightBracket.toWolfram(mode)}"
    }

    // override fun toSage() = when (type) {
    //     BracketFormulaBox.Type.BAR -> "abs(${sequence.toSage()})"
    //     BracketFormulaBox.Type.FLOOR -> "floor(${sequence.toSage()})"
    //     BracketFormulaBox.Type.CEIL -> "ceil(${sequence.toSage()})"
    //     else -> "${leftBracket.toSage()}${sequence.toSage()}${rightBracket.toSage()}"
    // }
}