package fr.benichn.math3.graphics.caret

import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.graphics.Utils
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.SequenceFormulaBox
import fr.benichn.math3.graphics.boxes.types.Range
import fr.benichn.math3.graphics.boxes.types.SidedBox
import fr.benichn.math3.graphics.types.Side

sealed class CaretPosition {
    data object None : CaretPosition()
    data class Single(val box: InputFormulaBox, val index: Int) : CaretPosition() {
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

        companion object {
            fun fromSidedBox(sb: SidedBox): Single? {
                val (box, side) = sb
                if (box is InputFormulaBox) {
                    assert(box.ch.size == 0) // en theorie la input ne peut pas etre cliquée si elle n'est pas vide
                    return Single(box, 0)
                }
                else {
                    var b = box
                    var i: Int
                    while (!b.isRoot) {
                        i = b.indexInParent!!
                        b = b.parent!!
                        if (b is InputFormulaBox) return Single(b, if (side == Side.L) i else i+1)
                    }
                    return null
                }
            }
        }
    }
    data class Selection(val box: SequenceFormulaBox, val indexRange: Range) : CaretPosition() {
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

        val isMutable
            get() = box is InputFormulaBox

        val leftSingle
            get() = (box as? InputFormulaBox)?.let { Single(it, indexRange.start) }
        val rightSingle
            get() = (box as? InputFormulaBox)?.let { Single(it, indexRange.end) }

        fun contains(b: FormulaBox): Boolean {
            val i = box.deepIndexOf(b)
            return indexRange.start <= i && i < indexRange.end
        }

        companion object {
            fun mergeSelections(s1: Selection, s2: Selection): Selection? {
                val s1Sequences = getBoxSequences(s1.box)
                val s2Sequences = getBoxSequences(s2.box)
                val commonParent = s1Sequences.zip(s2Sequences).lastOrNull { (p1, p2) -> p1.box == p2.box }

                fun retrieveRange(s: Selection, p: FormulaBox.ParentWithIndex) : Range =
                    if (p.box == s.box) {
                        s.indexRange
                    } else {
                        Range(p.index, p.index+1)
                    }

                return commonParent?.let { (p1, p2) ->
                    val box = p1.box as SequenceFormulaBox
                    val r1 = retrieveRange(s1, p1)
                    val r2 = retrieveRange(s2, p2)
                    val r = Range.sum(r1, r2)
                    Selection(box, r)
                }
            }

            private fun getBoxSequences(b: FormulaBox) =
                b.parentsAndThis.filter { it.box is SequenceFormulaBox }

            private fun getBoxParentSequenceWithIndex(b: FormulaBox): FormulaBox.ParentWithIndex? =
                b.parentWithIndex?.let {
                    if (it.box is SequenceFormulaBox) {
                        it
                    }
                    else {
                        getBoxParentSequenceWithIndex(it.box)
                    }
                }

            fun fromBox(b: FormulaBox): Selection? =
                getBoxParentSequenceWithIndex(b)?.let { (p, i) -> Selection(p as SequenceFormulaBox, Range(i, i+1)) }

            fun fromSingles(p1: Single, p2: Single): Selection? {
                val s1 = fromSingle(p1)
                val s2 = fromSingle(p2)
                return mergeSelections(s1, s2)
            }

            fun fromSingle(p: Single) =
                Selection(p.box, Range(p.index, p.index))
        }
    }
}