package fr.benichn.math3.graphics.caret

import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.minus
import fr.benichn.math3.graphics.Utils
import fr.benichn.math3.graphics.Utils.leftBar
import fr.benichn.math3.graphics.Utils.rightBar
import fr.benichn.math3.graphics.Utils.sumOfRects
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.GridFormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.SequenceFormulaBox
import fr.benichn.math3.graphics.boxes.types.ParentInput
import fr.benichn.math3.graphics.boxes.types.ParentWithIndex
import fr.benichn.math3.graphics.boxes.types.PtsRange
import fr.benichn.math3.graphics.boxes.types.Range
import kotlin.math.max
import kotlin.math.min

sealed class CaretPosition {
    open fun isValid(root: FormulaBox) =
        selectedBoxes.all { b -> b.root == root }

    abstract val selectedBoxes: List<FormulaBox>
    fun contains(b: FormulaBox) =
        selectedBoxes.any { sb ->
            sb == b || sb.deepIndexOf(b) != -1
        }

    abstract fun withoutModif(): CaretPosition
    open val absPos: PointF? = null

    data class Single(val box: FormulaBox, override val absPos: PointF? = null) : CaretPosition() {
        constructor(input: InputFormulaBox, index: Int, absPos: PointF? = null) : this(input.ch[index], absPos)

        val nextSingle
            get() = parentInput.let { (inp, i) -> inp.findNextSingleAfter(inp.ch[i]) }
        val previousSingle
            get() = parentInput.let { (inp, i) -> inp.findPreviousSingleBefore(if (i == inp.ch.lastIndex) null else inp.ch[i+1]) }

        val barRect
            get() = parentInput.let { (inp, _) ->
                if (inp.ch.size == 1) {
                    inp.accRealBounds.run {
                        val x = centerX()
                        RectF(x, top, x, bottom)
                    }
                } else box.accRealBounds.rightBar()
            }

        override val selectedBoxes: List<FormulaBox>
            get() = listOf()

        override fun isValid(root: FormulaBox) =
            box.root == root && box.parent is InputFormulaBox

        val parentInput
            get() = box.parentWithIndex!!.let { (par, i) -> ParentInput(par as InputFormulaBox, i) }

        fun getElement(absPos: PointF) =
            if (BoxCaret.singleBarPadding.applyOnRect(barRect).contains(absPos.x, absPos.y)) {
                Element.BAR
            } else {
                Element.NONE
            }

        enum class Element {
            BAR,
            NONE
        }

        fun withModif(absPos: PointF) = Single(box, absPos)
        override fun withoutModif() = Single(box)

        companion object {
            fun getParentInput(b: FormulaBox): ParentInput? {
                val pi = b.parentWithIndex
                return pi?.let { if (it.box is InputFormulaBox) ParentInput(it.box, it.index) else getParentInput(it.box) }
            }
            fun fromBox(box: FormulaBox) =
                getParentInput(box)?.let { (inp, i) -> Single(inp, i) }
            fun fromBox(box: FormulaBox, absPos: PointF) =
                getParentInput(box)?.let { (inp, i) ->
                    val j = inp.ch[i].let { b -> if (b !is SequenceFormulaBox.LineStart && absPos.x < b.accRealBounds.centerX()) max(0, i-1) else i }
                    Single(inp, j)
                }
        }
    }

    data class Double(val input: InputFormulaBox, val startIndex: Int, val endIndex: Int, override val absPos: PointF? = null, val fixedAbsPos: PointF? = null) : CaretPosition() {
        override val selectedBoxes = input.ch.subList(startIndex+1, endIndex+1).toList()
        override fun isValid(root: FormulaBox) =
            input.root == root && input.ch.filterIndexed { i, _ -> startIndex < i && i <= endIndex } == selectedBoxes

        val nextSingle
            get() = rightSingle.nextSingle
        val previousSingle
            get() = leftSingle.previousSingle

        val bounds
            get() = sumOfRects(selectedBoxes.map { it.accRealBounds })

        val rects: List<RectF>
            get() {
                val res = mutableListOf<RectF>()
                var n = 0
                for (l in input.lines) {
                    val s = min(l.boxes.lastIndex, max(-1, startIndex - n))
                    val e = min(l.boxes.lastIndex, endIndex - n)
                    if (s < e) res.add(sumOfRects(l.boxes.subList(s+1, e+1).map { b -> b.accRealBounds }))
                    n += l.boxes.size
                    if (n > endIndex) break
                }
                return res
            }

        val leftSingle
            get() = Single(input, startIndex)
        val rightSingle
            get() = Single(input, endIndex)

        fun getElement(absPos: PointF) =
            if (selectedBoxes.isEmpty()) Element.NONE
            else rects.let { rs ->
                if (BoxCaret.selectionBarPadding.applyOnRect(rs.first().leftBar()).contains(absPos.x, absPos.y)) {
                    Element.LEFT_BAR
                } else if (BoxCaret.selectionBarPadding.applyOnRect(rs.last().rightBar()).contains(absPos.x, absPos.y)) {
                    Element.RIGHT_BAR
                } else if (rects.any { r -> r.contains(absPos.x, absPos.y) }) {
                    Element.INTERIOR
                } else {
                    Element.NONE
                }
            }

        enum class Element {
            LEFT_BAR,
            RIGHT_BAR,
            INTERIOR,
            NONE
        }

        fun withModif(absPos: PointF? = null, fixedAbsPos: PointF? = null) = Double(input, startIndex, endIndex, absPos, fixedAbsPos)
        override fun withoutModif() = Double(input, startIndex, endIndex)

        companion object {
            fun mergeSelections(s1: Double?, s2: Double?): Double? {
                if (s1 == null || s2 == null) return null
                val s1Sequences = getBoxInputs(s1.input)
                val s2Sequences = getBoxInputs(s2.input)
                val commonParent = s1Sequences.zip(s2Sequences).lastOrNull { (p1, p2) -> p1.box == p2.box }

                fun retrieveRange(s: Double, p: ParentWithIndex) : Range =
                    if (p.box == s.input) {
                        Range(s.startIndex, s.endIndex)
                    } else {
                        Range(p.index-1, p.index)
                    }

                return commonParent?.let { (p1, p2) ->
                    val box = p1.box as InputFormulaBox
                    val r1 = retrieveRange(s1, p1)
                    val r2 = retrieveRange(s2, p2)
                    val r = Range.sum(r1, r2)
                    Double(box, r.start, r.end)
                }
            }

            private fun getBoxInputs(b: FormulaBox) =
                b.parentsAndThis.filter { it.box is InputFormulaBox }

            fun fromBox(b: FormulaBox): Double? =
                Single.getParentInput(b)?.let { (inp, i) -> Double(inp, i-1, i) }

            fun fromBoxes(bs: List<FormulaBox>): Double? =
                if (bs.isEmpty()) {
                    null
                } else {
                    bs.map { b -> fromBox(b) }.reduce { acc, d -> mergeSelections(acc, d) }
                }

            fun fromSingles(p1: Single, p2: Single): Double? {
                val s1 = fromSingle(p1)
                val s2 = fromSingle(p2)
                return mergeSelections(s1, s2)
            }

            fun fromSingle(p: Single) = p.parentInput.let { pi ->
                Double(pi.input, pi.index, pi.index)
            }
        }
    }

    class DiscreteSelection(val box: FormulaBox, val indices: List<Int>) : CaretPosition() {
        override val selectedBoxes = indices.map { i -> box.ch[i] }

        override fun isValid(root: FormulaBox) =
            box.root == root && indices.map { i -> if (i < box.ch.size) box.ch[i] else null } == selectedBoxes

        val rects
            get() = selectedBoxes.map { it.accRealBounds }

        fun getElement(absPos: PointF) =
            if (rects.any { it.contains(absPos.x, absPos.y) }) Element.INTERIOR else Element.NONE

        fun callDelete() = box.deleteMultiple(selectedBoxes)
        override fun withoutModif() = this

        enum class Element {
            INTERIOR,
            NONE
        }

        companion object {
            fun fromBox(b: FormulaBox) = b.parentWithIndex?.let { DiscreteSelection(it.box, listOf(it.index)) }
            fun fromBoxes(vararg boxes: FormulaBox) = if (boxes.isNotEmpty()) {
                boxes[0].parent?.let { par ->
                    DiscreteSelection(par, boxes.map {
                        it.parentWithIndex!!.let { (p, i) ->
                            if (par != p) {
                                return null
                            }
                            i
                        }
                    })
                }
            } else null
        }
    }

    data class GridSelection(val box: GridFormulaBox, val ptsRange: PtsRange, override val absPos: PointF? = null, val fixedAbsPos: PointF? = null) : CaretPosition() {
        override val selectedBoxes
            get() = ptsRange.map { pt -> box[pt] }
        val selectedInputs = ptsRange.map { pt -> box.getInput(pt) }
        val bounds
            get() = sumOfRects(selectedBoxes.map { it.accRealBounds })

        override fun isValid(root: FormulaBox) =
            box.root == root && ptsRange.map { pt -> box.getInputOrNull(pt) } == selectedInputs

        val topLeftSingle
            get() = box.getInput(ptsRange.tl).firstSingle
        val topRightSingle
            get() = box.getInput(ptsRange.tr).lastSingle
        val bottomLeftSingle
            get() = box.getInput(ptsRange.bl).firstSingle
        val bottomRightSingle
            get() = box.getInput(ptsRange.br).lastSingle

        fun getElement(absPos: PointF): Element = bounds.let {
            val cs = Utils.corners(it)
            val i = cs.indexOfFirst { c ->
                Utils.l2(absPos - c) <= BoxCaret.BALL_MAX_TOUCH_DIST_SQ + BoxCaret.BALL_RADIUS * BoxCaret.BALL_RADIUS
            }
            return when (i) {
                0 -> Element.CORNER_TL
                1 -> Element.CORNER_TR
                2 -> Element.CORNER_BR
                3 -> Element.CORNER_BL
                else -> {
                    if (it.contains(absPos.x, absPos.y)) {
                        Element.INTERIOR
                    } else {
                        Element.NONE
                    }
                }
            }
        }

        fun withModif(absPos: PointF? = null, fixedAbsPos: PointF? = null) = GridSelection(box, ptsRange, absPos, fixedAbsPos)
        override fun withoutModif() = GridSelection(box, ptsRange)

        enum class Element {
            CORNER_TL,
            CORNER_TR,
            CORNER_BR,
            CORNER_BL,
            INTERIOR,
            NONE
        }

        companion object {
            fun fromBoxes(b1: FormulaBox, b2: FormulaBox): GridSelection? = FormulaBox.commonParent(b1, b2)?.let {
                val (p, ixs) = it
                if (p is GridFormulaBox) {
                    val pts = ixs.map { i -> p.getIndex(i) }
                    val pr = PtsRange.fromPts(pts)
                    GridSelection(p, pr)
                } else null
            }
        }
    }
}