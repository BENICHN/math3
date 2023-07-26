package fr.benichn.math3

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import fr.benichn.math3.formulas.FormulaGroupedToken.Companion.readGroupedToken
import fr.benichn.math3.formulas.FormulaToken.Companion.readToken
import fr.benichn.math3.graphics.FormulaView
import fr.benichn.math3.graphics.boxes.BracketFormulaBox
import fr.benichn.math3.graphics.boxes.BracketsInputFormulaBox
import fr.benichn.math3.graphics.boxes.DerivativeFormulaBox
import fr.benichn.math3.graphics.boxes.FractionFormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.MatrixFormulaBox
import fr.benichn.math3.graphics.boxes.SequenceChild.Companion.ign
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.TopDownFormulaBox
import fr.benichn.math3.graphics.boxes.DerivativeOperatorFormulaBox
import fr.benichn.math3.graphics.boxes.DiscreteOperationFormulaBox
import fr.benichn.math3.graphics.boxes.DiscreteOperatorFormulaBox
import fr.benichn.math3.graphics.boxes.IntegralFormulaBox
import fr.benichn.math3.graphics.boxes.IntegralOperatorFormulaBox
import fr.benichn.math3.graphics.boxes.RootFormulaBox
import fr.benichn.math3.graphics.boxes.ScriptFormulaBox
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.caret.ContextMenuEntry
import fr.benichn.math3.numpad.NumpadView
import fr.benichn.math3.numpad.types.Pt
import java.io.StringReader

class App : Application() {
    init {
        instance = this
        // Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        //     Log.d("erore", "$thread ~ $throwable")
        // }
    }

    companion object {
        lateinit var instance: App private set
    }
}


class MainActivity : AppCompatActivity() {
    private lateinit var fv: FormulaView
    private lateinit var nv: NumpadView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fv = findViewById(R.id.fv)
        nv = findViewById(R.id.numpad)
        nv.onButtonClicked += { _, id ->
            when (id) {
                "⌫" -> {
                    fv.sendDelete()
                }
                else -> {
                    val newBox = {
                        when (id) {
                            "over" -> FractionFormulaBox()
                            "sqrt" -> RootFormulaBox(RootFormulaBox.Type.SQRT)
                            "sqrt_n" -> RootFormulaBox(RootFormulaBox.Type.ORDER)
                            "brace" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.BRACE)
                            "bracket" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.BRACKET)
                            "chevron" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.CHEVRON)
                            "curly" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.CURLY)
                            "floor" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.FLOOR)
                            "ceil" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.CEIL)
                            "abs" -> BracketsInputFormulaBox(type = BracketFormulaBox.Type.BAR)
                            "superscript" -> ScriptFormulaBox(TopDownFormulaBox.Type.TOP)
                            "subscript" -> ScriptFormulaBox(TopDownFormulaBox.Type.BOTTOM)
                            "int_indef" -> IntegralFormulaBox(TopDownFormulaBox.Type.NONE)
                            "int_def" -> IntegralFormulaBox(TopDownFormulaBox.Type.BOTH)
                            "deriv" -> DerivativeFormulaBox(TopDownFormulaBox.Type.BOTTOM)
                            "deriv_n" -> DerivativeFormulaBox(TopDownFormulaBox.Type.BOTH)
                            "sum_indef" -> DiscreteOperationFormulaBox("∑", DiscreteOperatorFormulaBox.Type.INDEFINITE)
                            "sum_bounds" -> DiscreteOperationFormulaBox("∑", DiscreteOperatorFormulaBox.Type.BOUNDS)
                            "sum_list" -> DiscreteOperationFormulaBox("∑", DiscreteOperatorFormulaBox.Type.LIST)
                            "prod_indef" -> DiscreteOperationFormulaBox("∏", DiscreteOperatorFormulaBox.Type.INDEFINITE)
                            "prod_bounds" -> DiscreteOperationFormulaBox("∏", DiscreteOperatorFormulaBox.Type.BOUNDS)
                            "prod_list" -> DiscreteOperationFormulaBox("∏", DiscreteOperatorFormulaBox.Type.LIST)
                            "matrix" -> MatrixFormulaBox(Pt(2, 2))
                            else -> TextFormulaBox(id)
                        }
                    }
                    fv.sendAdd(newBox)
                }
            }
        }

        val s = "(a* b^1)(x)*2 + a2*[2+6].real(5+(9+9))" // "(3*(-5 - Sqrt[33])*(2*x - 4*x^2 + 4*x^3 - n*x^n - n^2*x^n + 2*x^(1 + n) + 2*n^2*x^(1 + n) - 4*x^(2 + n) + n*x^(2 + n) - n^2*x^(2 + n) + 4*x*Sin[x] - 4*x^2*Sin[x] - 4*x^n*Sin[x] + 4*x^(1 + n)*Sin[x]))/(2*(-1 + x)^3*x)"
        val sr = StringReader(s)
        val gtk = sr.readGroupedToken()
        Log.d("gtk", gtk.toString())
    }
}