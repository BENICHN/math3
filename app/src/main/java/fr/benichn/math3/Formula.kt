package fr.benichn.math3

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.minus
import androidx.core.graphics.plus

data class MeasuredPath(
    val path: Path,
    val w: Float,
    val h: Float,
)

abstract class Box {
    val onBoundsChanged = Callback<Box, ValueChangedEvent<RectF>>()
    var bounds: RectF = RectF(0f,0f,0f,0f)
        protected set(value) {
            val old = field
            field = value
            onBoundsChanged(this, ValueChangedEvent(old, value))
        }
    var origin: PointF = PointF()
        set(value) {
            val diff = value - field
            field = value
            bounds += diff
        }

    var path: Path = Path()
        protected set

    protected val children = mutableListOf<Box>()
    operator fun get(i: Int) = children[i]
    operator fun iterator() = children.iterator()
    val count
        get() = children.size

    fun drawOnCanvas(canvas: Canvas) {
        canvas.translate(origin.x, origin.y)
        canvas.drawPath(path, FormulaView.cyan)
        canvas.drawRect(bounds-origin, FormulaView.red)
        for (b in children) {
            b.drawOnCanvas(canvas)
        }
        canvas.translate(-origin.x, -origin.y)
    }

    // open fun dispose() {
    //     onBoundsChanged.clear()
    //     for (b in children) {
    //         b.dispose()
    //     }
    // }

    companion object {
        fun getTextPathAndSize(textSize: Float, text: String): MeasuredPath {
            val res = Path()
            val paint = Paint()
            paint.typeface = App.instance.resources.getFont(R.font.source_code_pro_light)
            paint.textSize = textSize
            Log.d("mes", paint.measureText(text).toString())
            // val widths = FloatArray(text.length)
            // paint.getTextWidths(text, widths)
            // Log.d("metrics", paint.fontMetrics.let { "${it.top} ~ ${it.ascent} ~ ${it.descent} ~ ${it.bottom} ~ ${it.leading}" })
            // Log.d("metrics", "${paint.fontSpacing} ~ ${paint.letterSpacing}")
            // Log.d("metrics", widths.joinToString())
            paint.getTextPath(text, 0, text.length, 0f, 0f-(paint.fontMetrics.top+paint.fontMetrics.bottom)/2, res)
            return MeasuredPath(res, paint.measureText(text), paint.fontMetrics.top)
        }
        fun getPathBounds(path: Path): RectF {
            val res = RectF()
            path.computeBounds(res, false)
            return res
        }
    }
}

class FormulaText(val string: String): Box() {
    init {
        val (p, w, h) = getTextPathAndSize(140f, string)
        path = p
        bounds = RectF(0f, -h/2, w, h/2)
    }
}

class FormulaSequence: Box() {
    private fun computeBounds() {
        val res = RectF()
        for (b in children) {
            res.union(b.bounds)
        }
        bounds = res
    }

    fun addBox(b: Box) {
        b.origin = PointF(children.lastOrNull()?.bounds?.right ?: 0f, 0f)
        b.onBoundsChanged += { s, e ->
            val i = children.indexOf(s)
            if (i+1 < children.size) {
                children[i+1].origin += PointF(e.new.right - e.old.right, 0f)
            } else {
                computeBounds()
            }
        }
        children.add(b)
        computeBounds()
    }
    fun removeBoxAt(i: Int) {
        val b = children[i]
        children.removeAt(i)
        // b.dispose()
        if (i < children.size) {
            children[i].origin -= PointF(b.bounds.width(), 0f)
        } else {
            computeBounds()
        }
    }
    fun removeLastBox() { if (children.isNotEmpty()) { removeBoxAt(children.size - 1) } }
    fun removeBox(b: Box) = removeBoxAt(children.indexOf(b))
}