package fr.benichn.math3.graphics.boxes

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.Utils.toJsonArray
import fr.benichn.math3.graphics.Utils.peek
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.PaintedPath
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.types.CellMode
import kotlin.math.abs

class InputFormulaBox(boxes: List<FormulaBox>, isVisible: Boolean = true) : SequenceFormulaBox(boxes,false) {
    val firstSingle
        get() = CaretPosition.Single(this, 0)
    val lastSingle
        get() = CaretPosition.Single(this, ch.lastIndex)

    constructor(vararg boxes: FormulaBox, isVisible: Boolean = true) : this(boxes.asList(), isVisible)

    private val dlgIsVisible = BoxProperty(this, isVisible)
    var isVisible by dlgIsVisible

    init {
        updateGraphics()
    }

    override fun onChildRequiresDelete(b: FormulaBox, vararg anticipation: FormulaBox) =
        if (anticipation.isEmpty()) {
            val i = ch.indexOf(b)
            if (i == 0) delete()
            else {
                removeBoxes(b)
                val s = CaretPosition.Single(this, i-1)
                DeletionResult(s, true)
            }
        } else {
            DeletionResult.fromSelection(*anticipation)
        }

    override val isFilled: Boolean
        get() = ch.size > 1

    override fun clear() {
        clearBoxes()
        super.clear()
    }

    override fun shouldEnterInChild(c: FormulaBox, pos: PointF) =
        c.realBounds.run {
            pos.x in left..right &&
            !(pos.y in -DEFAULT_TEXT_RADIUS .. DEFAULT_TEXT_RADIUS && (abs(left - pos.x) < SEP_RADIUS || abs(right - pos.x) < SEP_RADIUS))
        }

    fun addFinalBoxes(i: Int, fb: FinalBoxes) : CaretPosition {
        addBoxesAfter(i, fb.boxesBefore + fb.boxesAfter)
        val j = i + fb.boxesBefore.size
        return when {
            fb.selectBoxesAfter || fb.selectBoxesBefore ->
                CaretPosition.Double(
                    this,
                    if (fb.selectBoxesBefore) j-fb.boxesBefore.size else j,
                    if (fb.selectBoxesAfter) j+fb.boxesBefore.size else j
                )
            else -> CaretPosition.Single(this, j)
        }
    }

    override fun generateGraphics() = if (isVisible && ch.size == 1) {
        val rx = DEFAULT_TEXT_WIDTH * 0.25f
        val ry = DEFAULT_TEXT_RADIUS * 0.5f
        val bds = RectF(0f, -DEFAULT_TEXT_RADIUS, DEFAULT_TEXT_WIDTH, DEFAULT_TEXT_RADIUS)
        val path = Path()
        path.moveTo(bds.left + rx, bds.top)
        path.rLineTo(-rx, 0f)
        path.rLineTo(0f, ry)
        path.moveTo(bds.left + rx, bds.bottom)
        path.rLineTo(-rx, 0f)
        path.rLineTo(0f, -ry)
        path.moveTo(bds.right - rx, bds.top)
        path.rLineTo(rx, 0f)
        path.rLineTo(0f, ry)
        path.moveTo(bds.right - rx, bds.bottom)
        path.rLineTo(rx, 0f)
        path.rLineTo(0f, -ry)
        FormulaGraphics(
            PaintedPath(
                path,
                Paints.stroke(DEFAULT_LINE_WIDTH)
            ),
            bounds = Padding(bds.right * 0.2f, 0f).applyOnRect(bds)
        )
    } else super.generateGraphics()

    // override fun toWolfram(mode) = ch.joinToString("") {
    //     if (it.isVariable()) " ${it.toWolfram(mode)} "
    //     else it.toWolfram(mode)
    // }

    open class TaggedBox(
        val wolframString: String
    ) {
        class Osef(wolframString: String) : TaggedBox(wolframString)
        class Number(wolframString: String) : TaggedBox(wolframString)
        class Variable(wolframString: String) : TaggedBox(wolframString)
    }

    override fun toWolfram(mode: Int): String {
        val results = mutableListOf<TaggedBox>()
        val iter = ch.listIterator().apply { next() }
        fun addDigits() {
            var res = ""
            var isDec = false
            while (iter.hasNext()) {
                val b = iter.next()
                when {
                    b.hasChar { it.isDigit() } -> res += b.toWolfram(mode)
                    b.hasText { it == "." } -> {
                        if (isDec) {
                            iter.previous()
                            break
                        } else {
                            isDec = true
                            res += b.toWolfram(mode)
                        }
                    }
                    else -> {
                        iter.previous()
                        break
                    }
                }
            }
            if (res == ".") {
                iter.previous()
                results.add(TaggedBox.Osef(res))
            }
            results.add(TaggedBox.Number(res))
        }
        fun addVariable() {
            var res = ""
            while (iter.hasNext()) {
                val b = iter.next()
                when {
                    b.isVariable() -> {
                        res += b.toWolfram(mode)
                        if (mode and CellMode.ONE_LETTER_VAR != 0) break
                    }
                    res.isNotEmpty() && b.hasText { it.all { c -> c.isLetterOrDigit() } } -> {
                        res += b.toWolfram(mode)
                    }
                    else -> {
                        iter.previous()
                        break
                    }
                }
            }
            if (iter.hasNext()) {
                val b = iter.next()
                if (b is ScriptFormulaBox && b.type.hasBottom) {
                    res = "Subscript[$res, ${b.subscript.toWolfram(mode)}]"
                    results.add(TaggedBox.Variable(res))
                    if (b.type.hasTop) results.add(TaggedBox.Osef("^(${b.superscript.toWolfram(mode)})"))
                }
                else {
                    iter.previous()
                    results.add(TaggedBox.Variable(res))
                }
            } else {
                results.add(TaggedBox.Variable(res))
            }
        }
        while (iter.hasNext()) {
            val b = iter.peek()
            when {
                b is LineStart || b.hasText { text -> text.all { it.isWhitespace() } } -> {
                    iter.next()
                    continue
                }
                b is TextFormulaBox ->
                    if (b.text.isNotEmpty()) {
                        val c0 = b.text[0]
                        when {
                            c0.isDigit() || c0 == '.' -> {
                                addDigits()
                                continue
                            }
                            c0.isLetter() -> {
                                addVariable()
                                continue
                            }
                            else -> null
                        }
                    } else null
                else -> null
            } ?: results.add(TaggedBox.Osef(iter.next().toWolfram(mode)))
        }
        var res = ""
        val resIter = results.listIterator()
        while (resIter.hasNext()) {
            val tb = resIter.next()
            res += tb.wolframString
            if (tb is TaggedBox.Variable && resIter.hasNext() && resIter.peek().let { it !is TaggedBox.Osef }) {
                res += " "
            }
        }
        return res
    }

    override fun toJson() = chr.map { it.toJson() }.toJsonArray()

    companion object {
        val binaryOperators = listOf( "+", "-", "×", "*", "^" )
        val specialCharacters = listOf('ⅇ', 'ⅈ', 'ℼ')
        const val SEP_RADIUS = DEFAULT_TEXT_WIDTH * 0.5f
    }
}
