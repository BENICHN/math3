package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.minus
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.types.callback.ObservableProperty
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.math.ceil

open class FormulaViewer(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var originRP by ObservableProperty(this, RectPoint.NAN).apply {
        onChanged += { _, _ ->
            invalidate()
        }
    }

    private val origin
        get() = originRP.get(Rect(0, 0, width, height))

    var fitToBox by ObservableProperty(this, false).apply {
        onChanged += { _, _ ->
            requestLayout()
        }
    }

    override fun invalidate() {
        MainScope().launch {
            super.invalidate()
        }
    }

    override fun requestLayout() {
        MainScope().launch {
            super.requestLayout()
        }
    }

    protected abstract inner class FormulaViewerAction : TouchAction({ it - origin })

    protected open val initialBoxTransformers: Array<BoundsTransformer>
        get() = arrayOf()

    protected val box = TransformerFormulaBox(transformers = initialBoxTransformers)
    var child
        get() = box.child
        set(value) { box.child = value }

    protected var touchAction: TouchAction? by ObservableProperty<FormulaViewer, TouchAction?>(this, null) { _, e ->
        e.new?.apply {
            onFinished += { _, _ -> touchAction = null }
            onReplaced += { _, ev -> touchAction = ev.new }
        }
    }

    private fun runTouchAction(e: MotionEvent) {
        touchAction?.also {
            it.onTouchEvent(e)
            if (touchAction != it) { // en cas de remplacement
                runTouchAction(e)
            }
        }
    }

    protected open fun createTouchAction(e: MotionEvent) {
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Log.d("nump", e.toString())
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                createTouchAction(e)
            }
        }
        runTouchAction(e)
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (fitToBox) {
            box.realBounds.run {
                setMeasuredDimension(
                    ceil(width()).toInt(),
                    ceil(height()).toInt()
                )
            }
        } else super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        origin.run { canvas.translate(x, y) }
        box.drawOnCanvas(canvas)
    }

    init {
        isClickable = true
        setWillNotDraw(false)
        box.onBoundsChanged += { _, _ ->
            if (fitToBox) requestLayout()
        }
        box.onPictureChanged += { _, _ ->
            invalidate()
        }
    }
}