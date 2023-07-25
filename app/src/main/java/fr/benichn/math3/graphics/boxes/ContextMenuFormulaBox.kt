package fr.benichn.math3.graphics.boxes

import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.PaintedPath
import fr.benichn.math3.graphics.boxes.types.Paints

class ContextMenuFormulaBox(childrenPadding: Padding = Padding(DEFAULT_TEXT_RADIUS), uniformWidths: Boolean = false, pressedItem: Int? = null) : SequenceFormulaBox() {
    val dlgChildrenPadding = BoxProperty(this, childrenPadding).apply {
        onChanged += { _, _ ->
            adjustPaddings()
        }
    }
    var childrenPadding by dlgChildrenPadding

    val dlgUniformWidths = BoxProperty(this, uniformWidths).apply {
        onChanged += { _, _ -> adjustPaddings() }
    }
    var uniformWidths by dlgUniformWidths

    val dlgPressedItem = BoxProperty(this, pressedItem)
    var pressedItem by dlgPressedItem

    init {
        updateGraphics()
    }

    override fun shouldEnterInChild(c: FormulaBox, pos: PointF) = false

    fun addEntryBox(b: FormulaBox) = addEntryBox(ch.size, b)
    fun addEntryBox(i: Int, b: FormulaBox) {
        b.setForegroundRecursive { when(it) {
            is InputFormulaBox -> Color.GRAY
            else -> Color.BLACK
        } }
        connect(b.onBoundsChanged) { _, _ -> adjustPaddings() }
        addBox(i, TransformerFormulaBox(b))
        adjustPaddings()
    }

    private fun adjustPaddings() {
        if (uniformWidths) {
            val cs = ch.map { c -> (c as TransformerFormulaBox) }
            val maxW = cs.maxOf { c -> c.child.bounds.width() }
            val paddings = cs.map { c ->
                c.child.bounds.let { b ->
                    Padding(
                        (maxW - b.width()) * 0.5f,
                        0f
                    )
                }
            }
            cs.forEachIndexed { i, c ->
                c.padding = paddings[i] + childrenPadding
            }
        } else ch.forEach { c ->
            c.padding = Padding() + childrenPadding
        }
    }

    override fun generateGraphics(): FormulaGraphics {
        val bds = super.generateGraphics().bounds
        return FormulaGraphics(
            pressedItem?.let { i ->
                val r = ch[i].realBounds
                PaintedPath(
                    Path().apply {
                        addRect(r.left, bds.top, r.right, bds.bottom, Path.Direction.CCW)
                    },
                    Paints.fill(Color.rgb(230, 230, 230))
                )
            },
            PaintedPath(
                Path().apply {
                    val dy = bds.bottom - bds.top
                    (1 until ch.size).map { i ->
                        val x = ch[i].realBounds.left
                        moveTo(x, bds.top)
                        rLineTo(0f, dy)
                    }
                },
                Paints.stroke(0.25f, Color.BLACK)
            ),
            bounds = bds,
            background = Color.WHITE
        )
    }
}