package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.plus
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.types.callback.ValueChangedEvent

open class TopDownFormulaBox(
    limitsPosition: LimitsPosition = LimitsPosition.CENTER,
    limitsScale: Float = 1f,
    type: Type = Type.BOTH,
    val middle: FormulaBox = FormulaBox(),
    val bottom: FormulaBox = FormulaBox(),
    val top: FormulaBox = FormulaBox()
) : FormulaBox() {
    val bottomContainer = TransformerFormulaBox(bottom)
    val topContainer = TransformerFormulaBox(top)

    val dlgLimitsScale = BoxProperty(this, limitsScale).apply {
        onChanged += { _, _ -> setTransformers() }
    }
    var limitsScale: Float by dlgLimitsScale

    val dlgLimitsPosition = BoxProperty(this, limitsPosition).apply {
        onChanged += { _, _ ->
            setTransformers()
            alignChildren()
        }
    }
    var limitsPosition: LimitsPosition by dlgLimitsPosition

    val dlgType = BoxProperty(this, type).apply {
        onChanged += { _, _ -> resetChildren() }
    }
    var type by dlgType

    init {
        setTransformers()
        resetChildren()
        updateGraphics()
    }

    override fun findChildBox(pos: PointF) = when (limitsPosition) {
        LimitsPosition.CENTER -> when {
            type.hasTop && pos.y <= middle.bounds.top -> topContainer
            type.hasBottom && pos.y >= middle.bounds.bottom -> bottomContainer
            else -> middle
        }

        LimitsPosition.LEFT -> if (pos.x <= middle.bounds.left) {
            when (type) {
                Type.NONE -> middle
                Type.BOTTOM -> bottomContainer
                Type.TOP -> topContainer
                Type.BOTH -> if (pos.y <= 0) topContainer else bottomContainer
            }
        } else {
            middle
        }
        LimitsPosition.RIGHT -> if (pos.x >= middle.bounds.right) {
            when (type) {
                Type.NONE -> middle
                Type.BOTTOM -> bottomContainer
                Type.TOP -> topContainer
                Type.BOTH -> if (pos.y <= 0) topContainer else bottomContainer
            }
        } else {
            middle
        }
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

    private fun setTransformers() {
        Log.d("fun", "setTransformers")
        when (limitsPosition) {
            LimitsPosition.CENTER -> {
                bottomContainer.transformer = BoundsTransformer.Constant(BoxTransform.scale(limitsScale)) * BoundsTransformer.Align(RectPoint.TOP_CENTER)
                topContainer.transformer = BoundsTransformer.Constant(BoxTransform.scale(limitsScale)) * BoundsTransformer.Align(RectPoint.BOTTOM_CENTER)
            }
            LimitsPosition.LEFT -> {
                bottomContainer.transformer = BoundsTransformer.Constant(BoxTransform.scale(limitsScale)) * BoundsTransformer.Align(RectPoint.TOP_RIGHT)
                topContainer.transformer = BoundsTransformer.Constant(BoxTransform.scale(limitsScale)) * BoundsTransformer.Align(RectPoint.BOTTOM_RIGHT)
            }
            LimitsPosition.RIGHT -> {
                bottomContainer.transformer = BoundsTransformer.Constant(BoxTransform.scale(limitsScale)) * BoundsTransformer.Align(RectPoint.TOP_LEFT)
                topContainer.transformer = BoundsTransformer.Constant(BoxTransform.scale(limitsScale)) * BoundsTransformer.Align(RectPoint.BOTTOM_LEFT)
            }
        }
    }

    private fun resetChildren() {
        Log.d("fun", "resetChildren")
        removeAllBoxes()
        addChildren()
        alignChildren()
    }

    private fun addChildren() {
        Log.d("fun", "addChildren")
        addBox(middle)
        if (type.hasBottom) addBox(bottomContainer)
        if (type.hasTop) addBox(topContainer)
    }

    private fun alignChildren() {
        Log.d("fun", "alignChildren")
        when (limitsPosition) {
            LimitsPosition.CENTER -> {
                if (type.hasBottom) setChildTransform(
                    bottomContainer,
                    BoxTransform.yOffset(middle.bounds.bottom)
                )
                if (type.hasTop) setChildTransform(
                    topContainer,
                    BoxTransform.yOffset(middle.bounds.top)
                )
            }
            LimitsPosition.LEFT -> {
                if (type.hasBottom) setChildTransform(
                    bottomContainer,
                    BoxTransform(RectPoint.BOTTOM_LEFT.get(middle.bounds) + PointF(0f, -DEFAULT_V_OFFSET))
                )
                if (type.hasTop) setChildTransform(
                    topContainer,
                    BoxTransform(RectPoint.TOP_LEFT.get(middle.bounds) + PointF(0f, DEFAULT_V_OFFSET))
                )
            }
            LimitsPosition.RIGHT -> {
                if (type.hasBottom) setChildTransform(
                    bottomContainer,
                    BoxTransform(RectPoint.BOTTOM_RIGHT.get(middle.bounds) + PointF(0f, -DEFAULT_V_OFFSET))
                )
                if (type.hasTop) setChildTransform(
                    topContainer,
                    BoxTransform(RectPoint.TOP_RIGHT.get(middle.bounds) + PointF(0f, DEFAULT_V_OFFSET))
                )
            }
        }
    }

    enum class Type {
        NONE {
            override val hasBottom: Boolean = false
            override val hasTop: Boolean = false
             },
        BOTTOM {
            override val hasBottom: Boolean = true
            override val hasTop: Boolean = false
               },
        TOP {
            override val hasBottom: Boolean = false
            override val hasTop: Boolean = true
            },
        BOTH {
            override val hasBottom: Boolean = true
            override val hasTop: Boolean = true
        };
        abstract val hasBottom: Boolean
        abstract val hasTop: Boolean
    }

    enum class LimitsPosition {
        CENTER,
        LEFT,
        RIGHT
    }

    companion object {
        const val DEFAULT_V_OFFSET = DEFAULT_TEXT_RADIUS * 0.5f
    }
}