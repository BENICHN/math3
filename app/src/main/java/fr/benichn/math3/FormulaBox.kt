package fr.benichn.math3

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.plus
import androidx.core.graphics.times
import androidx.core.graphics.unaryMinus
import fr.benichn.math3.Utils.Companion.div
import fr.benichn.math3.Utils.Companion.times
import kotlin.math.max
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class BoxProperty<S: FormulaBox, T>(private val source: S, private val defaultValue: T, val updatesGraphics: Boolean = true) : ReadWriteProperty<S, T> {
    private var field = defaultValue
    val onChanged = VCC<S, T>(source)
    fun get() = field
    fun set(value: T) {
        val old = field
        field = value
        onChanged(old, value)
        if (updatesGraphics) {
            source.updateGraphics()
        }
    }
    private val connections = mutableListOf<CallbackLink<*,*>>()
    fun <A, B> connectValue(callback: VCC<A, B>, mapper: (A, B) -> T) {
        connections.add(CallbackLink(callback) { s, e ->
            set(mapper(s, e.new))
        })
    }
    fun <A, B> connectValue(callback: VCC<A, B>, currentValue: B, mapper: (A, B) -> T) {
        connectValue(callback, mapper)
        set(mapper(callback.source, currentValue))
    }
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
    override fun setValue(thisRef: S, property: KProperty<*>, value: T) {
        if (!isConnected) {
            set(value)
        }
    }
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

data class Range(val start: Float = 0f, val end: Float = 0f)

enum class Orientation { H, V }

data class RectPoint(val tx: Float, val ty: Float) {
    init {
        assert((tx.isNaN() || tx in 0.0..1.0) && (ty.isNaN() || ty in 0.0..1.0))
    }

    fun get(r: RectF): PointF = PointF(
            if (tx.isNaN()) 0f else r.left + tx * (r.right - r.left),
            if (ty.isNaN()) 0f else r.top + ty * (r.bottom - r.top)
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
        val TOP_NAN = RectPoint(Float.NaN, 0f)
        val BOTTOM_NAN = RectPoint(Float.NaN, 1f)
        val NAN_LEFT = RectPoint(0f, Float.NaN)
        val NAN_RIGHT = RectPoint(1f, Float.NaN)
        val NAN = RectPoint(Float.NaN, Float.NaN)
    }
}

enum class Side {
    L,
    R
}

data class SidedBox(val box: FormulaBox, val side: Side) {
    fun toInputCoord() : BoxInputCoord? = FormulaBox.getBoxInputCoord(this)
}

open class FormulaBox {
    private var parent: FormulaBox? = null
    private val children = mutableListOf<FormulaBox>()
    val ch = ImmutableList(children)
    protected open fun addBox(b: FormulaBox) = addBox(children.size, b)
    protected open fun addBox(i: Int, b: FormulaBox) {
        if (b.parent != null) b.delete()
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

    open fun addInitialBoxes(ib: InitialBoxes) {

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

    operator fun get(c: BoxCoord): FormulaBox {
        var b = this
        for (i in c) {
            b = b.ch[i]
        }
        return b
    }

    protected open fun onChildRequiresDelete(i: Int): BoxInputCoord? = delete()
    fun delete() : BoxInputCoord? = parent?.onChildRequiresDelete(indexInParent!!)

    open fun getInitialCaretPos(): SidedBox = SidedBox(this, Side.R)

    private fun buildCoord(childCoord: BoxCoord): BoxCoord =
        if (parent == null) {
            childCoord
        } else {
            val i = indexInParent!!
            parent!!.buildCoord(BoxCoord(i, childCoord))
        }

    fun getSide(x: Float): Side = if (x > accRealBounds.centerX()) Side.R else Side.L

    open fun findChildBox(absX: Float, absY: Float) : FormulaBox {
        for (c in children) {
            if (c.accRealBounds.contains(absX, absY)) {
                return c
            }
        }
        return this
    }

    protected open val alwaysEnter // ~~ rustine ~~
        get() = false

    fun findBox(absX: Float, absY: Float) : SidedBox {
        val c = findChildBox(absX, absY)
        return if (c == this || (!c.alwaysEnter && c.accRealBounds.let { absX < it.left || it.right < absX })) {
            SidedBox(c, c.getSide(absX))
        } else {
            c.findBox(absX, absY)
        }
    }

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

    val onGraphicsChanged = VCC<FormulaBox, FormulaGraphics>(this)
    var graphics: FormulaGraphics = FormulaGraphics()
        private set(value) {
            val old = field
            field = value
            onGraphicsChanged(old, value)
            if (old.path != value.path) onPathChanged(old.path, value.path)
            if (old.paint != value.paint) onPaintChanged(old.paint, value.paint)
            if (old.bounds != value.bounds) onBoundsChanged(old.bounds, value.bounds)
            isProcessing = false
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

    protected fun listenChildBoundsChange(i: Int) {
        val b = children[i]
        b.onBoundsChanged += { _, _ -> updateGraphics() }
    }

    protected fun listenChildBoundsChange(b: FormulaBox) = listenChildBoundsChange(children.indexOf(b))

    protected open fun generateGraphics(): FormulaGraphics = FormulaGraphics(
        Path(),
        paint,
        Utils.sumOfRects(ch.map { it.realBounds })
    )

    fun updateGraphics() {
        graphics = generateGraphics()
    }

    fun alert() {
        graphics = FormulaGraphics(
            path,
            Paint(paint).also { it.color = Color.GREEN },
            bounds
        )
    }

    fun getLength(o: Orientation) = when (o) {
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

    val indexInParent
        get() = parent?.children?.indexOf(this)

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
        const val DEFAULT_TEXT_RADIUS = DEFAULT_TEXT_SIZE * 0.5f
        const val DEFAULT_TEXT_WIDTH = DEFAULT_TEXT_SIZE * 0.6f
        const val DEFAULT_LINE_WIDTH = 4f

        fun getBoxCoord(b: FormulaBox): BoxCoord = b.buildCoord(BoxCoord.root)
        fun getBoxInputCoord(sb: SidedBox): BoxInputCoord? {
            val (box, side) = sb
            if (box is InputFormulaBox) {
                assert(box.ch.size == 0)
                return BoxInputCoord(box, 0)
            }
            else {
                var b = box
                var i: Int
                while (b.parent != null) {
                    i = b.indexInParent!!
                    b = b.parent!!
                    if (b is InputFormulaBox) return BoxInputCoord(b, if (side == Side.L) i else i+1)
                }
                return null
            }
        }
    }
}

sealed class InitialBoxes {
    data class BeforeAfter(val boxesBefore: List<FormulaBox>, val boxesAfter: List<FormulaBox>) : InitialBoxes()
    data class Selection(val boxes: List<FormulaBox>)
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
        val bounds = RectF(0f, -h * 0.5f, w, h * 0.5f)
        return FormulaGraphics(p, paint, bounds)
    }
}

class LineFormulaBox(orientation: Orientation = Orientation.V,
                     range: Range = Range(-DEFAULT_TEXT_RADIUS,DEFAULT_TEXT_RADIUS)) : FormulaBox() {
    val dlgOrientation = BoxProperty(this, orientation)
    var orientation by dlgOrientation

    val dlgRange = BoxProperty(this, range)
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

sealed class SequenceFormulaBox(vararg boxes: FormulaBox) : FormulaBox() {
    init {
        paint.color = Color.WHITE
        paint.strokeWidth = DEFAULT_LINE_WIDTH
        paint.style = Paint.Style.STROKE
        updateGraphics()
        for (b in boxes) {
            addBox(b)
        }
    }

    override fun addBox(i: Int, b: FormulaBox) {
        super.addBox(i, b)
        setChildTransform(
            i,
            BoxTransform.xOffset((if (i == 0) 0f else ch[i - 1].let { it.transform.origin.x + it.bounds.right }) - b.bounds.left)
        )
        connect(b.onBoundsChanged) { s, e ->
            val j = b.indexInParent!!
            offsetFrom(j, e.old.left - e.new.left)
            offsetFrom(j + 1, e.new.right - e.old.right)
        }
        listenChildBoundsChange(i)
        offsetFrom(i + 1, b.bounds.width())
        updateGraphics()
    }
    override fun removeBoxAt(i: Int) {
        val b = ch[i]
        super.removeBoxAt(i)
        offsetFrom(i, -b.bounds.width())
        updateGraphics()
    }

    private fun offsetFrom(i: Int, l: Float) {
        isProcessing = true
        for (j in i until ch.size) {
            modifyChildTransform(j) { it * BoxTransform.xOffset(l) }
        }
    }

    override fun generateGraphics(): FormulaGraphics =
        if (ch.isEmpty()) {
            val rh = DEFAULT_TEXT_RADIUS
            val w = DEFAULT_TEXT_WIDTH
            val path = Path()
            path.moveTo(0f, rh*0.5f)
            path.lineTo(0f, rh)
            path.lineTo(w*0.25f, rh)
            path.moveTo(0f, -rh*0.5f)
            path.lineTo(0f, -rh)
            path.lineTo(w*0.25f, -rh)
            path.moveTo(w, rh*0.5f)
            path.lineTo(w, rh)
            path.lineTo(w*0.75f, rh)
            path.moveTo(w, -rh*0.5f)
            path.lineTo(w, -rh)
            path.lineTo(w*0.75f, -rh)
            val bounds = RectF(0f, -rh, w, rh)
            FormulaGraphics(
                path,
                paint,
                bounds
            )
        } else {
            super.generateGraphics()
        }
}

class InputFormulaBox(vararg boxes: FormulaBox) : SequenceFormulaBox(*boxes) {
    public override fun addBox(i: Int, b: FormulaBox) = super.addBox(i, b)
    public override fun addBox(b: FormulaBox) = super.addBox(b)
    public override fun removeBoxAt(i: Int) = super.removeBoxAt(i)
    public override fun removeBox(b: FormulaBox) = super.removeBox(b)
    public override fun removeLastBox() = super.removeLastBox()

    override fun findChildBox(absX: Float, absY: Float): FormulaBox {
        for (c in ch) {
            if (absX < c.accRealBounds.right) {
                return c
            }
        }
        return if (ch.isEmpty()) this else ch.last()
    }

    override fun onChildRequiresDelete(i: Int): BoxInputCoord {
        removeBoxAt(i)
        return BoxInputCoord(this, i)
    }

    override fun getInitialCaretPos(): SidedBox {
        return if (ch.isEmpty()) SidedBox(this, Side.R) else SidedBox(ch.last(), Side.R)
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
    }

    override val alwaysEnter: Boolean
        get() = true
    override fun findChildBox(absX: Float, absY: Float): FormulaBox = child.findChildBox(absX, absY)
    override fun getInitialCaretPos(): SidedBox = child.getInitialCaretPos()

    override fun addBox(i: Int, b: FormulaBox) {
        super.addBox(i, b)
        connect(b.onBoundsChanged) { s, e ->
            alignChild()
        }
        listenChildBoundsChange(i)
        alignChild()
        updateGraphics()
    }

    private fun alignChild() {
        isProcessing = true
        setChildTransform(
            0,
            BoxTransform(-(rectPoint.get(child.bounds)))
        )
    }
}

class FractionFormulaBox(numChildren: Array<FormulaBox> = emptyArray(), denChildren: Array<FormulaBox> = emptyArray()) : FormulaBox() {
    private val bar = LineFormulaBox(Orientation.H)
    private val num = AlignFormulaBox(InputFormulaBox(*numChildren), RectPoint.BOTTOM_CENTER)
    private val den = AlignFormulaBox(InputFormulaBox(*denChildren), RectPoint.TOP_CENTER)
    val numerator
        get() = num.child
    val denominator
        get() = den.child
    init {
        addBox(bar)
        addBox(num)
        addBox(den)
        bar.range = getBarWidth()
        bar.dlgRange.connectValue(num.onBoundsChanged) { _, _ -> getBarWidth() }
        bar.dlgRange.connectValue(den.onBoundsChanged) { _, _ -> getBarWidth() }
        setChildTransform(1, BoxTransform.yOffset(-DEFAULT_TEXT_SIZE * 0.15f))
        setChildTransform(2, BoxTransform.yOffset(DEFAULT_TEXT_SIZE * 0.15f))
        listenChildBoundsChange(num)
        listenChildBoundsChange(den)
        updateGraphics()
    }

    override fun getInitialCaretPos(): SidedBox {
        return numerator.getInitialCaretPos()
    }

    override fun findChildBox(absX: Float, absY: Float): FormulaBox =
        if (bar.accRealBounds.let { it.left <= absX && absX <= it.right }) {
            if (absY > accTransform.origin.y) {
                den
            } else {
                num
            }
        } else {
            this
        }

    override fun generateGraphics(): FormulaGraphics { // padding ?
        val gr = super.generateGraphics()
        return FormulaGraphics(
            gr.path,
            gr.paint,
            RectF(gr.bounds.left - DEFAULT_TEXT_WIDTH * 0.25f, gr.bounds.top, gr.bounds.right + DEFAULT_TEXT_WIDTH * 0.25f, gr.bounds.bottom)
        )
    }

    private fun getBarWidth(): Range {
        val w = max(num.bounds.width(), den.bounds.width()) + DEFAULT_TEXT_WIDTH * 0.25f
        val r = w * 0.5f
        return Range(-r, r)
    }
}

class BracketFormulaBox(range: Range = Range(-DEFAULT_TEXT_RADIUS,DEFAULT_TEXT_RADIUS)) : FormulaBox() {
    val dlgRange = BoxProperty(this, range)
    var range by dlgRange

    init {
        paint.color = Color.WHITE
        paint.strokeWidth = DEFAULT_LINE_WIDTH
        paint.style = Paint.Style.STROKE
        updateGraphics()
    }

    override fun generateGraphics(): FormulaGraphics {
        val l1 = range.start + DEFAULT_TEXT_RADIUS
        val l2 = range.end - DEFAULT_TEXT_RADIUS
        val r = DEFAULT_TEXT_RADIUS * 0.9f
        val path = Path()
        path.moveTo(0f,l2)
        path.lineTo(0f,l1)
        path.rCubicTo(0f, 0f, 0f,-0.75f * r, 0.5f* r, -r)
        path.moveTo(0f,l2)
        path.rCubicTo(0f, 0f, 0f,0.75f * r, 0.5f* r, r)
        val bounds = RectF(-0.25f * DEFAULT_TEXT_RADIUS, range.start, 0.5f * DEFAULT_TEXT_RADIUS, range.end)
        return FormulaGraphics(
            path,
            paint,
            bounds
        )
    }
}

sealed class Chain<out T> : Iterable<T> {
    data object Empty : Chain<Nothing>() {
        override val isEmpty: Boolean
            get() = true
        override fun conputeSize(): Int = 0
    }
    data class Node<out T>(val head: T, val tail: Chain<T>) : Chain<T>() {
        override val isEmpty: Boolean
            get() = false
        override fun conputeSize(): Int = 1 + tail.size
    }

    abstract val isEmpty: Boolean

    val size by lazy { conputeSize() }
    protected abstract fun conputeSize(): Int

    fun asNode(): Node<T> = this as Node<T>

    fun toList(): List<T> {
        val a = ArrayList<T>(size)
        var i = 0
        for (e in this) {
            a[i] = e
            i++
        }
        return a
    }

    override fun iterator() = object : Iterator<T> {
        private var next: Chain<T> = this@Chain
        override fun hasNext(): Boolean = !next.isEmpty
        override fun next(): T {
            if (hasNext()) {
                val (h, t) = next as Node<T>
                next = t
                return h
            } else {
                throw NoSuchElementException("No additional element available")
            }
        }

    }

    companion object {
        fun <T> fromList(l: List<T>): Chain<T> {
            var current: Chain<T> = Empty
            for (i in l.size - 1 downTo 0) {
                current = Node(l[i], current)
            }
            return current
        }

        // fun <T> withLast(source: Chain<T>, e: T): Chain<T> {
        //     if (source.isEmpty) return Empty
        //     val a = source.toList()
        //     var current = Node(e, Empty)
        //     for (i in a.size-2 downTo 0) {
        //         current = Node(a[i], current)
        //     }
        //     return current
        // }
    }
}

@JvmInline
value class BoxCoord(private val indices: Chain<Int>) {
    val depth
        get() = indices.size
    operator fun iterator() = indices.iterator()

    constructor(h: Int, t: BoxCoord) : this(Chain.Node(h, t.indices))

    operator fun compareTo(bc: BoxCoord): Int =
        when {
            indices.isEmpty && bc.indices.isEmpty -> 0
            indices.isEmpty && !bc.indices.isEmpty -> -1
            !indices.isEmpty && bc.indices.isEmpty -> 1
            else -> {
                val (h1, t1) = indices.asNode()
                val (h2, t2) = bc.indices.asNode()
                if (h1 == h2) {
                    BoxCoord(t1).compareTo(BoxCoord(t2))
                } else {
                    0
                }
            }
        }

    // operator fun plus(i: Int) = BoxCoord(Chain.withLast(indices, indices.last()+i))
    // operator fun minus(i: Int) = plus(-i)

    companion object {
        val root: BoxCoord = BoxCoord(Chain.Empty)
    }
}

// data class SidedIndex(val index: Int, val side: Side) {
//     fun toR() = if (side == Side.L) SidedIndex(index-1, Side.R) else this
//     fun toL() = if (side == Side.R) SidedIndex(index+1, Side.L) else this
// }

data class BoxInputCoord(val box: InputFormulaBox, val index: Int) {
    fun getAbsPosition(): PointF {
        val y = box.accTransform.origin.y
        val x = if (box.ch.isEmpty()) {
            assert(index == 0)
            box.accRealBounds.centerX()
        } else if (index == box.ch.size) {
            box.accRealBounds.right
        } else {
            box.ch[index].accRealBounds.left
        }
        return PointF(x, y)
    }
}

class BoxCaret(val root: FormulaBox) {
    var position: BoxInputCoord? = null
        set(value) {
            field = value
            onPictureChanged(Unit)
        }

    val onPictureChanged = Callback<BoxCaret, Unit>(this)
    fun drawOnCanvas(canvas: Canvas) {
        position?.also {
            val p = it.getAbsPosition()
            canvas.drawLine(
                p.x,
                p.y - FormulaBox.DEFAULT_TEXT_RADIUS,
                p.x,
                p.y + FormulaBox.DEFAULT_TEXT_RADIUS,
                paint
            )
        }
    }

    companion object {
        val paint = Paint().also {
            it.color = Color.YELLOW
            it.style = Paint.Style.STROKE
            it.strokeWidth = 6f
        }
    }
}