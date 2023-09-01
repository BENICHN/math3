package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.types.callback.ValueChangedEvent
import kotlin.math.max
import kotlin.math.min

open class SequenceFormulaBox(updGr: Boolean = true) : FormulaBox() {
    class LineStart : PhantomFormulaBox(RectF(0f, -DEFAULT_TEXT_RADIUS, 0f, DEFAULT_TEXT_RADIUS))

    class Line(
        val boxes: List<FormulaBox>,
        val top: Float,
        val bottom: Float
    )

    data class Child(val box: FormulaBox, val ignored: Boolean) {
        companion object {
            infix fun FormulaBox.ign(ignored: Boolean) = Child(this, ignored)
        }
    }

    private val ignMap = mutableMapOf<FormulaBox, Boolean>()
    private var isInitialized = false

    init {
        super.addBoxes(0, listOf(LineStart()))
        updateLines()
        if (updGr) updateGraphics()
    }

    constructor(vararg boxes: FormulaBox, updGr: Boolean = true) : this(boxes.asList(), updGr)
    constructor(vararg children: Child, updGr: Boolean = true) : this(false) {
        addBoxes(1, *children, updGr=updGr)
    }
    constructor(boxes: List<FormulaBox>, updGr: Boolean = true) : this(false) {
        addBoxes(1, boxes, updGr=updGr)
    }

    override fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
        alignChildren()
        updateGraphics()
    }

    fun setIgnored(c: FormulaBox, ignored: Boolean) {
        ignMap[c] = ignored
    }

    val chr
        get() = ch.drop(1)

    fun addBoxes(vararg children: Child) = addBoxes(ch.size, *children)
    fun addBoxes(i: Int, vararg children: Child) = addBoxes(i, *children, updGr=true)
    protected fun addBoxes(i: Int, vararg children: Child, updGr: Boolean) {
        addBoxes(i, children.map { it.box }, updGr)
        children.forEach { (b, ig) -> setIgnored(b, ig) }
    }

    override fun addBoxes(i: Int, boxes: List<FormulaBox>) {
        addBoxes(i, boxes, true)
    }
    protected fun addBoxes(i: Int, boxes: List<FormulaBox>, updGr: Boolean) {
        super.addBoxes(i, boxes)
        alignChildren()
        if (updGr) updateGraphics()
    }

    override fun removeBoxes(boxes: List<FormulaBox>) {
        for (b in boxes) ignMap.remove(b)
        super.removeBoxes(boxes)
        alignChildren()
        updateGraphics()
    }

    fun clearBoxes() = removeBoxes(ch.drop(1))

    var lines = listOf<Line>()
        private set

    private fun updateLines() {
        val lines = mutableListOf<Line>()
        var lineBoxes = mutableListOf<FormulaBox>()
        var top = -DEFAULT_TEXT_RADIUS
        var bottom = DEFAULT_TEXT_RADIUS
        fun addLine() {
            lines.add(
                Line(
                    lineBoxes,
                    top,
                    bottom
                )
            )
            lineBoxes = mutableListOf()
            top = -DEFAULT_TEXT_RADIUS
            bottom = DEFAULT_TEXT_RADIUS
        }
        ch.forEachIndexed { i, c ->
            if (i == 0) {
                assert(c is LineStart)
                lineBoxes.add(c)
            } else {
                if (c is LineStart) {
                    addLine()
                }
                lineBoxes.add(c)
                top = min(top, c.bounds.top)
                bottom = max(bottom, c.bounds.bottom)
            }
        }
        if (lineBoxes.isNotEmpty()) addLine()
        this.lines = lines
    }

    private fun alignChildren() {
        updateLines()
        var y = 0f
        for (i in lines.indices) {
            val line = lines[i]
            var x = 0f
            for (b in line.boxes) {
                val dx = x - b.bounds.left
                x += b.bounds.width()
                setChildTransform(b, BoxTransform(PointF(dx, y)))
            }
            if (i != lines.lastIndex) {
                y += line.bottom - lines[i+1].top + LINE_SPACING
            }
        }
    }

    private fun findCell(x: Float, marks: List<Float>) =
        marks.indexOfFirst { m -> x < m }.let { i -> if (i < 0) marks.size else i }

    override fun findChildBox(pos: PointF): FormulaBox {
        if (lines.isEmpty()) return this
        var y = lines[0].top
        val yMarks = lines.map { l ->
            y += l.bottom - l.top
            y
        }.dropLast(1)
        val line = lines[findCell(pos.y, yMarks)]
        val xMarks = line.boxes.map { b -> b.realBounds.right }.dropLast(1)
        val i = findCell(pos.x, xMarks)
        val b = line.boxes[i]
        val range = if (pos.x < b.realBounds.centerX()) i downTo 0 else i until ch.size
        val k = range.firstOrNull { j ->
            !ignMap.getOrDefault(line.boxes[j], false)
        }
        return k?.let { line.boxes[it] } ?: this
    }

    companion object {
        const val LINE_SPACING = DEFAULT_TEXT_RADIUS * 0.33f
    }
}