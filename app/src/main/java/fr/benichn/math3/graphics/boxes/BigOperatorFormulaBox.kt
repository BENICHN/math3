package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.types.callback.ValueChangedEvent

class BigOperatorFormulaBox(
    operator: FormulaBox = FormulaBox(),
    below: FormulaBox = FormulaBox(),
    above: FormulaBox = FormulaBox()
) : FormulaBox() {
    val dlgOperator = BoxProperty(this, operator)
    var operator by dlgOperator
    val dlgBelow = BoxProperty(this, below)
    var below by dlgBelow
    val dlgAbove = BoxProperty(this, above)
    var above by dlgAbove
    private val oper = TransformerFormulaBox(operator, BoundsTransformer.Align(RectPoint.NAN_CENTER)).apply {
        padding = Padding(DEFAULT_TEXT_RADIUS * 0.25f)
    }
    private val abov = TransformerFormulaBox(above, BoundsTransformer.Constant(BoxTransform.scale(0.75f)) * BoundsTransformer.Align(RectPoint.BOTTOM_CENTER))
    private val belo = TransformerFormulaBox(below, BoundsTransformer.Constant(BoxTransform.scale(0.75f)) * BoundsTransformer.Align(RectPoint.TOP_CENTER))

    init {
        addBoxes(oper, belo, abov)
        oper.dlgChild.connectTo(dlgOperator)
        belo.dlgChild.connectTo(dlgBelow)
        abov.dlgChild.connectTo(dlgAbove)
        alignChildren()
        updateGraphics()
    }

    override fun findChildBox(pos: PointF) = when {
        pos.y < oper.bounds.top -> abov
        pos.y > oper.bounds.bottom -> belo
        else -> this
    }

    override fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
        when (b) {
            oper -> {
                alignChildren()
            }
            else -> { }
        }
        updateGraphics()
    }

    private fun alignChildren() {
        setChildTransform(1, BoxTransform.yOffset(oper.bounds.bottom))
        setChildTransform(2, BoxTransform.yOffset(oper.bounds.top))
    }
}