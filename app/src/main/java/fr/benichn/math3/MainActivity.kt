package fr.benichn.math3

import android.app.Application
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import fr.benichn.math3.graphics.FormulaView
import fr.benichn.math3.graphics.boxes.BigOperatorFormulaBox
import fr.benichn.math3.graphics.boxes.BracketsInputFormulaBox
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.FractionFormulaBox
import fr.benichn.math3.graphics.boxes.FunctionFormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.MatrixFormulaBox
import fr.benichn.math3.graphics.boxes.OperationFormulaBox
import fr.benichn.math3.graphics.boxes.ScriptFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.TopDownFormulaBox
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.caret.ContextMenuEntry
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.numpad.NumpadFragment
import fr.benichn.math3.numpad.types.Pt

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val nf = NumpadFragment()
        supportFragmentManager.commit {
            add(R.id.numpad, nf)
        }
        fv = findViewById(R.id.fv)
        nf.onButtonClicked = { id ->
            when (id) {
                "del" -> {
                    fv.sendDelete()
                }
                else -> {
                    val newBox = {
                        when (id) {
                            "E" -> {
                                fun op() = object : TopDownFormulaBox(
                                    bottom=TransformerFormulaBox(TextFormulaBox("⎳"), BoundsTransformer.Align(RectPoint.TOP_CENTER)),
                                    top=TransformerFormulaBox(TextFormulaBox("⎲"), BoundsTransformer.Align(RectPoint.BOTTOM_CENTER))) {
                                    override fun findChildBox(pos: PointF) = this
                                }
                                val opp = op()
                                object : OperationFormulaBox(
                                    BigOperatorFormulaBox(
                                        limitsPosition = TopDownFormulaBox.LimitsPosition.RIGHT,
                                        type = TopDownFormulaBox.Type.BOTH,
                                        operator = opp,
                                        below = InputFormulaBox(),
                                        above = InputFormulaBox()
                                    ),
                                    InputFormulaBox(),
                                    TextFormulaBox("ⅆ"),
                                    InputFormulaBox()
                                ) {
                                    override fun generateContextMenu() =
                                        ContextMenu(listOf(
                                            ContextMenuEntry.create<OperationFormulaBox>(BigOperatorFormulaBox(
                                                operator = op(),
                                                above = InputFormulaBox(),
                                                below = InputFormulaBox(),
                                                type = TopDownFormulaBox.Type.BOTH,
                                                limitsPosition = TopDownFormulaBox.LimitsPosition.RIGHT
                                            )) {
                                                it.bigOperator.type = TopDownFormulaBox.Type.BOTH
                                            },
                                            ContextMenuEntry.create<OperationFormulaBox>(BigOperatorFormulaBox(
                                                operator = op(),
                                                above = InputFormulaBox(),
                                                below = InputFormulaBox(),
                                                type = TopDownFormulaBox.Type.NONE,
                                            )) {
                                                it.bigOperator.type = TopDownFormulaBox.Type.NONE
                                            }),
                                            listOf(
                                                opp
                                            )
                                        )
                                }
                            }
                            "over" -> FractionFormulaBox()
                            "clav" -> FunctionFormulaBox("PGCD")
                            "recent" -> ScriptFormulaBox(TopDownFormulaBox.Type.BOTH)
                            "enter" -> MatrixFormulaBox(Pt(3, 3))
                            else -> TextFormulaBox(id)
                        }
                    }
                    fv.sendAdd(newBox)
                }
            }
        }
    }
}