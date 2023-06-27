package fr.benichn.math3.graphics.boxes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import fr.benichn.math3.graphics.caret.BoxCaret
import fr.benichn.math3.graphics.boxes.types.BoxCoord
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
import fr.benichn.math3.graphics.boxes.types.Range
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.types.Chain
import fr.benichn.math3.types.callback.*

open class FormulaBox {
    private var parent: FormulaBox? = null
        set(value) {
            if (value != null) {
                assert(caret == null)
            }
            field = value
        }
    val isRoot
        get() = parent == null
    val isInputRoot: Boolean
        get() = parent?.let { if (it is InputFormulaBox) false else it.isInputRoot } ?: true

    private data class ParentWithIndex(val parent: FormulaBox, val index: Int)
    private val parentWithIndex
        get() = parent?.let { ParentWithIndex(it, it.ch.indexOf(this)) }
    private fun buildParents(tail: Chain<ParentWithIndex>): Chain<ParentWithIndex> =
        parent?.let{ it.buildParents(Chain.Node(ParentWithIndex(it, it.ch.indexOf(this)), tail)) } ?: tail
    private val parents: Chain<ParentWithIndex>
        get() = buildParents(Chain.Empty)

    private var caret: BoxCaret? = null
        get() = if (isRoot) field else parent!!.caret

    fun createCaret(): BoxCaret {
        assert(isRoot)
        val cr = BoxCaret()
        cr.onPositionChanged += { _, _ ->
            notifyPictureChanged()
        }
        caret = cr
        return cr
    }

    fun removeCaret() {
        caret = null
    }

    private val children = mutableListOf<FormulaBox>()
    val ch = ImmutableList(children)
    protected open fun addBox(b: FormulaBox) = addBox(children.size, b)
    protected open fun addBox(i: Int, b: FormulaBox) {
        if (!b.isRoot) b.delete()
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

    val isSelected
        get() = caret?.position?.let {
            if (it is CaretPosition.Selection) {
                it.contains(this)
            } else {
                false
            }
        } ?: false

    fun deepIndexOf(b: FormulaBox): Int {
        for (p in b.parents) {
            if (p.parent == this) return p.index
        }
        return  -1
    }

    // var isSelected = false
    //     set(value) {
    //         field = value
    //         for (c in ch) {
    //             c.isSelected = value
    //         }
    //         onPictureChanged(Unit)
    //     }
//
    // val selectedChildren: List<FormulaBox>
    //     get() =
    //         ch.flatMap {
    //             if (it.isSelected) {
    //                 listOf(it)
    //             } else {
    //                 it.selectedChildren
    //             }
    //         }

    private val connections = mutableListOf<CallbackLink<*, *>>()
    fun <A, B> connect(listener: VCL<A, B>, f: (A, ValueChangedEvent<B>) -> Unit) {
        connections.add(CallbackLink(listener, f))
    }
    fun disconnectFrom(b: FormulaBox) {
        connections.removeIf {
            if (it.listener.source == b) {
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

    private val notifyTransformChanged = VCC<FormulaBox, BoxTransform>(this)
    val onTransformChanged = notifyTransformChanged.Listener()
    var transform: BoxTransform = BoxTransform()
        private set(value) {
            val old = field
            field = value
            notifyTransformChanged(old, value)
            updateAccTransform()
        }

    private fun updateAccTransform() {
        accTransform = transform * parent!!.accTransform
    }

    private val notifyAccTransformChanged = VCC<FormulaBox, BoxTransform>(this)
    val onAccTransformChanged = notifyAccTransformChanged.Listener()
    var accTransform: BoxTransform = BoxTransform()
        private set(value) {
            val old = field
            field = value
            notifyAccTransformChanged(old, value)
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

    private val notifyGraphicsChanged = VCC<FormulaBox, FormulaGraphics>(this)
    val onGraphicsChanged = notifyGraphicsChanged.Listener()
    var graphics: FormulaGraphics = FormulaGraphics()
        private set(value) {
            val old = field
            field = value
            notifyGraphicsChanged(old, value)
            if (old.path != value.path) notifyPathChanged(old.path, value.path)
            if (old.paint != value.paint) notifyPaintChanged(old.paint, value.paint)
            if (old.bounds != value.bounds) notifyBoundsChanged(old.bounds, value.bounds)
            isProcessing = false
        }
    private val notifyBoundsChanged = VCC<FormulaBox, RectF>(this)
    val onBoundsChanged = notifyBoundsChanged.Listener()
    val bounds
        get() = graphics.bounds
    private val notifyPathChanged = VCC<FormulaBox, Path>(this)
    val onPathChanged = notifyPathChanged.Listener()
    val path
        get() = graphics.path
    private val notifyPaintChanged = VCC<FormulaBox, Paint>(this)
    val onPaintChanged = notifyPaintChanged.Listener()
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
                notifyPictureChanged(Unit)
            }
        }
    private var hasChangedPicture = false

    private val notifyPictureChanged = Callback<FormulaBox, Unit>(this)
    val onPictureChanged = notifyPictureChanged.Listener()

    fun drawOnCanvas(canvas: Canvas) {
        transform.applyOnCanvas(canvas)
        // if (!isSelected && this is SequenceFormulaBox) {
        //     val rects = ch
        //         .filter { it.isSelected }
        //         .map { c -> c.realBounds }
        //         .fold(Chain.Empty) { acc : Chain<RectF>, r: RectF ->
        //             when (acc) {
        //                 is Chain.Empty -> Chain.Node(r, Chain.Empty)
        //                 is Chain.Node -> if (r.left == acc.head.right) {
        //                     Chain.Node(sumOfRects(r, acc.head), acc.tail)
        //                 } else {
        //                     Chain.Node(r, acc)
        //                 }
        //             }
        //         }
        //     for (r in rects) {
        //         canvas.drawRect(r, BoxCaret.selectionPaint)
        //     }
        // }
        val p = caret?.position
        if (p is CaretPosition.Selection) {
            if (p.box == this) {
                val r = sumOfRects(p.selectedBoxes.map { it.realBounds })
                canvas.drawRect(r, BoxCaret.selectionPaint)
            }
        }
        canvas.drawPath(path, paint)
        // canvas.drawRect(bounds, FormulaView.red)
        for (b in children) {
            b.drawOnCanvas(canvas)
        }
        transform.invert.applyOnCanvas(canvas)
        if (isRoot) p?.also {
            when (it) {
                is CaretPosition.None -> { }
                is CaretPosition.Single ->  {
                    val pos = it.getAbsPosition()
                    canvas.drawLine(
                        pos.x,
                        pos.y - DEFAULT_TEXT_RADIUS,
                        pos.x,
                        pos.y + DEFAULT_TEXT_RADIUS,
                        BoxCaret.caretPaint
                    )
                }
                is CaretPosition.Selection -> { }
            }
        }
    }

    val indexInParent
        get() = parent?.children?.indexOf(this)

    init {
        onPictureChanged += { _, _ ->
            if (isProcessing) {
                hasChangedPicture = true
            } else {
                parent?.notifyPictureChanged?.invoke()
            }
        }
        onGraphicsChanged += { _, e ->
            if (e.old.path != e.new.path || e.old.paint != e.new.paint) {
                notifyPictureChanged()
            }
        }
        onTransformChanged += { _, _ ->
            notifyPictureChanged()
        }
    }

    companion object {
        const val DEFAULT_TEXT_SIZE = 96f
        const val DEFAULT_TEXT_RADIUS = DEFAULT_TEXT_SIZE * 0.5f
        const val DEFAULT_TEXT_WIDTH = DEFAULT_TEXT_SIZE * 0.6f
        const val DEFAULT_LINE_WIDTH = 4f

        // fun getBoxCoord(b: FormulaBox): BoxCoord = b.buildCoord(BoxCoord.root)
        fun getCaretPositionFromSidedBox(b: FormulaBox, s: Side = Side.R) = getCaretPositionFromSidedBox(SidedBox(b, s))
        fun getCaretPositionFromSidedBox(sb: SidedBox): CaretPosition {
            val (box, side) = sb
            if (box is InputFormulaBox) {
                assert(box.ch.size == 0)
                return CaretPosition.Single(box, 0)
            }
            else {
                var b = box
                var i: Int
                while (!b.isRoot) {
                    i = b.indexInParent!!
                    b = b.parent!!
                    if (b is InputFormulaBox) return CaretPosition.Single(b, if (side == Side.L) i else i+1)
                }
                return CaretPosition.None
            }
        }

        fun mergeSelections(s1: CaretPosition.Selection, s2: CaretPosition.Selection): CaretPosition.Selection? {
            if (s1.box.ch.isEmpty() || s2.box.ch.isEmpty()) return null
            val s1ParentSequences = s1.box.ch[0].parents.filter { it.parent is SequenceFormulaBox }
            val s2ParentSequences = s2.box.ch[0].parents.filter { it.parent is SequenceFormulaBox }
            val commonParent = s1ParentSequences.zip(s2ParentSequences).lastOrNull { (p1, p2) -> p1.parent == p2.parent }
            return commonParent?.let { (p1, p2) ->
                val box = p1.parent as SequenceFormulaBox
                val r1 = retrieveRange(s1, p1)
                val r2 = retrieveRange(s2, p2)
                val r = Range.sum(r1, r2)
                CaretPosition.Selection(box, r)
            }
        }

        private fun retrieveRange(s: CaretPosition.Selection, p: ParentWithIndex) : Range =
            if (p.parent == s.box) {
                s.indexRange
            } else {
                Range(p.index, p.index)
            }

        private fun getParentSequenceWithIndex(b: FormulaBox): ParentWithIndex? =
            b.parentWithIndex?.let {
                if (it.parent is SequenceFormulaBox) {
                    it
                }
                else {
                    getParentSequenceWithIndex(it.parent)
                }
            }

        fun getSelectionFromBox(b: FormulaBox): CaretPosition.Selection? =
            getParentSequenceWithIndex(b)?.let { (p, i) -> CaretPosition.Selection(p as SequenceFormulaBox, Range(i, i+1)) }

        // fun getSelectionFromBoxes(b1: FormulaBox, b2: FormulaBox): CaretPosition.Selection? {
        //     val s1 = getSelectionFromBox(b1)
        //     val s2 = getSelectionFromBox(b2)
        //     return if (s1 != null && s2 != null) mergeSelections(s1, s2) else null
        // }

    }
}
