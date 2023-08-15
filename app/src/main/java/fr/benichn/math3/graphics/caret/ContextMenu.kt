package fr.benichn.math3.graphics.caret

import android.graphics.PointF
import fr.benichn.math3.graphics.boxes.ContextMenuFormulaBox
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.types.ImmutableList

class ContextMenu(entries: Iterable<ContextMenuEntry>, val trigger: (PointF) -> Boolean, uniformWidths: Boolean = true) {
    constructor(vararg entries: ContextMenuEntry, trigger: (PointF) -> Boolean, uniformWidths: Boolean = true) : this(entries.asIterable(), trigger, uniformWidths)
    constructor(vararg entries: ContextMenuEntry) : this(entries.asIterable(), { false }, false)

    private val entries = mutableListOf<ContextMenuEntry>()
    val ents = ImmutableList(this.entries)

    val fb = ContextMenuFormulaBox()
    val box = TransformerFormulaBox(fb, BoundsTransformer.Constant(BoxTransform.scale(0.6f)))

    var source: FormulaBox? = null
    var index = -1
    var destroyPopup = { }

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

    fun findEntry(pos: PointF): ContextMenuEntry? =
        if (box.bounds.contains(pos.x, pos.y)) {
            val b = box.findBox(pos) as TransformerFormulaBox
            entries.firstOrNull { it.box == b.child || it.box.deepIndexOf(b) != -1 }
        } else {
            null
        }
}