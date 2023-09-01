package fr.benichn.math3.graphics.boxes

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.google.gson.JsonObject
import fr.benichn.math3.Utils.toJsonArray
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.PaintedPath
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.caret.CaretPosition
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

    // override fun toWolfram() = ch.joinToString("") {
    //     if (it.isVariable()) " ${it.toWolfram()} "
    //     else it.toWolfram()
    // }

    override fun toJson() = chr.map { it.toJson() }.toJsonArray()

    override fun toSage(): String {
        var result = ""
        var check: ((FormulaBox) -> Boolean)? = null
        for (c in ch) {
            if (check?.invoke(c) != true) {
                if (result.isNotEmpty() && !(c is TextFormulaBox && c.text in binaryOperators) && result.last().toString() !in binaryOperators) {
                    result += "*"
                }
                check = null
            }
            result += c.toSage()
            check = check ?: when (c) {
                is TextFormulaBox -> {
                    val t = c.text
                    if (t.length == 1) {
                        val char = t[0]
                        when {
                            char in specialCharacters -> { _ -> false }
                            char.isDigit() || char == '.' -> { b: FormulaBox -> b is TextFormulaBox && b.text.length == 1 && b.text[0].let { it.isDigit() || it == '.' } || b is ScriptFormulaBox }
                            char.isLetter() -> { b: FormulaBox -> b is TextFormulaBox && b.text.length == 1 && b.text[0].let { (it.isLetter() || it.isDigit()) && it !in specialCharacters } || b is ScriptFormulaBox }
                            else -> null
                        }
                    } else null
                }
                else -> null
            }
        }
        return result
    }

    companion object {
        val binaryOperators = listOf( "+", "-", "×", "*", "^" )
        val specialCharacters = listOf('ⅇ', 'ⅈ', 'ℼ')
        const val SEP_RADIUS = DEFAULT_TEXT_WIDTH * 0.5f
    }
}
