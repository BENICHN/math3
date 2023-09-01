package fr.benichn.math3.graphics.boxes

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.plus
import com.google.gson.JsonObject
import fr.benichn.math3.Utils.toBoxes
import fr.benichn.math3.Utils.toJsonArray
import fr.benichn.math3.graphics.Utils.sumOfRects
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaBoxDeserializer
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.PaintedPath
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.caret.ContextMenuEntry
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.types.callback.ValueChangedEvent

class RootFormulaBox(type: Type = Type.SQRT) : FormulaBox() {
    val dlgType = BoxProperty(this, type).apply {
        onChanged += { _, _ ->
            order.clear()
            removeAllBoxes()
            applyType()
        }
    }
    var type by dlgType

    val order = InputFormulaBox()
    val ord = TransformerFormulaBox(order, BoundsTransformer.Align(RectPoint.BOTTOM_RIGHT) * BoundsTransformer.Constant(BoxTransform.scale(0.75f)))
    val input = InputFormulaBox()

    init {
        applyType()
        updateGraphics()
    }

    override fun generateContextMenu() = ContextMenu(
        ContextMenuEntry.create<RootFormulaBox>(
            RootFormulaBox(Type.SQRT)
        ) {
            it.type = Type.SQRT
            null
        },
        ContextMenuEntry.create<RootFormulaBox>(
            RootFormulaBox(Type.ORDER)
        ) {
            it.type = Type.ORDER
            null
        },
        trigger = { pos ->
            val b = input.realBounds
            RectF(b.left - 4f*OFFSET, b.bottom - OFFSET * 3f, b.left - OFFSET, b.bottom + OFFSET*0.33f).contains(pos.x, pos.y)
        }
    )

    private fun applyType() {
        when (type) {
            Type.SQRT -> {
                addBoxes(input)
            }
            Type.ORDER -> {
                addBoxes(input)
                addBoxes(ord)
                alignOrder()
            }
        }
    }

    override fun findChildBox(pos: PointF): FormulaBox =
        when {
            pos.x > input.realBounds.left - OFFSET -> input
            type == Type.ORDER -> ord
            else -> this
        }

    override fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
        when (b) {
            input -> alignOrder()
        }
        updateGraphics()
    }

    private fun alignOrder() {
        if (type == Type.ORDER) {
            val p = RectPoint.BOTTOM_LEFT.get(input.realBounds) + PointF(-2*OFFSET, -3*OFFSET)
            setChildTransform(ord, BoxTransform(p))
        }
    }

    override fun getInitialSingle() = if (input.ch.size > 1 && type == Type.ORDER) order.lastSingle else input.lastSingle

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        input.addBoxes(ib.selectedBoxes)
        return FinalBoxes()
    }

    override val isFilled: Boolean
        get() = false

    override fun getFinalBoxes() = FinalBoxes(input.chr)

    override fun onChildRequiresDelete(
        b: FormulaBox,
        vararg anticipation: FormulaBox
    ): DeletionResult = when (b) {
        ord -> deleteChildrenIfNotFilled(ord) {
            type = Type.SQRT
            DeletionResult(input.lastSingle)
        }
        input -> delete().withFinalBoxes(this)
        else -> delete()
    }

    override fun deleteMultiple(boxes: List<FormulaBox>) = when(boxes) {
        listOf(ord) -> {
            type = Type.SQRT
            DeletionResult(input.lastSingle)
        }
        else -> super.deleteMultiple(boxes)
    }

    override fun generateGraphics(): FormulaGraphics {
        val b = input.realBounds
        val path = Path().apply {
            val p1 = RectPoint.TOP_RIGHT.get(b) + PointF(OFFSET, -OFFSET)
            val p2 = RectPoint.TOP_LEFT.get(b) + PointF(-OFFSET, -OFFSET)
            val p3 = RectPoint.BOTTOM_LEFT.get(b) + PointF(-OFFSET * 1.5f, OFFSET * 0.33f)
            val p4 = p3 + PointF(-OFFSET, -OFFSET * 3)
            val p5 = p4 + PointF(-OFFSET*0.5f, OFFSET*0.5f)
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            lineTo(p4.x, p4.y)
            lineTo(p5.x, p5.y)
        }
        val bds = Padding(OFFSET*3f, OFFSET, OFFSET, OFFSET*0.33f).applyOnRect(b)
        val bounds = if (type == Type.SQRT) bds else sumOfRects(bds, ord.realBounds)
        return FormulaGraphics(
            PaintedPath(
                path,
                Paints.stroke(DEFAULT_LINE_WIDTH)
            ),
            bounds = bounds
        )
    }

    enum class Type {
        SQRT,
        ORDER
    }

    override fun toWolfram() = when(type) {
        Type.SQRT -> "Sqrt[${input.toWolfram()}]"
        Type.ORDER -> "(${input.toWolfram()})^(1/(${order.toWolfram()}))"
    }

    override fun toSage() = when(type) {
        Type.SQRT -> "sqrt(${input.toSage()})"
        Type.ORDER -> "(${input.toSage()})^(1/(${order.toSage()}))"
    }

    override fun toJson() = makeJsonObject("root") {
        addProperty("type", type.toString())
        add("input", input.toJson())
        if (type == Type.ORDER) add("order", order.toJson())
    }

    companion object {
        const val OFFSET = DEFAULT_TEXT_RADIUS * 0.33f
        init {
            deserializers.add(FormulaBoxDeserializer("root") {
                val type = Type.valueOf(get("type").asString)
                RootFormulaBox(
                    type
                ).apply {
                    input.addBoxes(getAsJsonArray("input").toBoxes())
                    if (type == Type.ORDER) order.addBoxes(getAsJsonArray("order").toBoxes())
                }
            })
        }
    }
}