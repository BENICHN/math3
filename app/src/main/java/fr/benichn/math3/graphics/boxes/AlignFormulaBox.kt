package fr.benichn.math3.graphics.boxes

import androidx.core.graphics.unaryMinus
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.boxes.types.SidedBox

class AlignFormulaBox(child: FormulaBox = FormulaBox(), rectPoint: RectPoint = RectPoint.NAN) : FormulaBox() {
    val dlgChild = BoxProperty(this, child).apply {
        onChanged += { s, e ->
            removeBox(e.old)
            addBox(e.new)
        }
    }
    var child by dlgChild

    val dlgRectPoint = BoxProperty(this, rectPoint).apply {
        onChanged += { _, _ ->
            alignChild()
        }
    }
    var rectPoint: RectPoint by dlgRectPoint

    init {
        addBox(child)
    }

    override val alwaysEnter: Boolean
        get() = true
    override fun findChildBox(absX: Float, absY: Float): FormulaBox = child.findChildBox(absX, absY)
    override fun getInitialCaretPos(): SidedBox = child.getInitialCaretPos()

    override fun addBox(i: Int, b: FormulaBox) {
        super.addBox(i, b)
        connect(b.onBoundsChanged) { s, e ->
            alignChild()
        }
        listenChildBoundsChange(i)
        alignChild()
        updateGraphics()
    }

    private fun alignChild() {
        setChildTransform(
            0,
            BoxTransform(-(rectPoint.get(child.bounds)))
        )
    }
}
