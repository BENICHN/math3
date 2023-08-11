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
import fr.benichn.math3.graphics.types.Orientation
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.graphics.types.TouchActionHandler
import fr.benichn.math3.numpad.types.Direction
import kotlin.math.abs

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

    val fvs
        get() = ll.children.flatMap { v -> (v as FormulaCell).fvs }

    val touchActionHandler = object : TouchActionHandler() {
        override fun createTouchAction(e: MotionEvent): TouchAction = FCCAction()
    }

    var movingOrientation: Orientation? = null

    private inner class FCCAction : TouchAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
        }

        override fun onMove() {
            movingOrientation = movingOrientation ?: if (abs(prim.totalAbsDiff.x) > abs(prim.totalAbsDiff.y)) Orientation.H else Orientation.V
        }

        override fun onUp() {
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish() {
            movingOrientation = null
        }
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        touchActionHandler.onTouchEvent(e)
        return super.dispatchTouchEvent(e)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        return when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (fvs.all { it.movingState == FormulaView.MovingState.NONE }) { // cannot scroll
                    false
                } else { // can scroll
                    if (movingOrientation == null || fvs.any { it.movingState == FormulaView.MovingState.PINCHING }) {
                        onTouchEvent(e)
                        false
                    } else movingOrientation == Orientation.V
                }
            }
            else -> super.onInterceptTouchEvent(e)
        }
    }

    companion object {
        val CELLS_SPACING = 10.dp()
    }
}