package fr.benichn.math3.formulas

import fr.benichn.math3.Utils.Companion.intercalate
import fr.benichn.math3.formulas.FormulaToken.Companion.readToken
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import java.io.Reader
import java.math.BigDecimal

// abstract class Formula {
//     abstract fun toBoxes(): List<FormulaBox>
//     abstract val exactValue: Formula
//     open val numericValue: BigDecimal = throw UnsupportedOperationException()
// }
//
// data class NumberFormula(val value: BigDecimal) : Formula() {
//     override fun toBoxes() = value.toPlainString().map { c -> TextFormulaBox(c.toString()) } //!
//     override val exactValue
//         get() = this
//     override val numericValue
//         get() = value
// }
//
// data class VariableFormula(val value: String) : Formula() {
//     override fun toBoxes() = listOf(TextFormulaBox(value))
//     override val exactValue
//         get() = this
// }
//
// data class SumFormula(val values: List<Formula>) : Formula() {
//     override fun toBoxes() = values.map { it.toBoxes() }.intercalate { listOf(TextFormulaBox("+")) }.flatten()
//     override val exactValue: Formula
//         get() =
// }
//
// class ProductFormula(val left: Formula, val right: Formula, val hideOperator: Boolean) : Formula() {
//     override fun toBoxes(): List<FormulaBox> = listOf(
//         left.toBoxes(),
//         if (hideOperator) listOf() else listOf(TextFormulaBox("×")),
//         right.toBoxes()
//     ).flatten()
// }