package fr.benichn.math3.graphics.boxes

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.PathPainting
import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.types.RectPoint
import kotlin.math.max

class ScriptFormulaBox(type: Type = Type.SUPER, range: RangeF = RangeF(-DEFAULT_TEXT_RADIUS, DEFAULT_TEXT_RADIUS)) : FormulaBox() {
    val superscript = InputFormulaBox()
    val subscript = InputFormulaBox()
    private val sup = TransformerFormulaBox(superscript,
        BoundsTransformer.Align(RectPoint.BOTTOM_LEFT) *
        BoundsTransformer.Constant(BoxTransform.scale(0.75f)
    ))
    private val sub = TransformerFormulaBox(subscript,
        BoundsTransformer.Align(RectPoint.TOP_LEFT) *
        BoundsTransformer.Constant(BoxTransform.scale(0.75f)))

    enum class Type {
        SUPER,
        SUB,
        BOTH
    }

    val dlgType = BoxProperty(this, type).apply {
        onChanged += { _, e ->
            removeAllBoxes()
            addChildren()
            alignChildren()
        }
    }
    var type by dlgType

    val dlgRange = BoxProperty(this, range).apply {
        onChanged += { _, _ -> alignChildren() }
    }
    var range by dlgRange

    init {
        addChildren()
        alignChildren()
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
        Type.SUPER, Type.BOTH -> superscript.lastSingle
        Type.SUB -> subscript.lastSingle
    }

    override fun generateGraphics(): FormulaGraphics = FormulaGraphics(
        Path(),
        PathPainting.Fill,
        when (type) {
            Type.SUPER -> RectF(0f, sup.realBounds.top, sup.realBounds.right, DEFAULT_TEXT_RADIUS)
            Type.SUB -> RectF(0f, -DEFAULT_TEXT_RADIUS, sub.realBounds.right, sub.realBounds.bottom)
            Type.BOTH -> RectF(0f, sup.realBounds.top, max(sup.realBounds.right, sub.realBounds.right), sub.realBounds.bottom)
        }
    )

    private fun deleteSup() =
        if (type == Type.BOTH) {
            superscript.removeAllBoxes()
            type = Type.SUB
            DeletionResult(subscript.lastSingle)
        } else {
            delete()
        }

    private fun deleteSub() =
        if (type == Type.BOTH) {
            subscript.removeAllBoxes()
            type = Type.SUPER
            DeletionResult(superscript.lastSingle)
        } else {
            delete()
        }

    override fun onChildRequiresDelete(b: FormulaBox) =
        when (b) {
            sup -> {
                if (superscript.ch.isEmpty()) {
                    deleteSup()
                } else {
                    DeletionResult.fromSelection(sup)
                }
            }
            sub -> {
                if (subscript.ch.isEmpty()) {
                    deleteSub()
                } else {
                    DeletionResult.fromSelection(sub)
                }
            }
            else -> delete()
        }

    override fun findChildBox(pos: PointF): FormulaBox {
        val m = (range.start + range.end) * 0.5f
        return when {
            pos.y <= m -> if (type != Type.SUB) sup else sub
            else -> if (type != Type.SUPER) sub else sup
        }
    }

    override fun deleteMultiple(indices: List<Int>) = when (indices.map { ch[it] }) {
        listOf(sup) -> deleteSup()
        listOf(sub) -> deleteSub()
        else -> throw UnsupportedOperationException()
    }

    private fun addChildren() {
        when (type) {
            Type.SUPER -> addBox(sup)
            Type.SUB -> addBox(sub)
            Type.BOTH -> {
                addBox(sup)
                addBox(sub)
            }
        }
    }

    private fun alignChildren() {
        when (type) {
            Type.SUPER -> setChildTransform(0, BoxTransform.yOffset(range.start+ DEFAULT_V_OFFSET))
            Type.SUB -> setChildTransform(0, BoxTransform.yOffset(range.end- DEFAULT_V_OFFSET))
            Type.BOTH -> {
                setChildTransform(0, BoxTransform.yOffset(range.start+ DEFAULT_V_OFFSET))
                setChildTransform(1, BoxTransform.yOffset(range.end- DEFAULT_V_OFFSET))
            }
        }
    }

    companion object {
        const val DEFAULT_V_OFFSET = DEFAULT_TEXT_RADIUS * 0.5f
    }
}