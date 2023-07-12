package fr.benichn.math3.graphics.boxes

import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.Utils.Companion.neg
import fr.benichn.math3.Utils.Companion.pos
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.RangeF
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.numpad.types.Pt
import fr.benichn.math3.types.callback.ValueChangedEvent
import kotlin.math.max
import kotlin.math.min

class GridFormulaBox(shape: Pt = Pt(1, 1)) : FormulaBox() {
    var shape: Pt = shape
        private set

    val rowsRange
        get() = 0 until shape.y
    val columnsRange
        get() = 0 until shape.x

    val rows
        get() = rowsRange.map { i ->
            columnsRange.map { j ->
                ch[i * shape.x + j]
            }
        }

    val columns
        get() = columnsRange.map { j ->
            rowsRange.map { i ->
                ch[i * shape.x + j]
            }
        }

    val inputs
        get() = ch.map { it.ch[0] as InputFormulaBox }

    operator fun get(pt: Pt) = ch[pt.y * shape.x + pt.x]
    fun getInput(pt: Pt) = inputs[pt.y * shape.x + pt.x]

    fun getIndex(i: Int) = if (i < 0) Pt(-1, -1) else Pt(i % shape.x, i / shape.x)

    fun indexOf(b: FormulaBox) = getIndex(ch.indexOf(b))

    override fun getInitialSingle() = inputs[0].lastSingle

    override fun onChildRequiresDelete(b: FormulaBox) = when(b) {
        this[Pt(0, 0)] ->
            if (inputs.all { it.ch.isEmpty() }) {
                delete()
            } else {
                delete()
            }
        else -> {
            val pt = getIndex(ch.indexOf(b)-1)
            DeletionResult(getInput(pt).lastSingle)
        }
    }

    override fun addInitialBoxes(ib: InitialBoxes): FinalBoxes {
        inputs[0].addBoxes(ib.selectedBoxes)
        return FinalBoxes()
    }

    override fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
        alignChildren()
        updateGraphics()
    }

    override fun generateGraphics(): FormulaGraphics {
        return super.generateGraphics().let {
            FormulaGraphics(
                it.path,
                it.painting,
                Padding(DEFAULT_CELL_SPACING * 0.5f).applyOnRect(it.bounds),
            )
        }
    }

    override fun findChildBox(pos: PointF): FormulaBox {
        var col = -1
        for ((i, x) in vLines.drop(1).withIndex()) {
            if (pos.x <= x) {
                col = i
                break
            }
        }
        if (col == -1) col = shape.x - 1
        var row = -1
        for ((i, y) in hLines.drop(1).withIndex()) {
            if (pos.y <= y) {
                row = i
                break
            }
        }
        if (row == -1) row = shape.y - 1
        return this[Pt(col, row)]
    }

    init {
        assert(shape.x >= 1 && shape.y >= 1)
        repeat(shape.x * shape.y) {
            addBox(TransformerFormulaBox(InputFormulaBox(), BoundsTransformer.Align(RectPoint.NAN_CENTER)))
        }
        alignChildren()
        updateGraphics()
    }

    fun addRow(i: Int) {
        repeat(shape.x) {
            addBox(i * shape.x, TransformerFormulaBox(InputFormulaBox(), BoundsTransformer.Align(RectPoint.NAN_CENTER)))
        }
        shape = Pt(shape.x, shape.y+1)
        alignChildren()
    }

    fun addColumn(i: Int) {
        for (j in shape.y downTo 1) {
            addBox(j * shape.x, TransformerFormulaBox(InputFormulaBox(), BoundsTransformer.Align(RectPoint.NAN_CENTER)))
        }
        shape = Pt(shape.x+1, shape.y)
        alignChildren()
    }

    private lateinit var hLines: List<Float>
    private lateinit var vLines: List<Float>

    private fun alignChildren() {
        val r = DEFAULT_CELL_SPACING * 0.5f
        val ws = columns.map { col -> col.maxOf { b -> b.bounds.width() } }
        val oxs = ws.scan(0f) { o, w -> o + w + 2*r }
        vLines = oxs.map { x -> x - r }
        val hs = rows.map { row -> RangeF(row.minOf { b -> -neg(b.bounds.top) }, row.maxOf { b -> pos(b.bounds.bottom) }) }
        val oys = hs.scan(0f) { o, h -> o + h.length + 2*r }
        hLines = oys.map { y -> y - r }
        val os = rowsRange.flatMap { i ->
            columnsRange.map { j ->
                val ox = oxs[j] + ws[j] * 0.5f
                val oy = oys[i] - hs[i].start
                PointF(ox, oy)
            }
        }
        os.forEachIndexed { i, p -> setChildTransform(i, BoxTransform(p)) }
    }

    companion object {
        const val DEFAULT_CELL_SPACING = DEFAULT_TEXT_WIDTH
    }
}

class MatrixFormulaBox(shape: Pt = Pt(1, 1)) : FormulaBox() {
    val grid = GridFormulaBox(shape)

    init {
        addBox(BracketsSequenceFormulaBox(TransformerFormulaBox(grid, BoundsTransformer.Align(RectPoint.CENTER))))
        updateGraphics()
    }

    override fun addInitialBoxes(ib: InitialBoxes) = grid.addInitialBoxes(ib)

    override fun getInitialSingle() = grid.getInitialSingle()

    override val selectBeforeDeletion: Boolean
        get() = true

    override fun onChildRequiresDelete(b: FormulaBox) =
        DeletionResult.fromSelection(this)
}