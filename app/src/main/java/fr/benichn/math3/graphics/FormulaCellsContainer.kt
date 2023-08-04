package fr.benichn.math3.graphics

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.children
import fr.benichn.math3.Utils.Companion.dp

class FormulaCellsContainer(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    private val ll = LinearLayout(context).also {
        it.orientation = LinearLayout.VERTICAL
        it.setPadding(0, CELLS_SPACING, 0, 0)
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
            setMargins(0, 0, 0, CELLS_SPACING)
        }
        cell.inputFV.apply{ onEnter += { _, _ -> clearAllCaretPositionsExcept(this) } }
        cell.outputFV.apply{ onEnter += { _, _ -> clearAllCaretPositionsExcept(this) } }
        ll.addView(cell)
    }

    private fun clearAllCaretPositionsExcept(fvn: FormulaView?) {
        fvs.forEach { fv -> if (fv != fvn) fv.clearCaretPositions() }
    }

    val realCurrentFV
        get() = fvs.firstOrNull { fv -> fv.caret.positions.isNotEmpty() }

    // val realCurrentCell
    //     get() = ll.children.firstOrNull { v -> (v as FormulaCell).fvs.any { fv -> fv.caret.positions.isNotEmpty() } }

    val fvs
        get() = ll.children.flatMap { v -> (v as FormulaCell).fvs }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (realCurrentFV?.canMove == false) return false
            }
        }
        onTouchEvent(e)
        return false
    }

    companion object {
        val CELLS_SPACING = 10.dp()
    }
}