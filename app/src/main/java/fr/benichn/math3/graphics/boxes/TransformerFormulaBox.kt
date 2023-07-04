package fr.benichn.math3.graphics.boxes

import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty

class TransformerFormulaBox(child: FormulaBox = FormulaBox(), transformer: BoundsTransformer = BoundsTransformer.Id) : FormulaBox() {
    val dlgChild = BoxProperty(this, child).apply {
        onChanged += { _, e ->
            removeBox(e.old)
            addBox(e.new)
        }
    }
    var child by dlgChild

    val dlgTransformer = BoxProperty(this, transformer).apply {
        onChanged += { _, _ ->
            transformChild()
        }
    }
    var transformer by dlgTransformer

    init {
        addBox(child)
    }

    override val alwaysEnter: Boolean
        get() = true
    override fun findChildBox(absX: Float, absY: Float): FormulaBox = child.findChildBox(absX, absY)
    override fun getInitialSingle() = child.getInitialSingle()

    override fun addBox(i: Int, b: FormulaBox) {
        super.addBox(i, b)
        connect(b.onBoundsChanged) { _, _ ->
            transformChild()
        }
        listenChildBoundsChange(i)
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
