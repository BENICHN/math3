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
                        val p = fv.caret.position
                        p?.let {
                            val (box, i) = it
                            if (i == 0) {
                                if (box.parentInput != null) {
                                    box.delete()
                                } else {
                                    DeletionResult(it)
                                }
                            } else {
                                val b = box.ch[i-1]
                                if (b.selectBeforeDeletion) {
                                    b.isSelected = true
                                    DeletionResult(it)
                                } else {
                                    b.delete()
                                }
                            }
                        }
                    }
                    deletionResult?.also { dr ->
                        val (newPos, fb) = dr
                        fv.caret.position = newPos?.let {
                            if (!fb.isEmpty) {
                                val i = it.box.addFinalBoxes(it.index, fb)
                                BoxInputCoord(it.box, i)
                            } else {
                                it
                            }
                        }
                    }
                }
                else -> {
                    val p = fv.caret.position
                    p?.also {
                        val (box, i) = it
                        val newBox = when (id) {
                            "over" -> FractionFormulaBox()
                            else -> TextFormulaBox(id)
                        }
                        p.box.addBox(i, newBox)
                        newBox.addInitialBoxes(
                            InitialBoxes.BeforeAfter(
                            p.box.ch.take(i),
                            p.box.ch.takeLast(p.box.ch.size - i)
                        ))
                        fv.caret.position = newBox.getInitialCaretPos().toInputCoord()
                    }
                }
            }
        }
    }
}