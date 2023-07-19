package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.SequenceChild.Companion.ign
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.types.Side
import fr.benichn.math3.types.callback.ValueChangedEvent

data class SequenceChild(val box: FormulaBox, val ignored: Boolean) {
    companion object {
        infix fun FormulaBox.ign(ignored: Boolean) = SequenceChild(this, ignored)
    }
}

open class SequenceFormulaBox() : FormulaBox() {
    constructor(vararg boxes: FormulaBox) : this() {
        addBoxes(*boxes)
    }
    constructor(vararg children: SequenceChild) : this() {
        for ((b, ignored) in children) {
            addBox(b, ignored)
        }
    }

    private val ignMap = mutableMapOf<FormulaBox, Boolean>()

    init {
        updateGraphics()
    }

    override fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
        val j = ch.indexOf(b)
        offsetFrom(j, e.old.left - e.new.left)
        offsetFrom(j + 1, e.new.right - e.old.right)
        updateGraphics()
    }

    fun setIgnored(c: FormulaBox, ignored: Boolean) {
        ignMap[c] = ignored
    }

    fun addBox(b: FormulaBox, ignored: Boolean) {
        addBox(b)
        setIgnored(b, ignored)
    }

    fun addBox(i: Int, b: FormulaBox, ignored: Boolean) {
        addBox(i, b)
        setIgnored(b, ignored)
    }

    final override fun addBox(i: Int, b: FormulaBox) {
        super.addBox(i, b)
        setChildTransform(
            i,
            BoxTransform.xOffset((if (i == 0) 0f else ch[i - 1].run { transform.origin.x + bounds.right }) - b.bounds.left)
        )
        offsetFrom(i + 1, b.bounds.width())
        updateGraphics()
    }

    final override fun removeBoxAt(i: Int) {
        val b = ch[i]
        ignMap.remove(b)
        super.removeBoxAt(i)
        offsetFrom(i, -b.bounds.width())
        updateGraphics()
    }

    private fun offsetFrom(i: Int, l: Float) {
        for (j in i until ch.size) {
            modifyChildTransform(j) { it * BoxTransform.xOffset(l) }
        }
    }

    override fun findChildBox(pos: PointF): FormulaBox {
        if (ch.isEmpty()) return this
        var i = -1
        while (i < ch.size-1) {
            i++
            val c = ch[i]
            if (pos.x <= c.realBounds.right) {
                break
            }
        }
        val b = ch[i]
        val s = if (pos.x < b.realBounds.centerX()) Side.L else Side.R
        val range = when (s) {
            Side.L -> i downTo 0
            Side.R -> i until ch.size
        }
        val k = range.firstOrNull { j ->
            !ignMap.getOrDefault(ch[j], false)
        }
        return k?.let { ch[it] } ?: this
    }
}