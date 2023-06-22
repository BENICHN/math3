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
                    if (p?.si != null && p.si != SidedIndex(0, Side.L)) {
                        val pr = p.si.toR()
                        p.box.removeBoxAt(pr.index)
                        fv.caret.position = if (pr.index == 0) BoxInputCoord(p.box, null) else BoxInputCoord(p.box, SidedIndex(pr.index-1,Side.R))
                    } else if (p != null) {
                        val newPos = p.box.delete()
                        fv.caret.position = newPos
                    }
                }
                else -> {
                    val p = fv.caret.position
                    if (p != null) {
                        val i = p.si?.toL()?.index ?: 0
                        val newBox = when (id) {
                            "over" -> FractionFormulaBox()
                            else -> TextFormulaBox(id)
                        }
                        p.box.addBox(i, newBox)
                        fv.caret.position = newBox.getInitialCaretPos().toInputCoord()
                    }
                }
            }
        }
    }
}