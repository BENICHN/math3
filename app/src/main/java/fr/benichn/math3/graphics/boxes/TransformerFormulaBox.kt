package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.types.callback.ValueChangedEvent

class TransformerFormulaBox(child: FormulaBox = FormulaBox(), transformers: List<BoundsTransformer> = listOf()) : FormulaBox() {
    constructor(child: FormulaBox = FormulaBox(), vararg transformers: BoundsTransformer) : this(child,  transformers.asList())
    constructor(child: FormulaBox = FormulaBox(), transformer: BoundsTransformer) : this(child,  listOf(transformer))

    val dlgChild = BoxProperty(this, child).apply {
        onChanged += { _, e ->
            removeAllBoxes()
            addBoxes(e.new)
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
        get() = if (transformers.isEmpty()) BoundsTransformer.id else transformers.reduce { a, b -> a * b }
        set(value) {
            transformers = listOf(value)
        }

    fun modifyTransformers(f: (List<BoundsTransformer>) -> List<BoundsTransformer>) {
        transformers = f(transformers)
    }

    init {
        addBoxes(child)
    }

    override fun findChildBox(pos: PointF): FormulaBox = child
    override fun getInitialSingle() = child.getInitialSingle()

    override fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
        transformChild()
        updateGraphics()
    }

    override fun addBoxes(i: Int, boxes: List<FormulaBox>) {
        assert(boxes.size == 1)
        super.addBoxes(i, boxes)
        transformChild()
        updateGraphics()
    }

    private fun transformChild() {
        setChildTransform(
            child,
            transformer(child.bounds)
        )
    }
}
