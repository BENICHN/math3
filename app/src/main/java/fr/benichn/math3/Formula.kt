package fr.benichn.math3

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.util.SizeF
import androidx.core.graphics.minus
import androidx.core.graphics.plus

/*

abstract class Box {
    var parent: Box? = null
        private set
    val onBoundsChanged = Callback<Box, ValueChangedEvent<RectF>>()
    var bounds: RectF = RectF(0f,0f,0f,0f)
        protected set(value) {
            val old = field
            field = value
            if (old != value) {
                onBoundsChanged(this, ValueChangedEvent(old, value))
            }
        }
    var origin: PointF = PointF()
        set(value) {
            val diff = value - field
            field = value
            bounds += diff
        }

    var path: Path = Path()
        protected set

    protected var minSize = SizeF(0f, 0f)

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

    open fun applyBounds(newBounds: RectF) {

    }

    fun resizeBounds(orientation: Orientation, newLength: Float) {
        val l = if (orientation == Orientation.H) {
            if (newLength < minSize.width) minSize.width
            else newLength
        } else {
            if (newLength < minSize.height) minSize.height
            else newLength
        }
        val r = l / 2
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
    }
}

class FormulaText(val string: String): Box() {
    init {
        val (p, w, h) = Utils.getTextPathAndSize(140f, string)
        path = p
        bounds = RectF(0f, -h/2, w, h/2)
    }
}

class FormulaLine(val orientation: Orientation): Box() {
    init {
        bounds = if (orientation == Orientation.H) RectF(-1f, -10f, 1f, 10f) else RectF(-10f, -1f, 10f, 1f)
    }
    override fun applyBounds(newBounds: RectF) {
        if (orientation == Orientation.H) {
            path = Path()
            path.moveTo(newBounds.left, 0f)
            path.lineTo(newBounds.right, 0f)
        } else {
            path = Path()
            path.moveTo(0f, newBounds.top)
            path.lineTo(0f, newBounds.bottom)
        }
        bounds = newBounds
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
    fun apply() {
        onSourceBoundsChanged(RectF(), source.bounds)
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

    protected open fun addBox(b: Box) {
        children.add(b)
        computeBounds()
    }

    protected open fun removeBoxAt(i: Int) {
        val b = children[i]
        children.removeAt(i)
        removeRulesWithDest(b)
        removeRulesWithSource(b)
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
    init {
        minSize = SizeF(84f, 140f)
        bounds = RectF(0f, -minSize.height/2, minSize.width, minSize.height/2)
    }

    public override fun addBox(b: Box) {
        b.origin = PointF(children.lastOrNull()?.bounds?.right ?: 0f, 0f)
        b.onBoundsChanged += { s, e ->
            val i = children.indexOf(s)
            if (i+1 < children.size) {
                children[i+1].origin += PointF(e.new.right - e.old.right, 0f)
            } else {
                computeBounds()
            }
        }
        super.addBox(b)
    }
    public override fun removeBoxAt(i: Int) {
        val b = children[i]
        super.removeBoxAt(i)
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

class FractionFormula : FormulaGroup() {
    val num = FormulaSequence()
    val den = FormulaSequence()
    val line = FormulaLine(Orientation.H)
    init {
        addBox(line)
        addBox(num)
        addBox(den)
        addRule(BoundsRules.Sync(num, line, Orientation.H, Orientation.H))
        addRule(BoundsRules.Place(line, num, RectPoint.TOP_CENTER, RectPoint.BOTTOM_CENTER).also { it.apply() })
        addRule(BoundsRules.Place(line, den, RectPoint.BOTTOM_CENTER, RectPoint.TOP_CENTER).also { it.apply() })
    }
}

 */