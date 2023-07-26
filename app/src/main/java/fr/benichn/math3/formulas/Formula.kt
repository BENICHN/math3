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
    data class Method(val value: String) : FormulaToken() {
        override fun toString() = ".$value"
    }
    data class Symbol(val value: Char) : FormulaToken() {
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
                    Number(BigDecimal(s))
                }
                c0.isLetter() -> {
                    readChars { c -> c.isLetter() || c.isDigit() }
                    Variable(s)
                }
                c0 == '.' && peekChar()?.isLetter() == true -> {
                    readChars { c -> c.isLetter() }
                    Method(s.substring(1))
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
    data class Function(val name: FormulaGroupedToken, val param: FormulaGroupedToken) : FormulaGroupedToken() {
        override fun toString() = "$$name($param)$"
    }
    data class Unary(val operator: String, val value: FormulaGroupedToken) : FormulaGroupedToken() {
        override fun toString() = "<$operator:$operator$value:>"
    }
    data class Method(val obj: FormulaGroupedToken, val name: String, val param: FormulaGroupedToken) : FormulaGroupedToken() {
        override fun toString() = "@$obj@$name($param)"
    }
    data class Binary(val operator: String, val left: FormulaGroupedToken, val right: FormulaGroupedToken) : FormulaGroupedToken() {
        override fun toString() = "<$operator:$left$operator$right:$operator>"
    }

    private class GroupedTokenWithEnd(
        val gtk: FormulaGroupedToken,
        val end: FormulaToken? = null
    )

    companion object {
        val priorities = mapOf(
            '+' to -1,
            '-' to -1,
            '*' to 0,
            '/' to 0,
            '^' to 1,
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
            var result: FormulaGroupedToken = Empty
            var tk = readToken()
            while (true) {
                when (tk) {
                    null -> break
                    else -> {
                        when (tk) {
                            is FormulaToken.Number, is FormulaToken.Variable -> {
                                assert(result is Empty)
                                result = Token(tk)
                            }
                            is FormulaToken.Method -> {
                                val beg = readToken()
                                assert(beg is FormulaToken.Symbol && beg.value == '(')
                                val content = readGroupedTokenWithEnd(-7)
                                result = Method(result, tk.value, content.gtk)
                            }
                            is FormulaToken.Symbol -> {
                                val c = tk.value
                                val g = groupDelimiters[c]?.let { del ->
                                    val content = readGroupedTokenWithEnd(-7)
                                    GroupedTokenWithEnd(
                                        if (c == '(' && result !is Empty) {
                                            Function(result, content.gtk).also { result = Empty }
                                        } else Group(c.toString(), del.toString(), content.gtk),
                                        null)
                                } ?: run {
                                    val p = getPriority(c)
                                    if (p <= priority) {
                                        return GroupedTokenWithEnd(result, tk)
                                    }
                                    val right = readGroupedTokenWithEnd(p)
                                    GroupedTokenWithEnd(
                                        if (result == Empty) Unary(c.toString(), right.gtk)
                                        else Binary(c.toString(), result, right.gtk).also { result = Empty },
                                        right.end
                                    )
                                }
                                assert(result is Empty)
                                result = g.gtk
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
            return GroupedTokenWithEnd(result)
        }
        fun Reader.readGroupedToken() = readGroupedTokenWithEnd().gtk
    }
}