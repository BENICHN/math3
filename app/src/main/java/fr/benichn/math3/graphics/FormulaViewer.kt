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
import fr.benichn.math3.graphics.types.TouchActionHandler
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

    abstract inner class FormulaViewerAction : TouchAction({ it - origin })

    protected open val initialBoxTransformers: Array<BoundsTransformer>
        get() = arrayOf()

    protected val box = TransformerFormulaBox(transformers = initialBoxTransformers)
    var child
        get() = box.child
        set(value) { box.child = value }

    protected val touchActionHandler = object : TouchActionHandler() {
        override fun createTouchAction(e: MotionEvent) =
            this@FormulaViewer.createTouchAction(e)
    }

    protected open fun createTouchAction(e: MotionEvent): TouchAction? = null
    protected val touchAction
        get() = touchActionHandler.touchAction

    override fun onTouchEvent(e: MotionEvent) =
        touchActionHandler.onTouchEvent(e)

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