package fr.benichn.math3.graphics.caret

import android.graphics.Color
import android.graphics.PointF
import fr.benichn.math3.graphics.Utils.Companion.with
import fr.benichn.math3.graphics.boxes.ContextMenuFormulaBox
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.SequenceFormulaBox
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.types.ImmutableList
import fr.benichn.math3.types.callback.ObservableProperty

class ContextMenu(entries: Iterable<ContextMenuEntry>, val triggers: List<FormulaBox>, val uniformWidths: Boolean = false) {
    constructor(vararg entries: ContextMenuEntry) : this(entries.asIterable(), listOf())

    private val entries = mutableListOf<ContextMenuEntry>()
    val ent = ImmutableList(this.entries)

    val fb = ContextMenuFormulaBox()
    val box = TransformerFormulaBox(fb, BoundsTransformer.Constant(BoxTransform.scale(0.5f)) * BoundsTransformer.Align(RectPoint.BOTTOM_CENTER))

    var origin by ObservableProperty(this, PointF()) { _, e ->
        box.modifyTransformers { it.with(1, BoundsTransformer.Constant(BoxTransform(e.new))) }
    }
    var source: FormulaBox? = null
    var index: Int = -1

    val onPictureChanged
        get() = box.onPictureChanged

    init {
        for (e in entries) {
            addEntry(e)
        }
        fb.uniformWidths = uniformWidths
    }

    fun addEntry(entry: ContextMenuEntry) = addEntry(entries.size, entry)
    fun addEntry(i: Int, entry: ContextMenuEntry) {
        entries.add(i, entry)
        fb.addEntryBox(i, entry.box)
    }

    fun findElement(p: PointF) =
        if (box.bounds.contains(p.x, p.y)) Element.INTERIOR
        else Element.NONE

    enum class Element {
        INTERIOR,
        NONE
    }

    fun findEntry(pos: PointF): ContextMenuEntry? =
        if (box.bounds.contains(pos.x, pos.y)) {
            val b = box.findBox(pos) as TransformerFormulaBox
            entries.firstOrNull { it.box == b.child || it.box.deepIndexOf(b) != -1 }
        } else {
            null
        }
}