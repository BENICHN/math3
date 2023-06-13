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

    open fun applyBounds(bounds: RectF) {

    }

    fun resizeBounds(orientation: Orientation, newLength: Float) {
        val r = newLength / 2
        val new = if (orientation == Orientation.H) {
            RectF(origin.x - r, bounds.top, origin.x + r, bounds.bottom)
        } else {
            RectF(bounds.left, origin.y - r, bounds.right, origin.y + r)
        }
        applyBounds(new)
    }

    fun getSide(orientation: Orientation) = if (orientation == Orientation.H) bounds.width() else bounds.height()

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

abstract class BoundsRule(val source: Box, val dest: Box) {
    private val obc = { _: Box, e: ValueChangedEvent<RectF> -> onSourceBoundsChanged(e.old, e.new)}
    init {
        source.onBoundsChanged += obc
    }
    abstract fun onSourceBoundsChanged(old: RectF, new: RectF)
    fun stop() {
        source.onBoundsChanged -= obc
    }
}

enum class Orientation { H, V }

data class RectPoint(val tx: Float, val ty: Float) {
    init {
        assert(tx in 0.0..1.0)
        assert(ty in 0.0..1.0)
    }

    fun get(r: RectF): PointF = PointF(
        r.left + tx * (r.right - r.left),
        r.top + ty * (r.bottom - r.top)
    )

    companion object {
        val TOP_LEFT = RectPoint(0f, 0f)
        val TOP_RIGHT = RectPoint(1f, 0f)
        val BOTTOM_RIGHT = RectPoint(1f, 1f)
        val BOTTOM_LEFT = RectPoint(0f, 1f)
        val CENTER = RectPoint(0.5f,0.5f)
        val TOP_CENTER = RectPoint(0.5f, 0f)
        val BOTTOM_CENTER = RectPoint(0.5f, 1f)
        val CENTER_LEFT = RectPoint(0f, 0.5f)
        val CENTER_RIGHT = RectPoint(1f, 0.5f)
    }
}

class BoundsRules {
    class Sync(source: Box, dest: Box, val source_d: Orientation, val dest_d: Orientation) : BoundsRule(source, dest) {
        override fun onSourceBoundsChanged(old: RectF, new: RectF) {
            val l = source.getSide(source_d)
            dest.resizeBounds(dest_d, l)
        }
    }
    class Overflow(source: Box, dest: Box, val l: Float, val orientation: Orientation) : BoundsRule(source, dest) {
        override fun onSourceBoundsChanged(old: RectF, new: RectF) {
            dest.resizeBounds(orientation, dest.getSide(orientation) + 2*l)
        }
    }
    class Place(source: Box, dest: Box, val source_rp: RectPoint, val dest_rp: RectPoint) : BoundsRule(source, dest) {
        override fun onSourceBoundsChanged(old: RectF, new: RectF) {
            val source_p = source_rp.get(source.bounds)
            val dest_p = dest_rp.get(dest.bounds)
            val v = source_p - dest_p
            dest.origin += v
        }
    }

    companion object {
    }
}

open class FormulaGroup : Box() {
    private val boundsRules = mutableListOf<BoundsRule>()
    protected fun addRule(r: BoundsRule) {
        boundsRules.add(r)
    }
    protected fun removeRuleAt(i: Int) {
        boundsRules[i].stop()
        boundsRules.removeAt(i)
    }
    protected fun removeRule(r: BoundsRule) = removeRuleAt(boundsRules.indexOf(r))
    protected fun removeRulesWithSource(b: Box) {
        val rules = boundsRules.filter { it.source == b }
        for (r in rules) {
            removeRule(r)
        }
    }
    protected fun removeRulesWithDest(b: Box) {
        val rules = boundsRules.filter { it.dest == b }
        for (r in rules) {
            removeRule(r)
        }
    }

    protected fun computeBounds() {
        val res = RectF()
        for (b in children) {
            res.union(b.bounds)
        }
        bounds = res
    }
}

class FormulaSequence: FormulaGroup() {
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