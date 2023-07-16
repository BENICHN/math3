package fr.benichn.math3.graphics.boxes

import android.graphics.Path
import android.graphics.RectF
import fr.benichn.math3.graphics.Utils.Companion.with
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.PathPainting
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

    override val selectBeforeDeletion: Boolean
        get() = true

    override fun addInitialBoxes(ib: InitialBoxes) = FinalBoxes(
        if (!ib.hasSelection || (ib.selectedBoxes.all { b -> b.isDigit() } && ib.boxesBefore.lastOrNull()?.isDigit() != true)) {
            ib.selectedBoxes
        } else {
            listOf(BracketsInputFormulaBox(*ib.selectedBoxes.toTypedArray()))
        }
    )

    override fun getInitialSingle() = when (type) {
        Type.TOP, Type.BOTH -> superscript.lastSingle
        Type.BOTTOM -> subscript.lastSingle
        Type.NONE -> null
    }

    override fun generateGraphics(): FormulaGraphics = FormulaGraphics(
        Path(),
        PathPainting.Fill,
        when (type) {
            Type.NONE -> RectF(0f, -DEFAULT_TEXT_RADIUS, 0f, DEFAULT_TEXT_RADIUS)
            Type.BOTTOM -> RectF(0f, -DEFAULT_TEXT_RADIUS, bottomContainer.realBounds.right, bottomContainer.realBounds.bottom)
            Type.TOP -> RectF(0f, topContainer.realBounds.top, topContainer.realBounds.right, DEFAULT_TEXT_RADIUS)
            Type.BOTH -> RectF(0f, topContainer.realBounds.top, max(topContainer.realBounds.right, bottomContainer.realBounds.right), bottomContainer.realBounds.bottom)
        }
    )

    private fun deleteSup() =
        if (type == Type.BOTH) {
            superscript.removeAllBoxes()
            type = Type.BOTTOM
            DeletionResult(subscript.lastSingle)
        } else {
            delete()
        }

    private fun deleteSub() =
        if (type == Type.BOTH) {
            subscript.removeAllBoxes()
            type = Type.TOP
            DeletionResult(superscript.lastSingle)
        } else {
            delete()
        }

    override fun onChildRequiresDelete(b: FormulaBox) =
        when (b) {
            topContainer -> {
                if (superscript.ch.isEmpty()) {
                    deleteSup()
                } else {
                    DeletionResult.fromSelection(topContainer)
                }
            }
            bottomContainer -> {
                if (subscript.ch.isEmpty()) {
                    deleteSub()
                } else {
                    DeletionResult.fromSelection(bottomContainer)
                }
            }
            else -> delete()
        }

    override fun deleteMultiple(indices: List<Int>) = when (indices.map { ch[it] }) {
        listOf(topContainer) -> deleteSup()
        listOf(bottomContainer) -> deleteSub()
        else -> throw UnsupportedOperationException()
    }

    private fun applyRange() {
        phantom.customBounds = RectF(0f, range.start, 0f, range.end)
    }
}