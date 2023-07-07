package fr.benichn.math3.graphics.caret

import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.Utils
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.SequenceFormulaBox
import fr.benichn.math3.graphics.boxes.types.Range

sealed class CaretPosition {
    data object None : CaretPosition()

    data class Single(val box: InputFormulaBox, val index: Int) : CaretPosition() {
        val radius
            get() = box.accTransform.scale * FormulaBox.DEFAULT_TEXT_RADIUS

        fun getAbsPosition(): PointF {
            val y = box.accTransform.origin.y
            val x = if (box.ch.isEmpty()) {
                assert(index == 0)
                box.accRealBounds.centerX()
            } else if (index == box.ch.size) {
                box.accRealBounds.right
            } else {
                box.ch[index].accRealBounds.left
            }
            return PointF(x, y)
        }

        fun getElement(absPos: PointF): Element {
            val pos = getAbsPosition()
            val r = radius
            return if (Utils.squareDistFromLineToPoint(
                    pos.x,
                    pos.y - r,
                    pos.y + r,
                    absPos.x,
                    absPos.y
                ) < BoxCaret.SINGLE_MAX_TOUCH_DIST_SQ
            ) {
                Element.BAR
            } else {
                Element.NONE
            }
        }

        enum class Element {
            BAR,
            NONE
        }
    }

    data class Double(val box: InputFormulaBox, val indexRange: Range) : CaretPosition() {
        val selectedBoxes
            get() = box.ch.subList(indexRange.start, indexRange.end).toList()

        val bounds
            get() = Utils.sumOfRects(selectedBoxes.map { it.accRealBounds }).let { r ->
                if (r.left.isNaN() && indexRange.start == indexRange.end) {
                    box.accRealBounds.let {
                        val x = leftSingle?.getAbsPosition()?.x
                        if (x == null) r else RectF(x, it.top, x, it.bottom)
                    }
                } else r
            }

        val leftSingle
            get() = Single(box, indexRange.start)
        val rightSingle
            get() = Single(box, indexRange.end)

        fun contains(b: FormulaBox): Boolean {
            val i = box.deepIndexOf(b)
            return indexRange.start <= i && i < indexRange.end
        }

        fun getElement(absPos: PointF) =
            bounds.run {
                if (Utils.squareDistFromLineToPoint(
                        right,
                        top,
                        bottom,
                        absPos.x,
                        absPos.y
                    ) < BoxCaret.SELECTION_MAX_TOUCH_DIST_SQ
                ) {
                    Element.RIGHT_BAR
                } else if (Utils.squareDistFromLineToPoint(
                        left,
                        top,
                        bottom,
                        absPos.x,
                        absPos.y
                    ) < BoxCaret.SELECTION_MAX_TOUCH_DIST_SQ
                ) {
                    Element.LEFT_BAR
                } else if (contains(absPos.x, absPos.y)) {
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

        companion object {
            fun mergeSelections(s1: Double?, s2: Double?): Double? {
                if (s1 == null || s2 == null) return null
                val s1Sequences = getBoxSequences(s1.box)
                val s2Sequences = getBoxSequences(s2.box)
                val commonParent = s1Sequences.zip(s2Sequences).lastOrNull { (p1, p2) -> p1.box == p2.box }

                fun retrieveRange(s: Double, p: FormulaBox.ParentWithIndex) : Range =
                    if (p.box == s.box) {
                        s.indexRange
                    } else {
                        Range(p.index, p.index+1)
                    }

                return commonParent?.let { (p1, p2) ->
                    val box = p1.box as InputFormulaBox
                    val r1 = retrieveRange(s1, p1)
                    val r2 = retrieveRange(s2, p2)
                    val r = Range.sum(r1, r2)
                    Double(box, r)
                }
            }

            private fun getBoxSequences(b: FormulaBox) =
                b.parentsAndThis.filter { it.box is InputFormulaBox }

            private fun getBoxParentInputWithIndex(b: FormulaBox): FormulaBox.ParentWithIndex? =
                b.parentWithIndex?.let {
                    if (it.box is InputFormulaBox) {
                        it
                    }
                    else {
                        getBoxParentInputWithIndex(it.box)
                    }
                }

            fun fromBox(b: FormulaBox): Double? =
                getBoxParentInputWithIndex(b)?.let { (p, i) -> Double(p as InputFormulaBox, Range(i, i+1)) }

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

            fun fromSingle(p: Single) =
                Double(p.box, Range(p.index, p.index))
        }
    }

    data class DiscreteSelection(val box: FormulaBox, val indices: List<Int>) : CaretPosition() {
        val selectedBoxes
            get() = indices.map { i -> box.ch[i] }

        val bounds
            get() = selectedBoxes.map { it.accRealBounds }

        fun contains(b: FormulaBox): Boolean {
            val i = box.deepIndexOf(b)
            return indices.contains(i)
        }

        fun getElement(absPos: PointF) =
            if (bounds.any { it.contains(absPos.x, absPos.y) }) Element.INTERIOR else Element.NONE

        enum class Element {
            INTERIOR,
            NONE
        }
    }
}

fun CaretPosition?.noneIfNull() = this ?: CaretPosition.None