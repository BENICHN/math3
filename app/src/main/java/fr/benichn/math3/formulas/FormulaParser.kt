package fr.benichn.math3.formulas

import fr.benichn.math3.Utils.intercalate
import fr.benichn.math3.formulas.FormulaToken.Companion.readToken
import fr.benichn.math3.graphics.Utils.prepend
import fr.benichn.math3.graphics.boxes.BracketFormulaBox
import fr.benichn.math3.graphics.boxes.BracketsInputFormulaBox
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.FractionFormulaBox
import fr.benichn.math3.graphics.boxes.MatrixFormulaBox
import fr.benichn.math3.graphics.boxes.RootFormulaBox
import fr.benichn.math3.graphics.boxes.ScriptFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.TopDownFormulaBox
import fr.benichn.math3.graphics.boxes.toBoxes
import fr.benichn.math3.graphics.types.CellMode
import fr.benichn.math3.numpad.types.Pt
import java.io.Reader

sealed class FormulaToken {
    data class Number(val value: String) : FormulaToken() {
        override fun toString() = value
    }
    data class Variable(val value: String) : FormulaToken() {
        override fun toString() = value
    }
    data class Sign(val value: Char) : FormulaToken() {
        override fun toString() = value.toString()
    }
    companion object {
        fun Reader.readChar() = read().let { i -> if (i == -1) null else i.toChar() }
        fun Reader.peekChar(): Char? {
            mark(1)
            return readChar().also { reset() }
        }
        fun Reader.readToken(): FormulaToken? {
            val c0 = readChar() ?: return null
            var s = c0.toString()
            fun readChars(check: (Char) -> Boolean) {
                while (true) {
                    mark(1)
                    val c = readChar() ?: break
                    if (check(c)) {
                        s += c
                    } else {
                        reset()
                        break
                    }
                }
            }
            return when {
                c0.isDigit() || c0 == '.' && peekChar()?.isDigit() == true -> {
                    readChars { c -> c.isDigit() || c == '.' }
                    Number(s)
                }
                c0.isLetter() -> {
                    readChars { c -> c.isLetter() || c.isDigit() }
                    Variable(s)
                }
                c0 == ' ' -> readToken()
                else -> {
                    Sign(c0)
                }
            }
        }
    }
}

sealed class FormulaGroupedToken {
    abstract fun toBoxes(mode: Int = CellMode.DEFAULT): List<FormulaBox>
    abstract fun flatten(): FormulaGroupedToken
    open fun degroup() = this
    val isVariable
        get() = this is Symbol && this.value is FormulaToken.Variable
    val isNumber
        get() = this is Symbol && this.value is FormulaToken.Number
    data object Empty: FormulaGroupedToken() {
        override fun toBoxes(mode: Int): List<FormulaBox> = listOf()
        override fun toString() = ""
        override fun flatten() = this
    }
    data class Symbol(val value: FormulaToken) : FormulaGroupedToken() {
        override fun toBoxes(mode: Int): List<FormulaBox> =
            when (value) {
                is FormulaToken.Number -> value.value.toBoxes()
                is FormulaToken.Sign -> throw UnsupportedOperationException()
                is FormulaToken.Variable -> when (value.value) {
                    "I" -> "ⅈ"
                    "Pi" -> "ℼ"
                    "E" -> "ⅇ"
                    else -> value.value
                }.let { text ->
                    if (mode and CellMode.ONE_LETTER_VAR != 0) listOf(TextFormulaBox(text))
                    else text.toBoxes()
                }
            }
        override fun toString() = value.toString()
        override fun flatten() = this
    }
    data class Group(val value: FormulaGroupedToken) : FormulaGroupedToken() {
        override fun toBoxes(mode: Int): List<FormulaBox> = listOf(BracketsInputFormulaBox(value.toBoxes(mode), type = BracketFormulaBox.Type.BRACE))
        override fun toString() = "(:$value:)"
        override fun flatten() =
            value.flatten().let { if (it is Group) it else Group(it) }
        override fun degroup() = value.degroup()
    }
    data class Listing(val type: Type, val values: List<FormulaGroupedToken>) : FormulaGroupedToken() {
        enum class Type {
            PARAMS,
            LIST,
            NONE
        }
        override fun toBoxes(mode: Int): List<FormulaBox> =
                matrixShape?.let { shape ->
                    val boxes = values.map { (it as Listing).values.map { gtk -> gtk.toBoxes(mode)
                    } }
                    listOf(MatrixFormulaBox(shape).apply {
                        for (j in 0 until shape.x) {
                            for (i in 0 until shape.y) {
                                grid.getInput(Pt(j, i)).addBoxes(boxes[i][j])
                            }
                        }
                    })
                } ?: values.map { it.toBoxes(mode) }.let { bss ->
                    when (type) {
                        Type.PARAMS -> listOf(
                            MatrixFormulaBox(
                                Pt(1, bss.size),
                                matrixType = MatrixFormulaBox.Type.PARAMS
                            ).apply { grid.addBoxesInColumn(0, bss) })

                        Type.LIST -> listOf(
                            MatrixFormulaBox(
                                Pt(1, bss.size),
                                matrixType = MatrixFormulaBox.Type.LIST
                            ).apply { grid.addBoxesInColumn(0, bss) })

                        Type.NONE -> bss.intercalate { ",".toBoxes() }.flatten()
                    }
                }
        override fun toString() = values.joinToString(",").let { s ->
            when (type) {
                Type.PARAMS -> "[:$s:]"
                Type.LIST -> "{:$s:}"
                Type.NONE -> s
            }
        }
        override fun flatten(): Listing =
            Listing(type, values.flatMap { gtk ->
                if (gtk is Listing && gtk.type == Type.NONE) gtk.flatten().values
                else listOf(gtk.flatten())
            })
        val matrixShape: Pt?
            get() {
                var sz = -1
                return if (type == Type.LIST && values.isNotEmpty() && values.all {
                    if (it is Listing && it.type == Type.LIST) {
                        val s = it.values.size
                        (sz == -1 || sz == s).also { sz = s }
                    } else false
                }) {
                    Pt(sz, values.size)
                } else null
            }
    }
    data class Function(val name: FormulaGroupedToken, val param: Listing) : FormulaGroupedToken() {
        override fun toBoxes(mode: Int) =
            ((name as? Symbol)?.value as? FormulaToken.Variable)?.let { v ->
                val s = v.value
                val n = param.values.size
                when {
                    s == "Sqrt" && n == 1 -> listOf(RootFormulaBox().apply {
                        input.addBoxes(param.values[0].toBoxes(mode))
                    })
                    else -> null
                }
            } ?: (name.toBoxes(mode) + param.toBoxes(mode))
        override fun toString() = "$$name$param$"
        override fun flatten() =
            Function(name, param.flatten())
    }
    data class Unary(val operator: String, val value: FormulaGroupedToken) : FormulaGroupedToken() {
        override fun toBoxes(mode: Int): List<FormulaBox> = value.toBoxes(mode).prepend(TextFormulaBox(operator))
        override fun toString() = "<$operator:$operator$value:>"
        override fun flatten() =
            Unary(operator, value.flatten())
    }
    data class Binary(val operator: String, val values: List<FormulaGroupedToken>) : FormulaGroupedToken() {
        // sealed class TaggedGTK(
        //     boxes: List<FormulaBox>
        // ) {
        //     data class Number(val boxes: List<FormulaBox>) : TaggedGTK(boxes)
        //     data class Variable(val boxes: List<FormulaBox>) : TaggedGTK(boxes)
        //     data class Osef(val boxes: List<FormulaBox>) : TaggedGTK(boxes)
        // }
        override fun toBoxes(mode: Int) = when (operator) {
            "/" -> values.map { it.degroup().toBoxes(mode) }.reduce { acc, boxes ->
                listOf(FractionFormulaBox(acc, boxes))
            }
            "*" -> mutableListOf<FormulaBox>().also { res ->
                values.forEachIndexed { i, gtk ->
                    if (i == 0) res.addAll(gtk.toBoxes(mode))
                    else {
                        val prev = values[i - 1]
                        if (
                            prev.isNumber && gtk.isNumber ||
                            mode and CellMode.ONE_LETTER_VAR == 0 && (prev.isVariable && (gtk.isNumber || gtk.isVariable))
                        ) {
                            res.add(TextFormulaBox("×"))
                        }
                        res.addAll(gtk.toBoxes(mode))
                    }
                }
            }
            else -> mutableListOf<FormulaBox>().also { res ->
                for (gtk in values) {
                    if (res.isEmpty()) res.addAll(gtk.toBoxes(mode))
                    else {
                        res.addAll(
                            when (operator) {
                                "^" -> listOf(ScriptFormulaBox(type = TopDownFormulaBox.Type.TOP).apply {
                                    superscript.addBoxes(gtk.degroup().toBoxes(mode))
                                })
                                else -> gtk.toBoxes(mode).prepend(TextFormulaBox(operator))
                            }
                        )
                    }
                }
            }
        }
        override fun toString() = "<$operator:${values.joinToString(operator)}:$operator>"
        override fun flatten(): Binary =
            Binary(operator, values.flatMap { gtk ->
                if (gtk is Binary && gtk.operator == operator) gtk.flatten().values
                else listOf(gtk.flatten())
            })
    }

    private class GroupedTokenWithEnd(
        val gtk: FormulaGroupedToken,
        val end: FormulaToken? = null
    )

    companion object {
        val priorities = mapOf(
            '[' to 6,
            '^' to 1,
            '*' to 0,
            '/' to 0,
            '+' to -1,
            '-' to -1,
            ',' to -6,
            ']' to -7,
            ')' to -7,
            '}' to -7,
        )
        val groupDelimiters = mapOf(
            '(' to ')',
            '[' to ']',
            '{' to '}',
        )
        fun getPriority(c: Char) = priorities.getOrDefault(c, 0)

        private fun Reader.readGroupedTokenWithEnd(
            priority: Int = -8,
        ): GroupedTokenWithEnd {
            val values = mutableListOf<FormulaGroupedToken>()
            var tk = readToken()
            fun makeResultGroup(): FormulaGroupedToken {
                val res = when(values.size) {
                    0 -> Empty
                    1 -> values[0]
                    else -> Binary("*", values.toList())
                }
                values.clear()
                return res
            }
            fun makeResult(end: FormulaToken?) =
                GroupedTokenWithEnd(makeResultGroup(), end)
            while (true) {
                when (tk) {
                    null -> break
                    else -> {
                        when (tk) {
                            is FormulaToken.Number, is FormulaToken.Variable -> {
                                if (priority > 0 && values.isNotEmpty()) {
                                    return makeResult(tk)
                                } else {
                                    values.add(Symbol(tk))
                                }
                            }
                            is FormulaToken.Sign -> {
                                val c = tk.value
                                val g = groupDelimiters[c]?.let { del ->
                                    val p = getPriority(c)
                                    if (p <= priority && values.isNotEmpty()) {
                                        return makeResult(tk)
                                    }
                                    val content = readGroupedTokenWithEnd(-7)
                                    GroupedTokenWithEnd(
                                        when (c) {
                                            '[' -> Function(values.removeLast(), Listing(Listing.Type.PARAMS, listOf(content.gtk)))
                                            '{' -> Listing(Listing.Type.LIST, listOf(content.gtk))
                                            '(' -> Group(content.gtk)
                                            else -> throw UnsupportedOperationException()
                                        },
                                        null)
                                } ?: run {
                                    val p = getPriority(c)
                                    if (p <= priority) {
                                        return makeResult(tk)
                                    }
                                    val right = readGroupedTokenWithEnd(p)
                                    GroupedTokenWithEnd(
                                        when (c) {
                                            ',' -> Listing(Listing.Type.NONE, listOf(makeResultGroup(), right.gtk))
                                            else ->
                                                if (values.isEmpty()) Unary(c.toString(), right.gtk)
                                                else {
                                                    val repl = if (p > 0) values.removeLast() else makeResultGroup()
                                                    Binary(c.toString(), listOf(repl, right.gtk))
                                                }
                                        },
                                        right.end
                                    )
                                }
                                if (g.gtk !is Empty) values.add(g.gtk)
                                if (g.end != null) {
                                    tk = g.end
                                    continue
                                }
                            }
                        }
                    }
                }
                tk = readToken()
            }
            return makeResult(null)
        }
        fun Reader.readGroupedToken() = readGroupedTokenWithEnd().gtk
        fun Reader.readGroupedTokenFlattened() = readGroupedToken().flatten()
    }
}