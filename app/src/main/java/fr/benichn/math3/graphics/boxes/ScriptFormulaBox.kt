package fr.benichn.math3.graphics.boxes

import android.graphics.Path
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

    override fun addInitialBoxes(ib: InitialBoxes) =
        when (ib) {
            is InitialBoxes.Selection -> {
                val newBoxes = if (ib.boxes.size <= 1 || (ib.boxes.all { b -> b.isDigit() } && ib.boxesBefore.lastOrNull()?.isDigit() != true)) {
                    ib.boxes
                } else {
                    listOf(BracketsFormulaBox(*ib.boxes.toTypedArray()))
                }
                FinalBoxes(newBoxes)
            }
            is InitialBoxes.BeforeAfter -> FinalBoxes()
        }

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

    override fun findChildBox(absX: Float, absY: Float): FormulaBox =
        when {
            absY <= accTransform.origin.y+range.start -> if (type != Type.SUB) sup else this
            absY > accTransform.origin.y+range.end -> if (type != Type.SUPER) sub else this
            else -> this
        }

    override fun addBox(i: Int, b: FormulaBox) {
        super.addBox(i, b)
        listenChildBoundsChange(i)
    }

    fun addChildren() {
        when (type) {
            Type.SUPER -> addBox(sup)
            Type.SUB -> addBox(sub)
            Type.BOTH -> {
                addBox(sup)
                addBox(sub)
            }
        }
    }

    fun alignChildren() {
        when (type) {
            Type.SUPER -> setChildTransform(0, BoxTransform.yOffset(range.start))
            Type.SUB -> setChildTransform(0, BoxTransform.yOffset(range.end))
            Type.BOTH -> {
                setChildTransform(0, BoxTransform.yOffset(range.start))
                setChildTransform(1, BoxTransform.yOffset(range.end))
            }
        }
    }
}