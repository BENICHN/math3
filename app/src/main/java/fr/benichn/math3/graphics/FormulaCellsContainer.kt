package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.children
import fr.benichn.math3.Utils.Companion.dp
import fr.benichn.math3.types.callback.ObservableProperty

class FormulaCellsContainer(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    private val ll = LinearLayout(context).also {
        it.orientation = LinearLayout.VERTICAL
        it.setPadding(0, 20.dp(), 0, 0)
    }
    init {
        overScrollMode = OVER_SCROLL_NEVER
        addView(ll)
    }
    fun addCell() = FormulaCell(context).also { cell ->
        cell.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 20.dp())
        }
        cell.inputFV.apply{ onEnter += { _, _ -> clearAllCaretPositionsExcept(this) } }
        cell.outputFV.apply{ onEnter += { _, _ -> clearAllCaretPositionsExcept(this) } }
        ll.addView(cell)
    }

    private fun clearAllCaretPositionsExcept(fvn: FormulaView?) {
        fvs.forEach { fv -> if (fv != fvn) fv.clearCaretPositions() }
    }

    var currentFV: FormulaView? = null
    val realCurrentFV
        get() = fvs.firstOrNull { fv -> fv.caret.positions.isNotEmpty() }

    val fvs
        get() = ll.children.flatMap { v -> (v as FormulaCell).children }.filterIsInstance<FormulaView>()

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentFV = fvs.firstOrNull { fv ->
                    val r = Rect()
                    fv.getDrawingRect(r)
                    offsetDescendantRectToMyCoords(fv, r)
                    r.contains(e.x.toInt(), e.y.toInt() + scrollY)
                }
                Log.d("cont", currentFV?.input?.ch?.size.toString())
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentFV?.canMove != true) return false
            }
        }
        onTouchEvent(e)
        return false
    }
}