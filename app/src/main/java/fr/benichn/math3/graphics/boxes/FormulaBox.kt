package fr.benichn.math3.graphics.boxes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.withClip
import fr.benichn.math3.graphics.FormulaView
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
import kotlin.math.abs

open class FormulaBox {
    var parent: FormulaBox? = null
        private set(value) {
            if (value != null) {
                assert(caret == null)
            }
            field = value
        }
    val root: FormulaBox
        get() = parent?.root ?: this
    val isRoot
        get() = parent == null

    data class ParentWithIndex(val box: FormulaBox, val index: Int)
    val parentWithIndex
        get() = parent?.let { ParentWithIndex(it, it.ch.indexOf(this)) }
    private fun buildParents(tail: Chain<ParentWithIndex>): Chain<ParentWithIndex> =
        parent?.let{ it.buildParents(Chain.Node(ParentWithIndex(it, it.ch.indexOf(this)), tail)) } ?: tail
    val parents: Chain<ParentWithIndex>
        get() = buildParents(Chain.Empty)
    val parentsAndThis
        get() = buildParents(Chain.singleton(ParentWithIndex(this, -1)))

    private var caret: BoxCaret? = null
        get() = if (isRoot) field else parent!!.caret

    fun createCaret(): BoxCaret {
        assert(isRoot)
        val cr = BoxCaret()
        cr.onPictureChanged += { _, _ ->
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
            if (p.box == this) return p.index
        }
        return  -1
    }

    private val connections = mutableListOf<VCCLink<*, *>>()
    fun <A, B> connect(listener: VCL<A, B>, f: (A, ValueChangedEvent<B>) -> Unit) {
        connections.add(VCCLink(listener, f))
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

    fun findBox(absPos: PointF) = findBox(absPos.x, absPos.y)
    fun findBox(absX: Float, absY: Float) : SidedBox {
        val c = findChildBox(absX, absY)
        return if (c == this || (!c.alwaysEnter && c.accRealBounds.run { absX < left || right < absX })) {
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

    fun getLength(o: Orientation) = when (o) {
        Orientation.H -> bounds.width()
        Orientation.V -> bounds.height()
    }

    private val notifyPictureChanged = Callback<FormulaBox, Unit>(this)
    val onPictureChanged = notifyPictureChanged.Listener()

    fun drawOnCanvas(canvas: Canvas) {
        transform.applyOnCanvas(canvas)
        val p = caret?.position

        fun draw() {
            if (isRoot) { // dessin du rectangle de selection
                when (p) {
                    is CaretPosition.Selection -> {
                        canvas.drawRect(p.bounds, BoxCaret.selectionPaint)
                    }

                    else -> {}
                }
            }

            canvas.drawPath(path, paint)
            // canvas.drawRect(bounds, FormulaView.red)
            for (b in children) {
                b.drawOnCanvas(canvas)
            }

            if (isRoot) { // dessin des curseurs
                when (p) {
                    is CaretPosition.None -> {
                    }

                    is CaretPosition.Single -> {
                        val pos = p.getAbsPosition()
                        BoxCaret.drawCaretAtPos(
                            canvas,
                            pos,
                            caret!!.absolutePosition != null
                        )
                    }

                    is CaretPosition.Selection -> {
                        val r = p.bounds
                        fun drawSelectionEnding(x: Float) {
                            canvas.drawLine(x, r.top, x, r.bottom, BoxCaret.caretPaint)
                            // canvas.drawCircle(x, r.top, SELECTION_CARET_RADIUS, BoxCaret.ballPaint)
                        }

                        val fx = caret!!.fixedX
                        if (fx == null) {
                            drawSelectionEnding(r.left)
                            drawSelectionEnding(r.right)
                        } else {
                            val x = if (abs(r.left - fx) <= abs(r.right - fx)) r.left else r.right
                            drawSelectionEnding(x)
                        }
                    }

                    else -> {}
                }
                caret?.absolutePosition?.also { ap -> // dessin de la position absolue des curseurs
                    when (p) {
                        is CaretPosition.Single -> {
                            BoxCaret.drawCaretAtPos(
                                canvas,
                                ap,
                                height = DEFAULT_TEXT_RADIUS /* + CARET_OVERFLOW_RADIUS */
                            )
                        }

                        is CaretPosition.Selection -> {
                            BoxCaret.drawCaretAtPos(
                                canvas,
                                ap,
                                height = p.bounds.height() * 0.5f /* + CARET_OVERFLOW_RADIUS */
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
        draw()

        if (isRoot /* && caret?.position is CaretPosition.Single */) caret?.absolutePosition?.also { ap ->
            canvas.translate(ap.x, ap.y-DEFAULT_TEXT_RADIUS*4)
            canvas.drawPath(magnifierPath, FormulaView.backgroundPaint)
            canvas.drawPath(magnifierPath, FormulaView.magnifierBorder)
            canvas.withClip(magnifierPath) {
                canvas.translate(-ap.x, -ap.y)
                draw()
            }
            canvas.translate(0f, DEFAULT_TEXT_RADIUS*4)
        }

        transform.invert.applyOnCanvas(canvas)
    }

    val indexInParent
        get() = parent?.children?.indexOf(this)

    init {
        onPictureChanged += { _, _ ->
            parent?.notifyPictureChanged?.invoke()
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
        const val SELECTION_CARET_RADIUS = 14f
        const val CARET_OVERFLOW_RADIUS = 18f
        const val MAGNIFIER_RADIUS = DEFAULT_TEXT_SIZE
        val magnifierPath = Path().apply {
            val rx = DEFAULT_TEXT_WIDTH * 3
            val ry = DEFAULT_TEXT_SIZE * 0.75f
            val r = RectF(-rx, -ry, rx, ry)
            addRoundRect(r, MAGNIFIER_RADIUS, MAGNIFIER_RADIUS, Path.Direction.CCW)
        }
    }
}
