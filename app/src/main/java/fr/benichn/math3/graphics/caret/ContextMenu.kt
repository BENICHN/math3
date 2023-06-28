package fr.benichn.math3.graphics.caret

import android.graphics.PointF
import fr.benichn.math3.graphics.boxes.AlignFormulaBox
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.types.ImmutableList

data class ContextMenuEntry(
    val box: FormulaBox,
    val action: (Any) -> Unit
) {
    companion object {
        inline fun <reified T> create(box: FormulaBox, crossinline action: (T) -> Unit) =
            ContextMenuEntry(box) {
                if (it is T) {
                    action(it)
                }
            }
    }
}

class ContextMenu(vararg entries: ContextMenuEntry) {
    private val entries = mutableListOf<ContextMenuEntry>()
    val ent = ImmutableList(this.entries)

    private val input = InputFormulaBox()
    val box = AlignFormulaBox(input, RectPoint.BOTTOM_CENTER)
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
        input.addBox(i, entry.box)
    }

    fun removeEntry(entry: ContextMenuEntry) = removeEntryAt(entries.indexOf(entry))
    fun removeEntryAt(i: Int) {
        entries.removeAt(i)
        input.removeBoxAt(i)
    }

    fun findEntry(p: PointF) = findEntry(p.x, p.y)
    fun findEntry(x: Float, y: Float): ContextMenuEntry? =
        if (box.bounds.contains(x, y)) {
            val sb = box.findBox(x, y)
            entries.firstOrNull { it.box == sb.box }
        } else {
            null
        }
}