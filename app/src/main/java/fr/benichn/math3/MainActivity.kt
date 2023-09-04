package fr.benichn.math3

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.HorizontalScrollView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import fr.benichn.math3.formulas.FormulaGroupedToken.Companion.readGroupedToken
import fr.benichn.math3.graphics.FormulaCell
import fr.benichn.math3.graphics.FormulaCellsContainer
import fr.benichn.math3.graphics.boxes.BracketFormulaBox
import fr.benichn.math3.graphics.boxes.BracketsInputFormulaBox
import fr.benichn.math3.graphics.boxes.DerivativeFormulaBox
import fr.benichn.math3.graphics.boxes.DiscreteOperationFormulaBox
import fr.benichn.math3.graphics.boxes.DiscreteOperatorFormulaBox
import fr.benichn.math3.graphics.boxes.FractionFormulaBox
import fr.benichn.math3.graphics.boxes.IntegralFormulaBox
import fr.benichn.math3.graphics.boxes.MatrixFormulaBox
import fr.benichn.math3.graphics.boxes.RootFormulaBox
import fr.benichn.math3.graphics.boxes.ScriptFormulaBox
import fr.benichn.math3.graphics.boxes.SequenceFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.TopDownFormulaBox
import fr.benichn.math3.numpad.NamesBarView
import fr.benichn.math3.numpad.NumpadView
import fr.benichn.math3.numpad.types.Pt
import org.matheclipse.core.basic.AndroidLoggerFix
import org.matheclipse.core.eval.ExprEvaluator
import java.io.StringReader


class App : Application() {
    init {
        instance = this
        AndroidLoggerFix.fix()
    }

    lateinit var main: MainActivity

    companion object {
        val gson = Gson()
        lateinit var instance: App private set
        fun copyToClipboard(text: String, vararg aux: String) =
            (instance.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).let { clipboard ->
                clipboard.setPrimaryClip(ClipData.newPlainText("math3", text).apply {
                    aux.forEach {
                        addItem(ClipData.Item(it))
                    }
                })
            }
        fun canPasteFromClipboard() =
            (instance.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).let { clipboard ->
                clipboard.primaryClipDescription?.hasMimeType(MIMETYPE_TEXT_PLAIN) == true
            }
        fun pasteFromClipboard() =
            (instance.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).let { clipboard ->
                clipboard.primaryClip?.run {
                    (0..<itemCount).map { i -> getItemAt(i).text.toString() }
                }
            } ?: listOf()
    }
}

class MainActivity : AppCompatActivity() {
    lateinit var cellsContainer: FormulaCellsContainer
    lateinit var numpadView: NumpadView
    lateinit var namesBarView: NamesBarView

    init {
        App.instance.main = this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        namesBarView = findViewById(R.id.names)
        (namesBarView.parent as HorizontalScrollView).isHorizontalScrollBarEnabled = false
        namesBarView.barBox.dlgPattern.onChanged += { _, _ ->
            (namesBarView.parent as HorizontalScrollView).scrollX = 0
        }
        cellsContainer = findViewById(R.id.cellsContainer)
        cellsContainer.addCell()
        cellsContainer.fvs.forEachIndexed { i, fv ->
            when (i) {
                0 -> fv.input.addBoxes(
                    MatrixFormulaBox(Pt(3,3)),
                    MatrixFormulaBox(Pt(1,3))
                )
            }
        }
        numpadView = findViewById(R.id.numpad)
        numpadView.onButtonClicked += { _, id ->
            cellsContainer.realCurrentFV?.let { fv ->
                when (id) {
                    "⌫" -> {
                        fv.sendDelete()
                    }

                    "eval" -> {
                        val fc = fv.parent.parent as FormulaCell
                        val fcc = fc.parent.parent as FormulaCellsContainer
                        fcc.evalInputCreateCell(fc)
                    }

                    "⇪", "⇩", "⇧" -> { }

                    "⇥" -> {
                        fv.moveToNextInput()
                    }
                    "⇤" -> {
                        fv.moveToPreviousInput()
                    }

                    else -> {
                        val newBox = {
                            when (id) {
                                "↵" -> SequenceFormulaBox.LineStart()
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
                                "superscript_base" -> ScriptFormulaBox(TopDownFormulaBox.Type.TOP).apply { initialBoxesInScript = true }
                                "superscript" -> ScriptFormulaBox(TopDownFormulaBox.Type.TOP)
                                "subscript" -> ScriptFormulaBox(TopDownFormulaBox.Type.BOTTOM)
                                "int_indef" -> IntegralFormulaBox(TopDownFormulaBox.Type.NONE)
                                "int_def" -> IntegralFormulaBox(TopDownFormulaBox.Type.BOTH)
                                "deriv" -> DerivativeFormulaBox(TopDownFormulaBox.Type.BOTTOM)
                                "deriv_n" -> DerivativeFormulaBox(TopDownFormulaBox.Type.BOTH)
                                "sum_indef" -> DiscreteOperationFormulaBox(
                                    "∑",
                                    DiscreteOperatorFormulaBox.Type.INDEFINITE
                                )

                                "sum_bounds" -> DiscreteOperationFormulaBox(
                                    "∑",
                                    DiscreteOperatorFormulaBox.Type.BOUNDS
                                )

                                "sum_list" -> DiscreteOperationFormulaBox(
                                    "∑",
                                    DiscreteOperatorFormulaBox.Type.LIST
                                )

                                "prod_indef" -> DiscreteOperationFormulaBox(
                                    "∏",
                                    DiscreteOperatorFormulaBox.Type.INDEFINITE
                                )

                                "prod_bounds" -> DiscreteOperationFormulaBox(
                                    "∏",
                                    DiscreteOperatorFormulaBox.Type.BOUNDS
                                )

                                "prod_list" -> DiscreteOperationFormulaBox(
                                    "∏",
                                    DiscreteOperatorFormulaBox.Type.LIST
                                )

                                "matrix" -> MatrixFormulaBox(Pt(2, 2))
                                else -> TextFormulaBox(id)
                            }
                        }
                        fv.sendAdd(newBox)
                    }
                }
            }
        }

        val t = System.currentTimeMillis()
        val ev = ExprEvaluator()
        Log.d("ev2", "${(System.currentTimeMillis()-t)*.0001}")
        val s = "(a* b^1)(x)*2 + a2[2+6].real(5+(9+9))" // "(3*(-5 - Sqrt[33])*(2*x - 4*x^2 + 4*x^3 - n*x^n - n^2*x^n + 2*x^(1 + n) + 2*n^2*x^(1 + n) - 4*x^(2 + n) + n*x^(2 + n) - n^2*x^(2 + n) + 4*x*Sin[x] - 4*x^2*Sin[x] - 4*x^n*Sin[x] + 4*x^(1 + n)*Sin[x]))/(2*(-1 + x)^3*x)"
        val sr = StringReader(s)
        val gtk = sr.readGroupedToken()
        Log.d("gtk", gtk.toString())
    }
}