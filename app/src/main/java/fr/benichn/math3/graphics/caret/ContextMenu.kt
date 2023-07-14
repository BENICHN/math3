package fr.benichn.math3.graphics.caret

import android.graphics.Color
import android.graphics.PointF
import fr.benichn.math3.graphics.Utils.Companion.with
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.types.ImmutableList
import fr.benichn.math3.types.callback.ObservableProperty

class ContextMenu(vararg entries: ContextMenuEntry) {
    private val entries = mutableListOf<ContextMenuEntry>()
    val ent = ImmutableList(this.entries)

    private val input = InputFormulaBox().also { it.background = Color.WHITE }
    val box = TransformerFormulaBox(input, BoundsTransformer.Constant(BoxTransform.scale(0.5f)) * BoundsTransformer.Align(RectPoint.BOTTOM_CENTER))

    var origin by ObservableProperty(this, PointF()) { _, e ->
        box.transformers = box.transformers.with(1, BoundsTransformer.Constant(BoxTransform(e.new)))
    }

    var index: Int = -1

    val onPictureChanged
        get() = box.onPictureChanged

    init {
        for (e in entries) {
            addEntry(e)
        }
    }

    fun addEntry(entry: ContextMenuEntry) = addEntry(entries.size, entry)
    fun addEntry(i: Int, entry: ContextMenuEntry) {
        entries.add(i, entry)
        entry.box.padding = Padding(FormulaBox.DEFAULT_TEXT_RADIUS)
        entry.box.foreground = Color.BLACK
        input.addBox(i, entry.box)
    }

    fun removeEntry(entry: ContextMenuEntry) = removeEntryAt(entries.indexOf(entry))
    fun removeEntryAt(i: Int) {
        entries.removeAt(i)
        input.removeBoxAt(i)
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
            val b = box.findBox(pos)
            entries.firstOrNull { it.box == b }
        } else {
            null
        }
}