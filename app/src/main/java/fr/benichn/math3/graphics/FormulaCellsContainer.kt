package fr.benichn.math3.graphics

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.children
import fr.benichn.math3.R
import fr.benichn.math3.Utils.dp
import fr.benichn.math3.WolframEngine
import fr.benichn.math3.graphics.types.Orientation
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.graphics.types.TouchActionHandler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.math.abs

class FormulaCellsContainer(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    init {
        overScrollMode = OVER_SCROLL_NEVER
        LayoutInflater.from(context).inflate(R.layout.formula_cells_container, this, true)
    }

    private val ll = findViewById<LinearLayout>(R.id.ll)
    private val addButton = findViewById<ImageButton>(R.id.addButton).apply {
        setOnClickListener {
            addCell()
        }
    }

    val engine = WolframEngine().apply {
        MainScope().launch {
            start()
        }
    }
    fun addCell(i: Int = ll.childCount-1) = FormulaCell(context).also { cell ->
        cell.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            // setMargins(0, 0, 0, CELLS_SPACING)
        }
        cell.inputFV.apply{ onEnter += { _, _ -> clearAllCaretPositionsExcept(this) } }
        cell.outputFV.apply{ onEnter += { _, _ -> clearAllCaretPositionsExcept(this) } }
        cell.deleteButton.setOnClickListener { onCellClose(it) }
        cell.addAboveButton.setOnClickListener { onCellAddAbove(it) }
        cell.addBelowButton.setOnClickListener { onCellAddBelow(it) }
        cell.evalAboveButton.setOnClickListener { onCellEvalAbove(it) }
        cell.evalBelowButton.setOnClickListener { onCellEvalBelow(it) }
        cell.evalButton.setOnClickListener { onCellEval(it) }
        fvs.forEach { it.clearCaretPositions() }
        cell.inputFV.setCaretOnEnd()
        ll.addView(cell, i)
    }

    private fun removeCell(fc: FormulaCell) {
        if (fc.evalState == FormulaCell.EvalState.Ready) {
            ll.removeView(fc)
        }
    }

    private fun getCellsAbove(fc: FormulaCell) = ll.children.indexOf(fc).let { i ->
        (0 .. i).map { j -> ll.getChildAt(j) as FormulaCell }
    }

    private fun getCellsBelow(fc: FormulaCell) = ll.children.indexOf(fc).let { i ->
        (i until ll.childCount-1).map { j -> ll.getChildAt(j) as FormulaCell }
    }

    private fun onCellClose(v: View) {
        val fc = v.parent.parent.parent as FormulaCell
        removeCell(fc)
    }

    private fun onCellAddAbove(v: View) {
        val fc = v.parent.parent.parent as FormulaCell
        val i = ll.children.indexOf(fc)
        addCell(i)
    }

    private fun onCellAddBelow(v: View) {
        val fc = v.parent.parent.parent as FormulaCell
        val i = ll.children.indexOf(fc)
        addCell(i+1)
    }

    private fun onCellEvalAbove(v: View) {
        val fc = v.parent.parent.parent as FormulaCell
        getCellsAbove(fc).forEach { c -> c.evalInput(engine) }
    }

    private fun onCellEvalBelow(v: View) {
        val fc = v.parent.parent.parent as FormulaCell
        getCellsBelow(fc).forEach { c -> c.evalInput(engine) }
    }

    private fun onCellEval(v: View) {
        val fc = v.parent.parent.parent as FormulaCell
        fc.evalInput(engine)
    }

    fun evalInputCreateCell(fc: FormulaCell) {
        val i = ll.children.indexOf(fc)
        if (fc.evalState == FormulaCell.EvalState.Ready) {
            fc.evalInput(engine)
            if (i == ll.childCount-2) addCell()
            else {
                fvs.forEach { it.clearCaretPositions() }
                (ll.getChildAt(i+1) as FormulaCell).inputFV.setCaretOnEnd()
            }
        }
    }

    private fun clearAllCaretPositionsExcept(fvn: FormulaView?) {
        fvs.forEach { fv -> if (fv != fvn) fv.clearCaretPositions() }
    }

    val realCurrentFV
        get() = fvs.firstOrNull { fv -> fv.caret.positions.isNotEmpty() }

    val cells
        get() = ll.childCount.let { n -> ll.children.filterIndexed { i, _ -> i != n-1 } }.map { it as FormulaCell }

    val fvs
        get() = cells.flatMap { it.fvs }

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