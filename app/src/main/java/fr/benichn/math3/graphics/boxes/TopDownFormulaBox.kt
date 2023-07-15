package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.types.callback.ValueChangedEvent

open class TopDownFormulaBox(
    val middle: FormulaBox = FormulaBox(),
    val bottom: FormulaBox = FormulaBox(),
    val top: FormulaBox = FormulaBox(),
    type: ScriptFormulaBox.Type = ScriptFormulaBox.Type.BOTH
) : FormulaBox() {
    val bottomContainer = TransformerFormulaBox(bottom, BoundsTransformer.Align(RectPoint.TOP_CENTER))
    val topContainer = TransformerFormulaBox(top, BoundsTransformer.Align(RectPoint.BOTTOM_CENTER))

    val dlgType = BoxProperty(this, type).apply {
        onChanged += { _, _ -> resetChildren() }
    }
    var type by dlgType

    init {
        resetChildren()
        updateGraphics()
    }

    override fun findChildBox(pos: PointF) = when {
        type != ScriptFormulaBox.Type.SUB && pos.y <= middle.bounds.top -> topContainer
        type != ScriptFormulaBox.Type.SUPER && pos.y >= middle.bounds.bottom -> bottomContainer
        else -> middle
    }

    override fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
        when (b) {
            middle -> {
                alignChildren()
            }
            else -> { }
        }
        updateGraphics()
    }

    private fun resetChildren() {
        removeAllBoxes()
        addChildren()
        alignChildren()
    }

    private fun addChildren() {
        addBox(middle)
        when (type) {
            ScriptFormulaBox.Type.SUPER -> addBox(topContainer)
            ScriptFormulaBox.Type.SUB -> addBox(bottomContainer)
            ScriptFormulaBox.Type.BOTH -> {
                addBox(bottomContainer)
                addBox(topContainer)
            }
        }
    }

    private fun alignChildren() {
        if (type != ScriptFormulaBox.Type.SUPER) setChildTransform(bottomContainer, BoxTransform.yOffset(middle.bounds.bottom))
        if (type != ScriptFormulaBox.Type.SUB) setChildTransform(topContainer, BoxTransform.yOffset(middle.bounds.top))
    }
}