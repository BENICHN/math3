package fr.benichn.math3.numpad

import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.SequenceFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.PaintedPath

class NameButton(text: String = "") : TransformerFormulaBox(
    TextFormulaBox(text).apply {
        padding = Padding(DEFAULT_TEXT_WIDTH*0.66f)
        foreground = Color.BLACK
    },
    BoundsTransformer.Constant(BoxTransform.scale(0.7f))) {
    val textBox = child as TextFormulaBox
    override fun generateGraphics(): FormulaGraphics =
        super.generateGraphics().let { g ->
            g.withPictures {
                val r = g.bounds
                listOf(
                    PaintedPath(
                        Path().apply {
                            moveTo(r.right, r.top)
                            lineTo(r.right, r.bottom)
                        },
                        NumpadPageFormulaBox.gridPaint,
                        aboveChildren = true
                    )
                )
            }
        }
}

class NamesBar : SequenceFormulaBox() {
    private var buttons = (1..N_NAMES).map { NameButton() }
    private var namesWords = listOf<List<String>>()
    val dlgNames = BoxProperty(this, listOf<String>()).apply {
        onChanged += { _, e ->
            namesWords = e.new.map {
                var word = ""
                val words = mutableListOf<String>()
                for (c in it) {
                    if (word.isNotEmpty() && c.isUpperCase()) {
                        words.add(word)
                        word = c.toString()
                    } else word += c
                }
                if (word.isNotEmpty()) words.add(word)
                words
            }
        }
    }
    var names by dlgNames

    val dlgPattern = BoxProperty(this, "").apply {
        onChanged += { _, e ->
            val foundNames = findNames(e.new)
            val diff = foundNames.size - (ch.lastIndex)
            if (diff > 0) addBoxes(buttons.subList(ch.lastIndex,ch.lastIndex+diff))
            else if (diff < 0) removeBoxes(buttons.subList(ch.lastIndex+diff, ch.lastIndex))
            (foundNames.forEachIndexed { i, name -> buttons[i].textBox.text = name })
        }
    }
    var pattern by dlgPattern

    fun findNames(pattern: String): List<String> {
        if (pattern.length < MIN_PATTERN_LENGT) return listOf()
        val matches = namesWords.map { words ->
            var pat = pattern
            words.map { word ->
                var res = ""
                for (c in word) {
                    if (pat.isNotEmpty() && c.equals(pat[0], ignoreCase = true)) {
                        pat = pat.substring(1)
                        res += c
                    } else break
                }
                res
            }.let {
                if (pat.isNotEmpty()) listOf("")
                else it
            }
        }
        val scores = matches.map { it.withIndex().sumOf { (i, match) -> match.length / (i+1).toDouble() } }
        return names.withIndex()
            .groupBy { (i, _) -> scores[i] }
            .filter { (score, _) -> score > 0 }
            .map { (score, ns) -> score to ns.map { it.value }.sortedBy { it.length } }
            .sortedByDescending { (score, _) -> score }
            .flatMap { (_, ns) -> ns }
            .take(N_NAMES)
    }

    init {
        ch[0].padding = Padding(0f, DEFAULT_TEXT_WIDTH*0.66f*0.7f)
    }

    override fun shouldEnterInChild(c: FormulaBox, pos: PointF) = false

    override fun generateGraphics(): FormulaGraphics {
        return super.generateGraphics()
            .withBounds { r -> Padding(DEFAULT_TEXT_WIDTH*0.2f, 0f).applyOnRect(r) }
            .withBackground { Color.WHITE }
    }

    companion object {
        const val N_NAMES = 10
        const val MIN_PATTERN_LENGT = 2
    }
}