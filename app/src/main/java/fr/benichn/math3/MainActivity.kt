package fr.benichn.math3

import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import fr.benichn.math3.graphics.FormulaView
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.FractionFormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.numpad.NumpadFragment

class App : Application() {
    init {
        instance = this
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
                    val deletionResult =
                        when (val p = fv.caret.position) {
                            is CaretPosition.None -> {
                                DeletionResult()
                            }

                            is CaretPosition.Single -> {
                                val (box, i) = p
                                if (i == 0) {
                                    fun isInputRoot(b: FormulaBox): Boolean =
                                        b.parent?.let { if (it is InputFormulaBox) false else isInputRoot(it) } ?: true
                                    if (!isInputRoot(box)) {
                                        box.delete()
                                    } else {
                                        DeletionResult(p)
                                    }
                                } else {
                                    val b = box.ch[i - 1]
                                    if (b.selectBeforeDeletion) {
                                        DeletionResult.fromSelection(b)
                                    } else {
                                        b.delete()
                                    }
                                }
                            }

                            is CaretPosition.Selection -> {
                                var res = DeletionResult()
                                for (c in p.selectedBoxes) {
                                    res = c.delete()
                                }
                                res
                            }
                        }
                    val (newPos, fb) = deletionResult
                    fv.caret.position = when (newPos) {
                        is CaretPosition.None -> { newPos }
                        is CaretPosition.Single -> {
                            if (!fb.isEmpty) {
                                newPos.box.addFinalBoxes(newPos.index, fb)
                            } else {
                                newPos
                            }
                        }
                        is CaretPosition.Selection -> { newPos }
                    }
                }
                else -> {
                    var initialBoxes: InitialBoxes? = null
                    val pos = when (val p = fv.caret.position) {
                        is CaretPosition.None -> { null }
                        is CaretPosition.Single -> {
                            p
                        }
                        is CaretPosition.Selection -> {
                            initialBoxes = InitialBoxes.Selection(p.selectedBoxes)
                            for (c in p.selectedBoxes) {
                                c.delete()
                            }
                            when (p.box) {
                                is InputFormulaBox -> CaretPosition.Single(p.box, p.indexRange.start)
                            }
                        }
                    }
                    pos?.also {
                        val (box, i) = it
                        val newBox = when (id) {
                            "over" -> FractionFormulaBox()
                            else -> TextFormulaBox(id)
                        }
                        box.addBox(i, newBox)
                        newBox.addInitialBoxes(initialBoxes ?:
                            InitialBoxes.BeforeAfter(
                                box.ch.take(i),
                                box.ch.takeLast(box.ch.size - i)
                            ))
                        fv.caret.position = newBox.getInitialCaretPos().toCaretPosition()
                    }
                }
            }
        }
    }
}