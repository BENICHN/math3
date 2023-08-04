package fr.benichn.math3.formulas

import fr.benichn.math3.formulas.FormulaToken.Companion.readToken
import fr.benichn.math3.graphics.boxes.FormulaBox
import java.io.Reader
import java.math.BigDecimal

sealed class FormulaToken {
    data class Number(val value: BigDecimal) : FormulaToken() {
        override fun toString() = value.toString()
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
                    Number(BigDecimal(s))
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
    abstract fun toBoxes(): List<FormulaBox>
    abstract fun flatten(): FormulaGroupedToken
    data object Empty: FormulaGroupedToken() {
        override fun toBoxes(): List<FormulaBox> = TODO()
        override fun toString() = ""
        override fun flatten() = this
    }
    data class Symbol(val value: FormulaToken) : FormulaGroupedToken() {
        override fun toBoxes(): List<FormulaBox> = TODO()
        override fun toString() = value.toString()
        override fun flatten() = this
    }
    data class Group(val value: FormulaGroupedToken) : FormulaGroupedToken() {
        override fun toBoxes(): List<FormulaBox> = TODO()
        override fun toString() = "(:$value:)"
        override fun flatten() =
            Group(value.flatten())
    }
    data class Listing(val grouped: Boolean, val values: List<FormulaGroupedToken>) : FormulaGroupedToken() {
        override fun toBoxes(): List<FormulaBox> = TODO()
        override fun toString() = if (grouped) "{:${values.joinToString(",")}:}" else values.joinToString(",")
        override fun flatten() =
            Listing(grouped, values.flatMap { gtk ->
                if (gtk is Listing && !gtk.grouped) gtk.values.map { it.flatten() }
                else listOf(gtk.flatten())
            })
    }
    data class Function(val name: FormulaGroupedToken, val param: FormulaGroupedToken) : FormulaGroupedToken() {
        override fun toBoxes(): List<FormulaBox> = TODO()
        override fun toString() = "$$name[$param]$"
        override fun flatten() =
            Function(name, param.flatten())
    }
    data class Unary(val operator: String, val value: FormulaGroupedToken) : FormulaGroupedToken() {
        override fun toBoxes(): List<FormulaBox> = TODO()
        override fun toString() = "<$operator:$operator$value:>"
        override fun flatten() =
            Unary(operator, value.flatten())
    }
    data class Binary(val operator: String, val values: List<FormulaGroupedToken>) : FormulaGroupedToken() {
        override fun toBoxes(): List<FormulaBox> = TODO()
        override fun toString() = "<$operator:${values.joinToString(operator)}:>"
        override fun flatten() =
            Binary(operator, values.flatMap { gtk ->
                if (gtk is Binary && gtk.operator == operator) values.map { it.flatten() }
                else listOf(gtk.flatten())
            })
    }

    private class GroupedTokenWithEnd(
        val gtk: FormulaGroupedToken,
        val end: FormulaToken? = null
    )

    companion object {
        val priorities = mapOf(
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
                    else -> Binary(" ", values.toList())
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
                                    val content = readGroupedTokenWithEnd(-7)
                                    GroupedTokenWithEnd(
                                        when (c) {
                                            '[' -> Function(values.removeLast(), content.gtk)
                                            '{' -> Listing(true, listOf(content.gtk))
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
                                        if (values.isEmpty()) Unary(c.toString(), right.gtk)
                                        else {
                                            val repl = if (p > 0) values.removeLast() else makeResultGroup()
                                            when (c) {
                                                ',' -> Listing(false, listOf(repl, right.gtk))
                                                else -> Binary(c.toString(), listOf(repl, right.gtk))
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