package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.plus
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.types.callback.ValueChangedEvent
import kotlin.math.max
import kotlin.math.min

open class TopDownFormulaBox(
    limitsPosition: LimitsPosition = LimitsPosition.CENTER,
    limitsScale: Float = 1f,
    type: Type = Type.BOTH,
    val middle: FormulaBox = FormulaBox(),
    val bottom: FormulaBox = FormulaBox(),
    val top: FormulaBox = FormulaBox(),
    updGr: Boolean = true
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

    var allowedTypes: List<Type> = listOf(type)

    init {
        setTransformers()
        resetChildren()
        if (updGr) updateGraphics()
    }

    override fun onChildRequiresDelete(b: FormulaBox, vararg anticipation: FormulaBox) = when (b) {
        bottomContainer -> {
            if (type.hasTop) {
                if (Type.TOP in allowedTypes) {
                    doOnChildrenIfNotFilled(bottomContainer) {
                        type = Type.TOP
                        DeletionResult(top.getInitialSingle())
                    }
                } else if (Type.NONE in allowedTypes) {
                    doOnChildrenIfNotFilled(bottomContainer, topContainer) {
                        type = Type.NONE
                        DeletionResult.fromSingle(this)
                    }
                } else {
                    deleteIfNotFilled()
                }
            } else {
                if (Type.NONE in allowedTypes) {
                    doOnChildrenIfNotFilled(bottomContainer) {
                        type = Type.NONE
                        DeletionResult.fromSingle(this)
                    }
                } else {
                    deleteIfNotFilled()
                }
            }
        }
        topContainer -> {
            if (type.hasBottom) {
                if (Type.BOTTOM in allowedTypes) {
                    doOnChildrenIfNotFilled(topContainer) {
                        type = Type.BOTTOM
                        DeletionResult(bottom.getInitialSingle())
                    }
                } else if (Type.NONE in allowedTypes) {
                    doOnChildrenIfNotFilled(bottomContainer, topContainer) {
                        type = Type.NONE
                        DeletionResult.fromSingle(this)
                    }
                } else {
                    deleteIfNotFilled()
                }
            } else {
                if (Type.NONE in allowedTypes) {
                    doOnChildrenIfNotFilled(topContainer) {
                        type = Type.NONE
                        DeletionResult.fromSingle(this)
                    }
                } else {
                    deleteIfNotFilled()
                }
            }
        }
        else -> deleteIfNotFilled()
    }

    override fun deleteMultiple(boxes: List<FormulaBox>) =
        when (boxes) {
            listOf(topContainer) -> {
                if (type == Type.BOTH) {
                    type = Type.BOTTOM
                    DeletionResult(bottom.getInitialSingle())
                } else {
                    type = Type.NONE
                    DeletionResult.fromSingle(this)
                }
            }
            listOf(bottomContainer) -> {
                if (type == Type.BOTH) {
                    type = Type.TOP
                    DeletionResult(top.getInitialSingle())
                } else {
                    type = Type.NONE
                    DeletionResult.fromSingle(this)
                }
            }
            listOf(bottomContainer, topContainer) -> {
                type = Type.NONE
                DeletionResult.fromSingle(this)
            }
            else -> DeletionResult()
        }

    override fun generateGraphics() = super.generateGraphics().withBounds { r ->
        RectF(r.left, min(r.top, -DEFAULT_TEXT_RADIUS), r.right, max(r.bottom, DEFAULT_TEXT_RADIUS))
    }

    override fun findChildBox(pos: PointF) =
        when {
            pos.y <= 0 -> if (type.hasTop) topContainer else if (type.hasBottom) bottomContainer else this
            else -> if (type.hasBottom) bottomContainer else if (type.hasTop) topContainer else this
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
        if (!type.hasTop) top.clear()
        if (!type.hasBottom) bottom.clear()
        removeAllBoxes()
        addChildren()
        alignChildren()
    }

    private fun addChildren() {
        addBoxes(middle)
        if (type.hasBottom) addBoxes(bottomContainer)
        if (type.hasTop) addBoxes(topContainer)
    }

    private fun alignChildren() {
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