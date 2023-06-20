package fr.benichn.math3

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

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
                        p.seq.removeBoxAt(pr.index)
                        fv.caret.position = if (pr.index == 0) BoxSeqCoord(p.seq, null) else BoxSeqCoord(p.seq, SidedIndex(pr.index-1,Side.R))
                    }
                }
                else -> {
                    val p = fv.caret.position
                    if (p != null) {
                        val i = p.si?.toL()?.index ?: 0
                        p.seq.addBox(i, when (id) {
                            "over" -> FractionFormulaBox()
                            else -> TextFormulaBox(id)
                        })
                        fv.caret.position = BoxSeqCoord(p.seq, SidedIndex(i,Side.R))
                    }
                }
            }
        }
    }
}