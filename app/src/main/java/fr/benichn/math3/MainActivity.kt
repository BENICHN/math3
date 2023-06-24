package fr.benichn.math3

import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit

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
                    val p = fv.caret.position
                    p?.also {
                        val (box, i) = it
                        val newPos = if (i == 0) {
                            box.delete()
                        } else {
                            box.removeBoxAt(i-1)
                            BoxInputCoord(box, i-1)
                        }
                        fv.caret.position = newPos
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
                        newBox.addInitialBoxes(InitialBoxes.BeforeAfter(
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