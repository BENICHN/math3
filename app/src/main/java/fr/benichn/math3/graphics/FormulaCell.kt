package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import fr.benichn.math3.Engine
import fr.benichn.math3.formulas.FormulaGroupedToken.Companion.readGroupedToken
import fr.benichn.math3.graphics.boxes.IntegralFormulaBox
import fr.benichn.math3.graphics.boxes.SequenceFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.types.callback.Callback
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.StringReader
import fr.benichn.math3.types.callback.invoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FormulaCell(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    class StopButton(context: Context, attrs: AttributeSet? = null) : FormulaViewer(context, attrs) {
        override val initialBoxTransformers: Array<BoundsTransformer>
            get() = arrayOf(BoundsTransformer.Align(RectPoint.CENTER))

        init {
            originRP = RectPoint.CENTER
            child = TextFormulaBox("▩", big = true)
        }

        private val notifyClicked = Callback<StopButton, Unit>(this)
        val onClicked = notifyClicked.Listener()

        private inner class StopButtonTouchAction : FormulaViewerAction() {
            override fun onDown() {
                if (child.accRealBounds.contains(prim.lastPosition.x, prim.lastPosition.y)) {
                    child.background = Color.LTGRAY
                } else finish()
            }

            override fun onLongDown() {
            }

            override fun onMove() {
                child.background = if (child.accRealBounds.contains(prim.lastPosition.x, prim.lastPosition.y)) {
                    Color.LTGRAY
                } else Color.TRANSPARENT
            }

            override fun onUp() {
                if (child.accRealBounds.contains(prim.lastPosition.x, prim.lastPosition.y)) {
                    notifyClicked()
                }
            }

            override fun onPinchDown() {
            }

            override fun onPinchMove() {
            }

            override fun onPinchUp() {
            }

            override fun beforeFinish(replacement: TouchAction?) {
                child.background = Color.TRANSPARENT
            }
        }

        override fun createTouchAction(e: MotionEvent) {
            touchAction = StopButtonTouchAction()
        }
    }

    val inputFV = FormulaView(context)
    val outputFV = FormulaView(context).apply {
        isReadOnly = true
        scale = 0.8f
        magneticScale = 0.8f
        input.addBoxes(IntegralFormulaBox().apply { integrand.addBoxes(TextFormulaBox("output")) })
    }
    private val textView = TextView(context).apply {
        visibility = GONE
        setTextIsSelectable(true)
        setBackgroundColor(FormulaView.defaultBackgroundColor)
    }
    private val stopButton = StopButton(context).apply {
        visibility = GONE
        setBackgroundColor(FormulaView.defaultBackgroundColor)
    }
    private val fl = FrameLayout(context).also {
        it.addView(outputFV)
        it.addView(textView)
        it.addView(stopButton)
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

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        canvas.drawLine(0f, 0f, width.toFloat(), 0f, borderPaint)
        canvas.drawLine(0f, height.toFloat(), width.toFloat(), height.toFloat(), borderPaint)
    }

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
            val v = if (fv == outputFV) fl else fv
            val r = Rect()
            v.getDrawingRect(r)
            offsetDescendantRectToMyCoords(v, r)
            e.offsetLocation(-r.left.toFloat(), -r.top.toFloat())
            v.dispatchTouchEvent(MotionEvent.obtain(e))
        } ?: super.dispatchTouchEvent(e)
    }

    private var currentEngine: Engine? = null

    fun computeInput(engine: Engine) {
        if (currentEngine != null) return
        currentEngine = engine
        val command = inputFV.input.toWolfram()
        engine.enqueue(this@FormulaCell, command)?.also { f ->
            textView.text = ""
            outputFV.input.clear()
            MainScope().launch {
                f.collect { s ->
                    textView.visibility = GONE
                    withContext(Dispatchers.Default) {
                        outputFV.input.addBoxes(s.map { c ->
                            when (c) {
                                '\n' -> SequenceFormulaBox.LineStart()
                                else -> TextFormulaBox(
                                    c.toString()
                                )
                            }
                        })
                    }
                    val sr = StringReader(s)
                    val gtk = sr.readGroupedToken()
                    Log.d("gtk", gtk.toString())
                }
                currentEngine = null
            }
        } ?: run {
            currentEngine = null
        }
    }

    fun abortComputation() {
        currentEngine?.let { engine ->
            currentEngine = null
            MainScope().launch {
                engine.abort(this@FormulaCell)
            }
        }
    }

    init {
        orientation = VERTICAL
        // inputFV.onScaleChanged += { s, e ->
        //     if (syncScales && currentFV == s) {
        //         val ratio = e.new / e.old
        //         outputFV.scale *= ratio
        //     }
        // }
        // outputFV.onScaleChanged += { s, e ->
        //     if (syncScales && currentFV == s) {
        //         val ratio = e.new / e.old
        //         inputFV.scale *= ratio
        //     }
        // }
        stopButton.onClicked += { _, _ ->
            abortComputation()
        }
        addView(inputFV)
        addView(hline(2, Color.GRAY))
        addView(fl)
    }

    companion object {
        val borderPaint = Paints.stroke(2f, Color.GRAY)
    }
}