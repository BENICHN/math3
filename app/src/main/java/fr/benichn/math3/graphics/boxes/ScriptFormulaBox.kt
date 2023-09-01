package fr.benichn.math3.graphics.boxes

import android.graphics.Path
import android.graphics.RectF
import com.google.gson.JsonObject
import fr.benichn.math3.Utils.toBoxes
import fr.benichn.math3.Utils.toJsonArray
import fr.benichn.math3.graphics.Utils.with
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaBoxDeserializer
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.types.RectPoint
import kotlin.math.max

class ScriptFormulaBox(type: Type = Type.TOP, range: RangeF = RangeF(-DEFAULT_TEXT_RADIUS, DEFAULT_TEXT_RADIUS)) : TopDownFormulaBox(
    LimitsPosition.RIGHT,
    0.75f,
    type,
    PhantomFormulaBox(),
    InputFormulaBox(),
    InputFormulaBox()
) {
    private val phantom = middle as PhantomFormulaBox
    val subscript = bottom as InputFormulaBox
    val superscript = top as InputFormulaBox

    var initialBoxesInScript: Boolean = false

    val dlgRange = BoxProperty(this, range).apply {
        onChanged += { _, _ -> applyRange() }
    }
    var range by dlgRange

    init {
        applyRange()
        dlgRange.connect(onBrothersBoundsChanged) { _, _ ->
            parentWithIndex?.let {
                if (it.index == 0) {
                    null
                } else {
                    RangeF.fromRectV(it.box.ch[it.index-1].bounds)
                }
            } ?: dlgRange.defaultValue
        }
    }

    override fun addInitialBoxes(ib: InitialBoxes) = if (initialBoxesInScript) {
        val input = if (type.hasBottom) subscript else if (type.hasTop) superscript else null
        input?.run { addBoxes(ib.selectedBoxes) }
        FinalBoxes()
    } else FinalBoxes(
        if (!ib.hasSelection || (ib.selectedBoxes.all { b -> b.isDigit() } && ib.boxesBefore.lastOrNull()?.isDigit() != true)) {
            ib.selectedBoxes
        } else {
            listOf(BracketsInputFormulaBox(ib.selectedBoxes))
        }
    )

    override fun getInitialSingle() = when (type) {
        Type.TOP, Type.BOTH -> superscript.lastSingle
        Type.BOTTOM -> subscript.lastSingle
        Type.NONE -> null
    }

    private fun deleteSup() =
        if (type == Type.BOTH) {
            superscript.clear()
            type = Type.BOTTOM
            DeletionResult(subscript.lastSingle)
        } else {
            delete()
        }

    private fun deleteSub() =
        if (type == Type.BOTH) {
            subscript.clear()
            type = Type.TOP
            DeletionResult(superscript.lastSingle)
        } else {
            delete()
        }

    // override fun onChildRequiresDelete(b: FormulaBox) =
    //     when (b) {
    //         topContainer -> {
    //             if (superscript.ch.isEmpty()) {
    //                 deleteSup()
    //             } else {
    //                 DeletionResult.fromSelection(topContainer)
    //             }
    //         }
    //         bottomContainer -> {
    //             if (subscript.ch.isEmpty()) {
    //                 deleteSub()
    //             } else {
    //                 DeletionResult.fromSelection(bottomContainer)
    //             }
    //         }
    //         else -> delete()
    //     }

    override fun deleteMultiple(boxes: List<FormulaBox>) = when (boxes) {
        listOf(topContainer) -> deleteSup()
        listOf(bottomContainer) -> deleteSub()
        else -> throw UnsupportedOperationException()
    }

    private fun applyRange() {
        phantom.customBounds = RectF(0f, range.start, 0f, range.end)
    }

    override fun toWolfram() =
        (if (type.hasBottom) "_(${bottom.toWolfram()})" else "") + (if (type.hasTop) "^(${top.toWolfram()})" else "")

    override fun toSage() =
        (if (type.hasBottom) "_(${bottom.toSage()})" else "") + (if (type.hasTop) "^(${top.toSage()})" else "")

    override fun toJson() = makeJsonObject("script") {
        addProperty("type", type.toString())
        add("superscript", superscript.toJson())
        add("subscript", subscript.toJson())
    }

    companion object {
        init {
            deserializers.add(FormulaBoxDeserializer("script") {
                val type = Type.valueOf(get("type").asString)
                ScriptFormulaBox(
                    type
                ).apply {
                    if (type.hasTop) superscript.addBoxes(getAsJsonArray("superscript").toBoxes())
                    if (type.hasBottom) subscript.addBoxes(getAsJsonArray("subscript").toBoxes())
                }
            })
        }
    }
}