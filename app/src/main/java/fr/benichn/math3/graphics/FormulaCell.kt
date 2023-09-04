package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import fr.benichn.math3.CommandOutput
import fr.benichn.math3.Engine
import fr.benichn.math3.R
import fr.benichn.math3.Utils.dp
import fr.benichn.math3.formulas.FormulaGroupedToken.Companion.readGroupedTokenFlattened
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.toBoxes
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.types.CellMode
import fr.benichn.math3.types.callback.ObservableProperty
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.StringReader

class FormulaCell(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    init {
        LayoutInflater.from(context).inflate(R.layout.formula_cell, this, true)
    }
    val inputFV = findViewById<FormulaView>(R.id.input_fv)
    val outputFV = findViewById<FormulaView>(R.id.output_fv).apply {
        isReadOnly = true
    }
    val deleteButton = findViewById<ImageButton>(R.id.delete_btn)
    val addAboveButton = findViewById<ImageButton>(R.id.add_above_btn)
    val addBelowButton = findViewById<ImageButton>(R.id.add_below_btn)
    val evalAboveButton = findViewById<ImageButton>(R.id.eval_above_btn)
    val evalBelowButton = findViewById<ImageButton>(R.id.eval_below_btn)
    val evalButton = findViewById<ImageButton>(R.id.eval_btn)
    val abortButton = findViewById<ImageButton>(R.id.abort_btn).apply {
        setOnClickListener {
            abortComputation()
        }
    }

    fun hline(height: Int, color: Int) = View(context).apply {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            height
        )
        foreground = ColorDrawable(color)
    }
    fun vline(width: Int, color: Int) = View(context).apply {
        layoutParams = LayoutParams(
            width,
            LayoutParams.MATCH_PARENT
        )
        foreground = ColorDrawable(color)
    }

    // override fun dispatchDraw(canvas: Canvas) {
    //     super.dispatchDraw(canvas)
    //     canvas.drawLine(0f, 0f, width.toFloat(), 0f, borderPaint)
    //     canvas.drawLine(0f, height.toFloat(), width.toFloat(), height.toFloat(), borderPaint)
    // }

    val fvs = listOf(inputFV, outputFV)
    private var currentFV: FormulaView? = null

    private fun getFV(x: Float, y: Float) = fvs.firstOrNull { fv ->
        val r = Rect()
        fv.getDrawingRect(r)
        offsetDescendantRectToMyCoords(fv, r)
        r.contains(x.toInt(), y.toInt())
    }

    private var syncScales = false

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        // fvs.forEach { fv -> fv.dispatchTouchEvent(e) }
        // return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentFV = getFV(e.x, e.y)
                // if (currentFV == inputFV) Log.d("fv", "inp")
                // if (currentFV == outputFV) Log.d("fv", "otp")
            }
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                syncScales = (1 until e.pointerCount).any { i ->
                    val fv = getFV(e.getX(i), e.getY(i))
                    fv != null && fv != currentFV
                }
            }
        }
        return currentFV?.let { fv ->
            // val v = if (fv == outputFV) fl else fv
            val r = Rect()
            fv.getDrawingRect(r)
            offsetDescendantRectToMyCoords(fv, r)
            e.offsetLocation(-r.left.toFloat(), -r.top.toFloat())
            fv.dispatchTouchEvent(MotionEvent.obtain(e))
        } ?: super.dispatchTouchEvent(e)
    }

    sealed class EvalState {
        data object Ready : EvalState()
        data class Evaluating(val engine: Engine) : EvalState()
        data class Aborting(val engine: Engine) : EvalState()
    }

    var evalState by ObservableProperty<FormulaCell, EvalState>(this, EvalState.Ready).apply {
        onChanged += { _, e ->
            when (e.new) {
                is EvalState.Ready -> {
                    evalButton.visibility = VISIBLE
                    evalAboveButton.visibility = VISIBLE
                    evalBelowButton.visibility = VISIBLE
                    abortButton.visibility = GONE
                }
                is EvalState.Evaluating -> {
                    evalButton.visibility = GONE
                    evalAboveButton.visibility = GONE
                    evalBelowButton.visibility = GONE
                    abortButton.visibility = VISIBLE
                    abortButton.isEnabled = true
                }
                is EvalState.Aborting -> {
                    evalButton.visibility = GONE
                    evalAboveButton.visibility = GONE
                    evalBelowButton.visibility = GONE
                    abortButton.visibility = VISIBLE
                    abortButton.isEnabled = false
                }
            }
        }
    }

    fun evalInputInContainer() =
        (parent.parent as? FormulaCellsContainer)?.let { fcc ->
            evalInput(fcc.engine)
            true
        } ?: false

    fun evalInput(engine: Engine) {
        if (evalState !is EvalState.Ready) return
        evalState = EvalState.Evaluating(engine)
        val command = inputFV.input.toWolfram(inputFV.cellMode)
        engine.enqueue(this@FormulaCell, command)?.also { f ->
            outputFV.input.clear()
            outputFV.clearCaretPositions()
            (inputFV.layoutParams as LinearLayout.LayoutParams).setMargins(
                12.dp(), 6.dp(), 12.dp(), 0
            )
            outputFV.visibility = VISIBLE
            MainScope().launch {
                f.collect { co ->
                    outputFV.input.addBoxes(
                        when (co) {
                            CommandOutput.Null -> listOf()
                            CommandOutput.Aborted -> listOf(TextFormulaBox("\$Aborted").apply { foreground = Color.rgb(243, 156, 18) })
                            CommandOutput.Failed -> listOf(TextFormulaBox("\$Failed").apply { foreground = Color.rgb(192, 57, 43) })
                            is CommandOutput.Message -> co.value.toBoxes()
                            is CommandOutput.SVG -> co.value.toBoxes()
                            is CommandOutput.Typeset -> {
                                val sr = StringReader(co.value)
                                val gtk = sr.readGroupedTokenFlattened()
                                gtk.toBoxes(outputFV.cellMode)
                            }
                        }
                    )
                }
                evalState = EvalState.Ready
            }
        } ?: run {
            evalState = EvalState.Ready
        }
    }

    fun abortComputation() {
        (evalState as? EvalState.Evaluating)?.apply {
            evalState = EvalState.Aborting(engine)
            MainScope().launch {
                engine.abort(this@FormulaCell)
                evalState = EvalState.Ready
            }
        }
    }

    companion object {
        val borderPaint = Paints.stroke(2f, Color.GRAY)
    }
}