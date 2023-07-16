package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.types.callback.ValueChangedEvent

class TransformerFormulaBox(child: FormulaBox = FormulaBox(), transformers: List<BoundsTransformer> = listOf()) : FormulaBox() {
    constructor(child: FormulaBox, transformer: BoundsTransformer) : this(child,  listOf(transformer))

    val dlgChild = BoxProperty(this, child).apply {
        onChanged += { _, e ->
            replaceBox(0, e.new)
        }
    }
    var child by dlgChild

    val dlgTransformers = BoxProperty(this, transformers).apply {
        onChanged += { _, _ ->
            transformChild()
        }
    }
    var transformers by dlgTransformers

    var transformer
        get() = if (transformers.isEmpty()) BoundsTransformer.Id else transformers.reduce { a, b -> a * b }
        set(value) {
            transformers = listOf(value)
        }

    init {
        addBox(child)
    }

    override fun findChildBox(pos: PointF): FormulaBox = child
    override fun getInitialSingle() = child.getInitialSingle()

    override fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
        transformChild()
        updateGraphics()
    }

    override fun addBox(i: Int, b: FormulaBox) {
        super.addBox(i, b)
        transformChild()
        updateGraphics()
    }

    private fun transformChild() {
        setChildTransform(
            0,
            transformer(child.bounds)
        )
    }
}
