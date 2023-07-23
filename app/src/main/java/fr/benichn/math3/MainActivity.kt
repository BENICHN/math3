package fr.benichn.math3

import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import fr.benichn.math3.graphics.FormulaView
import fr.benichn.math3.graphics.boxes.BracketsInputFormulaBox
import fr.benichn.math3.graphics.boxes.DerivativeFormulaBox
import fr.benichn.math3.graphics.boxes.FractionFormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.MatrixFormulaBox
import fr.benichn.math3.graphics.boxes.SequenceChild.Companion.ign
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.TopDownFormulaBox
import fr.benichn.math3.graphics.boxes.DerivativeOperatorFormulaBox
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.caret.ContextMenuEntry
import fr.benichn.math3.numpad.NumpadView
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
    private lateinit var nv: NumpadView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fv = findViewById(R.id.fv)
        nv = findViewById(R.id.numpad)
        nv.onButtonClicked += { _, id ->
            when (id) {
                "del" -> {
                    fv.sendDelete()
                }
                else -> {
                    val newBox = {
                        when (id) {
                            "E" -> {
                                TextFormulaBox("E")
                            }
                            "over" -> FractionFormulaBox()
                            "clav" -> BracketsInputFormulaBox() // FunctionFormulaBox("PGCD")
                            "recent" -> DerivativeFormulaBox() /*ScriptFormulaBox(TopDownFormulaBox.Type.BOTH).apply {
                                allowedTypes = listOf(
                                    TopDownFormulaBox.Type.TOP,
                                    TopDownFormulaBox.Type.BOTTOM,
                                    TopDownFormulaBox.Type.BOTH,
                                )
                            }*/
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