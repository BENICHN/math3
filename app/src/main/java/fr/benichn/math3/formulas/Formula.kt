package fr.benichn.math3.formulas

import fr.benichn.math3.formulas.FormulaToken.Companion.readToken
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import java.io.Reader
import java.math.BigDecimal

abstract class Formula {
    abstract fun getBoxes(): List<FormulaBox>
    abstract fun getString(): String
}

class ProductFormula(val left: Formula, val right: Formula, val hideOperator: Boolean) : Formula() {
    override fun getBoxes(): List<FormulaBox> = listOf(
        left.getBoxes(),
        if (hideOperator) listOf() else listOf(TextFormulaBox("×")),
        right.getBoxes()
    ).flatten()

    override fun getString(): String =
        left.getString() + (if (hideOperator) "" else " * ") + right.getString()
}

sealed class FormulaToken {
    data class Number(val value: BigDecimal) : FormulaToken() {
        override fun toString() = value.toString()
    }
    data class Variable(val value: String) : FormulaToken() {
        override fun toString() = value
    }
    data class Symbol(val value: Char) : FormulaToken() {
        override fun toString() = value.toString()
    }
    companion object {
        fun Reader.readToken(): FormulaToken? {
            val c0 = read().also { if (it == -1) return null }.toChar()
            var s = c0.toString()
            fun readChars(check: (Char) -> Boolean) {
                while (true) {
                    mark(1)
                    val i = read()
                    if (i == -1) break
                    val c = i.toChar()
                    if (check(c)) {
                        s += c
                    } else {
                        reset()
                        break
                    }
                }
            }
            return when {
                c0.isDigit() -> {
                    readChars { c -> c.isDigit() }
                    Number(BigDecimal(s))
                }
                c0.isLetter() -> {
                    readChars { c -> c.isLetter() || c.isDigit() }
                    Variable(s)
                }
                c0 == ' ' -> readToken()
                else -> {
                    Symbol(c0)
                }
            }
        }
    }
}

sealed class FormulaGroupedToken {
    data object Empty: FormulaGroupedToken() {
        override fun toString() = ""
    }
    data class Token(val value: FormulaToken) : FormulaGroupedToken() {
        override fun toString() = value.toString()
    }
    data class Group(val begin: String, val end: String, val value: FormulaGroupedToken) : FormulaGroupedToken() {
        override fun toString() = "$begin$value$end"
    }
    data class Unary(val operator: String, val value: FormulaGroupedToken) : FormulaGroupedToken() {
        override fun toString() = "$$operator$value$"
    }
    data class Binary(val operator: String, val values: List<FormulaGroupedToken>) : FormulaGroupedToken() {
        override fun toString() = "<$operator:${values.joinToString(operator)}:$operator>"
    }

    private class GroupedTokenWithEnd(
        val gtk: FormulaGroupedToken,
        val end: FormulaToken?
    )

    companion object {
        val priorities = mapOf(
            '+' to -1,
            '-' to -1,
            '(' to 0,
            '/' to 0,
            '^' to 1,
            '[' to 7,
        )
        val groupDelimiters = mapOf(
            '(' to ')',
            '[' to ']',
            '{' to '}',
        )
        fun getPriority(c: Char) = priorities.getOrDefault(c, 0)

        private fun Reader.readGroupedTokenWithEnd(
            priority: Int = -8,
            stopChar: Char? = null,
        ): GroupedTokenWithEnd {
            val values = mutableListOf<FormulaGroupedToken>()
            var tk = readToken()
            fun makeResultGroup(): FormulaGroupedToken {
                val res = when(values.size) {
                    0 -> Empty
                    1 -> values[0]
                    else -> Binary("", values.toList())
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
                                    values.add(Token(tk))
                                }
                            }
                            is FormulaToken.Symbol -> {
                                val c = tk.value
                                if (c == stopChar) {
                                    return makeResult(tk)
                                }
                                val p = getPriority(c)
                                if (p <= priority) {
                                    return makeResult(tk)
                                }
                                val g = groupDelimiters[c]?.let { del ->
                                    val content = readGroupedTokenWithEnd(-7, del)
                                    val gtk = Group(c.toString(), del.toString(), content.gtk)
                                    GroupedTokenWithEnd(
                                        if (c == '[') {
                                            val op = ((values.removeLast() as Token).value as FormulaToken.Variable).value
                                            Unary(op, gtk)
                                        } else gtk,
                                        null)
                                } ?: run {
                                    val left =
                                        if (p >= 0)
                                            values.lastOrNull()?.also {
                                                values.removeLast()
                                            } ?: Empty
                                        else makeResultGroup()
                                    val right = readGroupedTokenWithEnd(p)
                                    GroupedTokenWithEnd(
                                        Binary(c.toString(), listOf(left, right.gtk)),
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
    }
}