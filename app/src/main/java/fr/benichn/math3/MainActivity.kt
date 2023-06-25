package fr.benichn.math3

import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import fr.benichn.math3.graphics.FormulaView
import fr.benichn.math3.graphics.boxes.FractionFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoxInputCoord
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
                    val sc = fv.box.selectedChildren
                    val deletionResult = if (sc.isNotEmpty()) {
                        var res = DeletionResult()
                        for (c in sc) {
                            res = c.delete()
                        }
                        res
                    } else {
                        when (val p = fv.box.caret!!.position) {
                            is CaretPosition.None -> {
                                DeletionResult()
                            }

                            is CaretPosition.Single -> {
                                val (box, i) = p.ic
                                if (i == 0) {
                                    if (box.parentInput != null) {
                                        box.delete()
                                    } else {
                                        DeletionResult(p)
                                    }
                                } else {
                                    val b = box.ch[i - 1]
                                    if (b.selectBeforeDeletion) {
                                        b.isSelected = true
                                        DeletionResult(p)
                                    } else {
                                        b.delete()
                                    }
                                }
                            }
                        }
                    }
                    val (newPos, fb) = deletionResult
                    fv.box.caret!!.position = when (newPos) {
                        is CaretPosition.None -> { CaretPosition.None }
                        is CaretPosition.Single -> {
                            if (!fb.isEmpty) {
                                val i = newPos.ic.box.addFinalBoxes(newPos.ic.index, fb)
                                CaretPosition.Single(BoxInputCoord(newPos.ic.box, i))
                            } else {
                                newPos
                            }
                        }
                    }
                }
                else -> {
                    val p = fv.box.caret!!.position
                    when (p) {
                        is CaretPosition.None -> { }
                        is CaretPosition.Single -> {
                            val (box, i) = p.ic
                            val newBox = when (id) {
                                "over" -> FractionFormulaBox()
                                else -> TextFormulaBox(id)
                            }
                            box.addBox(i, newBox)
                            newBox.addInitialBoxes(
                                InitialBoxes.BeforeAfter(
                                    box.ch.take(i),
                                    box.ch.takeLast(box.ch.size - i)
                                ))
                            fv.box.caret!!.position = newBox.getInitialCaretPos().toCaretPosition()
                        }
                    }
                }
            }
        }
    }
}