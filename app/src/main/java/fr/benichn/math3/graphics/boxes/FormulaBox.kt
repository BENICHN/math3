package fr.benichn.math3.graphics.boxes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import fr.benichn.math3.graphics.caret.BoxCaret
import fr.benichn.math3.graphics.boxes.types.BoxCoord
import fr.benichn.math3.graphics.boxes.types.BoxInputCoord
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.types.ImmutableList
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.types.Orientation
import fr.benichn.math3.graphics.types.Side
import fr.benichn.math3.graphics.boxes.types.SidedBox
import fr.benichn.math3.graphics.Utils
import fr.benichn.math3.graphics.Utils.Companion.sumOfRects
import fr.benichn.math3.types.Chain
import fr.benichn.math3.types.callback.*

open class FormulaBox {
    private var parent: FormulaBox? = null
    val parentInput: InputFormulaBox?
        get() = parent?.let { if (it is InputFormulaBox) it else it.parentInput }
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
        setChildTransform(i, BoxTransform())
        children.removeAt(i)
        b.parent = null
    }

    open fun addInitialBoxes(ib: InitialBoxes) {
    }

    var isSelected = false
        set(value) {
            field = value
            for (c in ch) {
                c.isSelected = value
            }
            onPictureChanged(Unit)
        }

    val selectedChildren: List<FormulaBox>
        get() =
            ch.flatMap {
                if (it.isSelected) {
                    listOf(it)
                } else {
                    it.selectedChildren
                }
            }

    private val connections = mutableListOf<CallbackLink<*, *>>()
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

    open val selectBeforeDeletion: Boolean = false

    protected open fun onChildRequiresDelete(b: FormulaBox): DeletionResult = delete()
    fun delete() : DeletionResult = parent?.onChildRequiresDelete(this) ?: DeletionResult()

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
        if (!isSelected && this is SequenceFormulaBox) {
            val rects = ch
                .filter { it.isSelected }
                .map { c -> c.realBounds.let { RectF(it.left, bounds.top, it.right, bounds.bottom) } }
                .fold(Chain.Empty) { acc : Chain<RectF>, r: RectF ->
                    when (acc) {
                        is Chain.Empty -> Chain.Node(r, Chain.Empty)
                        is Chain.Node -> if (r.left == acc.head.right) {
                            Chain.Node(sumOfRects(r, acc.head), acc.tail)
                        } else {
                            Chain.Node(r, acc)
                        }
                    }
                }
            for (r in rects) {
                canvas.drawRect(r, BoxCaret.selectionPaint)
            }
        }
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
        fun getBoxInputCoord(b: FormulaBox, s: Side = Side.R) = Companion.getBoxInputCoord(SidedBox(b, s))
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