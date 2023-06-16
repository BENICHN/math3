package fr.benichn.math3

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.health.connect.datatypes.units.Length
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import androidx.core.graphics.times
import androidx.core.graphics.unaryMinus
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class BoxProperty<S: FormulaBox, T>(private val source: S, private val defaultValue: T) : ReadWriteProperty<S, T> {
    private var field = defaultValue
    val onChanged = VCC<S, T>(source)
    fun get() = field
    fun set(value: T) {
        val old = field
        field = value
        onChanged(old, value)
        source.updateGraphics()
    }
    private val connections = mutableListOf<CallbackLink<*,*>>()
    fun <A, B> connect(callback: VCC<A, B>, mapper: (A, ValueChangedEvent<B>) -> T) {
        connections.add(CallbackLink(callback) { s, e ->
            set(mapper(s, e))
        })
    }
    fun <A, B> disconnect(callback: VCC<A, B>) {
        connections.removeIf {
            if (it.callback == callback) {
                it.disconnect()
                true
            } else {
                false
            }
        }
        if (!isConnected) {
            set(defaultValue)
        }
    }
    val isConnected
        get() = connections.isEmpty()
    override fun getValue(thisRef: S, property: KProperty<*>): T = get()
    override fun setValue(thisRef: S, property: KProperty<*>, value: T) = set(value)
}
data class CallbackLink<S, T>(val callback: VCC<S, T>, val listener: (S, ValueChangedEvent<T>) -> Unit) {
    init {
        callback += listener
    }
    fun disconnect() {
        callback -= listener
    }
}

data class FormulaGraphics(val path: Path, val paint: Paint, val bounds: RectF) {
    constructor() : this(Path(), Paint(), RectF())
}

data class BoxTransform(val origin: PointF = PointF(), val scale: Float = 1f) {
    operator fun times(bt: BoxTransform) = BoxTransform(origin * bt.scale + bt.origin, scale * bt.scale) // [f*g](x) = g(f(x))
    val invert
        get() = BoxTransform(-origin/scale, 1/scale)
    fun applyOnCanvas(canvas: Canvas) {
        canvas.translate(origin.x, origin.y)
        canvas.scale(scale, scale)
    }
    fun applyOnRect(r: RectF): RectF = r * scale + origin
    companion object {
        fun xOffset(l: Float): BoxTransform = BoxTransform(PointF(l, 0f))
        fun yOffset(l: Float): BoxTransform = BoxTransform(PointF(0f, l))
        fun scale(a: Float): BoxTransform = BoxTransform(PointF(), a)
    }
}
operator fun PointF.times(scale: Float): PointF = PointF(x*scale,y*scale)
operator fun PointF.div(scale: Float): PointF = PointF(x/scale,y/scale)

data class Range(val start: Float = 0f, val end: Float = 0f)

enum class Orientation { H, V }

data class RectPoint(val tx: Float, val ty: Float) {
    init {
        assert((tx in 0.0..1.0 && ty in 0.0..1.0) || (tx.isNaN() && ty.isNaN()))
    }

    val isNaN
        get() = tx.isNaN()

    fun get(r: RectF): PointF = if (isNaN) {
        PointF(Float.NaN, Float.NaN)
    } else {
        PointF(
            r.left + tx * (r.right - r.left),
            r.top + ty * (r.bottom - r.top)
        )
    }

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
        val NAN = RectPoint(Float.NaN, Float.NaN)
    }
}

open class FormulaBox : Iterable<FormulaBox> {
    var parent: FormulaBox? = null
        private set
    private val children = mutableListOf<FormulaBox>()
    protected open fun addBox(b: FormulaBox) = addBox(children.size, b)
    protected open fun addBox(i: Int, b: FormulaBox) {
        assert(b.parent == null)
        children.add(i, b)
        b.parent = this
    }
    protected open fun removeLastBox() { if (children.isNotEmpty()) removeBoxAt(children.size - 1) }
    protected open fun removeBox(b: FormulaBox) = removeBoxAt(children.indexOf(b))
    protected open fun removeBoxAt(i: Int) {
        val b = children[i]
        assert(b.parent == this)
        disconnectFrom(b)
        children.removeAt(i)
        b.parent = null
    }

    private val connections = mutableListOf<CallbackLink<*,*>>()
    fun <A, B> connect(callback: VCC<A, B>, f: (A, ValueChangedEvent<B>) -> Unit) {
        connections.add(CallbackLink(callback, f))
    }
    fun disconnectFrom(b: FormulaBox) {
        connections.removeIf {
            if (it.callback.source == b) {
                it.disconnect()
                true
            } else {
                false
            }
        }
    }

    override fun iterator(): Iterator<FormulaBox> = children.iterator()
    operator fun get(i: Int) = children[i]
    val count
        get() = children.size

    val onTransformChanged = VCC<FormulaBox, BoxTransform>(this)
    var transform: BoxTransform = BoxTransform()
        private set(value) {
            val old = field
            field = value
            onTransformChanged(old, value)
            updateAccTransform()
        }

    private fun updateAccTransform() {
        accTransform = transform * parent!!.accTransform
    }

    val onAccTransformChanged = VCC<FormulaBox, BoxTransform>(this)
    var accTransform: BoxTransform = BoxTransform()
        private set(value) {
            val old = field
            field = value
            onAccTransformChanged(old, value)
            for (b in children) {
                b.updateAccTransform()
            }
        }

    protected fun setChildTransform(i: Int, bt: BoxTransform) {
        children[i].transform = bt
    }
    protected fun setChildTransform(b: FormulaBox, bt: BoxTransform) = setChildTransform(children.indexOf(b), bt)
    protected fun modifyChildTransform(i: Int, t: (BoxTransform) -> BoxTransform) = setChildTransform(i, t(children[i].transform))
    protected fun setChildTransform(b: FormulaBox, t: (BoxTransform) -> BoxTransform) = setChildTransform(b, t(b.transform))

    // protected open fun applyWidth(w: Float) {
    //     // val r = w/2
    //     // applyBounds(RectF(-r, bounds.top, r, bounds.bottom))
    // }
    // protected open fun applyHeight(h: Float) {
    //     // val r = h/2
    //     // applyBounds(RectF(bounds.left, -r, bounds.right, r))
    // }
    // // protected open fun applyBounds(bounds: RectF) {
    // // }
    // protected fun applySide(l: Float, o: Orientation) = when (o) {
    //     Orientation.H -> applyWidth(l)
    //     Orientation.V -> applyHeight(l)
    // }

    val onGraphicsChanged = VCC<FormulaBox, FormulaGraphics>(this)
    var graphics: FormulaGraphics = FormulaGraphics()
        private set(value) {
            val old = field
            field = value
            onGraphicsChanged(old, value)
            if (old.path != value.path) onPathChanged(old.path, value.path)
            if (old.paint != value.paint) onPaintChanged(old.paint, value.paint)
            if (old.bounds != value.bounds) onBoundsChanged(old.bounds, value.bounds)
        }
    val onBoundsChanged = VCC<FormulaBox, RectF>(this)
    val bounds
        get() = graphics.bounds
    val onPathChanged = VCC<FormulaBox, Path>(this)
    val path
        get() = graphics.path
    val onPaintChanged = VCC<FormulaBox, Paint>(this)
    val paint
        get() = graphics.paint

    val realBounds
        get() = transform.applyOnRect(bounds)
    val accRealBounds
        get() = accTransform.applyOnRect(bounds)

    protected open fun generateGraphics(): FormulaGraphics = FormulaGraphics(
        path,
        paint,
        Utils.sumOfRects(map { it.realBounds })
    )

    fun updateGraphics() {
        graphics = generateGraphics()
    }

    fun getSide(o: Orientation) = when (o) {
        Orientation.H -> bounds.width()
        Orientation.V -> bounds.height()
    }

    protected var isProcessing = false
        set(value) {
            field = value
            if (!value && hasChangedPicture) {
                onPictureChanged(Unit)
            }
        }
    private var hasChangedPicture = false
    val onPictureChanged = Callback<FormulaBox, Unit>(this)

    fun drawOnCanvas(canvas: Canvas) {
        transform.applyOnCanvas(canvas)
        canvas.drawPath(path, paint)
        // canvas.drawRect(bounds, FormulaView.red)
        for (b in children) {
            b.drawOnCanvas(canvas)
        }
        transform.invert.applyOnCanvas(canvas)
    }

    init {
        onPictureChanged += { _, _ ->
            if (isProcessing) {
                hasChangedPicture = true
            } else {
                parent?.onPictureChanged?.invoke(Unit)
            }
        }
        onGraphicsChanged += { _, e ->
            if (e.old.path != e.new.path || e.old.paint != e.new.paint) {
                onPictureChanged(Unit)
            }
        }
        onTransformChanged += { _, _ ->
            onPictureChanged(Unit)
        }
    }

    companion object {
        const val DEFAULT_TEXT_SIZE = 96f
        const val DEFAULT_TEXT_WIDTH = DEFAULT_TEXT_SIZE * 3/5
        const val DEFAULT_LINE_WIDTH = 3f
    }
}

open class EditableFormulaBox : FormulaBox() {
    public override fun addBox(i: Int, b: FormulaBox) = super.addBox(i, b)
    public override fun addBox(b: FormulaBox) = super.addBox(b)
    public override fun removeBoxAt(i: Int) = super.removeBoxAt(i)
    public override fun removeBox(b: FormulaBox) = super.removeBox(b)
    public override fun removeLastBox() = super.removeLastBox()
}

class TextFormulaBox(text: String = "") : FormulaBox() {
    val dlgText = BoxProperty(this, text)
    var text by dlgText

    init {
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        updateGraphics()
    }

    override fun generateGraphics(): FormulaGraphics {
        val (p, w, h) = Utils.getTextPathAndSize(DEFAULT_TEXT_SIZE, text)
        val bounds = RectF(0f, -h / 2, w, h / 2)
        return FormulaGraphics(p, paint, bounds)
    }
}

class LineFormulaBox(orientation: Orientation = Orientation.V, length: Float = DEFAULT_TEXT_SIZE) : FormulaBox() {
    val dlgOrientation = BoxProperty(this, orientation)
    var orientation by dlgOrientation

    val dlgRange = BoxProperty(this, Range())
    var range by dlgRange

    init {
        paint.color = Color.WHITE
        paint.strokeWidth = DEFAULT_LINE_WIDTH
        paint.style = Paint.Style.STROKE
        updateGraphics()
    }

    override fun generateGraphics(): FormulaGraphics = when (orientation) {
        Orientation.H -> {
            FormulaGraphics(
                Path().also {
                    it.moveTo(range.start, 0f)
                    it.lineTo(range.end, 0f)
                },
                paint,
                RectF(range.start, 0f, range.end, 0f)
            )
        }

        Orientation.V -> {
            FormulaGraphics(
                Path().also {
                    it.moveTo(0f, range.start)
                    it.lineTo(0f, range.end)
                },
                paint,
                RectF(0f, range.start, 0f, range.end)
            )
        }
    }
}

class SequenceFormulaBox : EditableFormulaBox() {
    override fun addBox(i: Int, b: FormulaBox) {
        super.addBox(i, b)
        setChildTransform(i, BoxTransform.xOffset((if (i == 0) 0f else this[i-1].let { it.transform.origin.x + it.bounds.right }) - b.bounds.left))
        connect(b.onBoundsChanged) { s, e ->
            offsetFrom(i, e.old.left-e.new.left)
            offsetFrom(i+1, e.new.right-e.old.right)
            updateGraphics()
        }
        offsetFrom(i+1, b.bounds.width())
        updateGraphics()
    }
    override fun removeBoxAt(i: Int) {
        val b = this[i]
        super.removeBoxAt(i)
        offsetFrom(i, b.bounds.width())
        updateGraphics()
    }

    private fun offsetFrom(i: Int, l: Float) {
        for (j in i until count) {
            modifyChildTransform(j) { it * BoxTransform.xOffset(l) }
        }
    }
}

class AlignFormulaBox(child: FormulaBox = FormulaBox(), rectPoint: RectPoint = RectPoint.NAN) : FormulaBox() {
    val dlgChild = BoxProperty(this, child).also {
        it.onChanged += { s, e ->
            removeBox(e.old)
            addBox(e.new)
        }
    }
    var child by dlgChild

    val dlgRectPoint = BoxProperty(this, rectPoint).also {
        it.onChanged += { _, _ ->
            alignChild()
        }
    }
    var rectPoint: RectPoint by dlgRectPoint

    init {
        addBox(child)
        updateGraphics()
    }

    override fun addBox(i: Int, b: FormulaBox) {
        super.addBox(i, b)
        connect(b.onBoundsChanged) { s, e ->
            alignChild()
            updateGraphics()
        }
        alignChild()
    }

    private fun alignChild() = setChildTransform(
        0,
        BoxTransform(-(if (!rectPoint.isNaN) rectPoint.get(child.bounds) else PointF()))
    )
}

class FractionFormulaBox : FormulaBox() {
    val bar = LineFormulaBox(Orientation.H)
    val num = AlignFormulaBox(SequenceFormulaBox(), RectPoint.BOTTOM_CENTER)
    val den = AlignFormulaBox(SequenceFormulaBox(), RectPoint.TOP_CENTER)
    init {
        addBox(bar)
        addBox(num)
        addBox(den)
        bar.range = getBarWidth()
        bar.dlgRange.connect(num.onBoundsChanged) { _, _ -> getBarWidth() }
        bar.dlgRange.connect(den.onBoundsChanged) { _, _ -> getBarWidth() }
        connect(num.onBoundsChanged) { s, e ->
            updateGraphics()
        }
        connect(den.onBoundsChanged) { s, e ->
            updateGraphics()
        }
    }

    private fun getBarWidth(): Range {
        val w = max(num.bounds.width(), den.bounds.width()) + DEFAULT_TEXT_WIDTH / 4
        val r = w/2
        return Range(-r, r)
    }
}