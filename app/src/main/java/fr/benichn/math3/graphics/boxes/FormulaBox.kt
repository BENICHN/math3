package fr.benichn.math3.graphics.boxes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.withClip
import fr.benichn.math3.graphics.FormulaView
import fr.benichn.math3.graphics.caret.BoxCaret
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.types.ImmutableList
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.types.Orientation
import fr.benichn.math3.graphics.types.Side
import fr.benichn.math3.graphics.Utils.Companion.sumOfRects
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.CommonParentWithIndices
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.ParentWithIndex
import fr.benichn.math3.graphics.boxes.types.PathPainting
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.types.Chain
import fr.benichn.math3.types.callback.*

open class FormulaBox {
    var parent: FormulaBox? = null
        private set(value) {
            if (value != null) {
                assert(caret == null)
            }
            field = value
            notifyBrothersBoundsChanged()
        }
    val root: FormulaBox
        get() = parent?.root ?: this
    val isRoot
        get() = parent == null

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

    private val notifyBrothersBoundsChanged = Callback<FormulaBox, Unit>(this)
    val onBrothersBoundsChanged = notifyBrothersBoundsChanged.Listener()

    private val children = mutableListOf<FormulaBox>()
    val ch = ImmutableList(children)
    fun addBoxes(vararg boxes: FormulaBox) = addBoxes(boxes.asIterable())
    fun addBoxes(boxes: Iterable<FormulaBox>) =
        addBoxes(children.size, boxes)
    fun addBoxes(i: Int, boxes: Iterable<FormulaBox>) {
        boxes.forEachIndexed { j, b ->
            addBox(i+j, b)
        }
    }
    fun addBox(b: FormulaBox) = addBox(children.size, b)
    open fun addBox(i: Int, b: FormulaBox) {
        if (!b.isRoot) b.delete()
        children.add(i, b)
        b.parent = this
        connect(b.onBoundsChanged) { s, e -> onChildBoundsChanged(s, e) }
        for (j in children.indices) {
            if (j != i) {
                children[j].notifyBrothersBoundsChanged()
            }
        }
    }
    fun removeAllBoxes() {
        while (children.isNotEmpty()) {
            removeLastBox()
        }
    }
    fun removeLastBox() { if (children.isNotEmpty()) removeBoxAt(children.size - 1) }
    fun removeBox(b: FormulaBox) = removeBoxAt(children.indexOf(b))
    open fun removeBoxAt(i: Int) {
        val b = children[i]
        assert(b.parent == this)
        disconnectFrom(b)
        setChildTransform(i, BoxTransform())
        children.removeAt(i)
        b.parent = null
        children.forEach { it.notifyBrothersBoundsChanged() }
    }

    open fun addInitialBoxes(ib: InitialBoxes) = FinalBoxes()

    val isSelected
        get() = caret?.positions.orEmpty().any { p -> p.contains(this) }

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

    open val selectBeforeDeletion: Boolean = false

    protected open fun onChildRequiresDelete(b: FormulaBox): DeletionResult = delete()
    fun delete() : DeletionResult = parent?.onChildRequiresDelete(this) ?: DeletionResult()

    fun forceDelete(): CaretPosition.Single? = parentWithIndex?.let { (p, i) ->
        if (p is InputFormulaBox) {
            p.removeBoxAt(i)
            CaretPosition.Single(p, i)
        }
        else p.forceDelete()
    }

    open fun deleteMultiple(indices: List<Int>) = DeletionResult()

    open fun getInitialSingle(): CaretPosition.Single? = null

    protected open fun shouldEnterInChild(c: FormulaBox, pos: PointF) =
        true

    protected open fun findChildBox(pos: PointF) : FormulaBox {
        for (c in children) {
            if (c.realBounds.contains(pos.x, pos.y)) {
                return c
            }
        }
        return this
    }

    fun findSingle(pos: PointF) : CaretPosition.Single? {
        val c = findChildBox(pos)
        return if (c == this || !shouldEnterInChild(c, pos)) {
            if (c is InputFormulaBox) {
                assert(c.ch.isEmpty())
                CaretPosition.Single(c, 0)
            }
            else {
                fun getParentInput(b: FormulaBox): ParentWithIndex? {
                    val pi = b.parentWithIndex
                    return pi?.let { if (it.box is InputFormulaBox) it else getParentInput(it.box) }
                }
                getParentInput(c)?.let {
                    val s = it.box.ch[it.index].let { b -> if (accTransform.applyOnPoint(pos).x <= b.accRealBounds.centerX()) Side.L else Side.R }
                    val i = if (s == Side.L) it.index else it.index + 1
                    CaretPosition.Single(it.box as InputFormulaBox, i)
                }
            }
        } else {
            c.findSingle(c.transform.invert.applyOnPoint(pos))
        }
    }

    fun findBox(pos: PointF) : FormulaBox {
        val c = findChildBox(pos)
        return if (c == this || !shouldEnterInChild(c, pos)) {
            c
        } else {
            c.findBox(c.transform.invert.applyOnPoint(pos))
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
            if (old.painting != value.painting) notifyPaintingChanged(old.painting, value.painting)
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
    private val notifyPaintingChanged = VCC<FormulaBox, PathPainting>(this)
    val onPaintingChanged = notifyPaintingChanged.Listener()
    val painting
        get() = graphics.painting

    private var backgroundPaint = PathPainting.Fill.getPaint(Color.TRANSPARENT)
    val dlgBackground = BoxProperty(this, Color.TRANSPARENT, false).apply {
        onChanged += { _, e ->
            backgroundPaint = PathPainting.Fill.getPaint(e.new)
            notifyPictureChanged()
        }
    }
    var background by dlgBackground

    private var foregroundPaint = Paint()
    val dlgForeground = BoxProperty(this, Color.WHITE, false).apply {
        onChanged += { _, e ->
            foregroundPaint = painting.getPaint(e.new)
            notifyPictureChanged()
        }
    }
    var foreground by dlgForeground

    val dlgPadding = BoxProperty(this, Padding(), false).apply {
        onChanged += { _, _ ->
            updateGraphics(false)
        }
    }
    var padding by dlgPadding

    val realBounds
        get() = transform.applyOnRect(bounds)
    val accRealBounds
        get() = accTransform.applyOnRect(bounds)

    protected open fun onChildBoundsChanged(b: FormulaBox, e: ValueChangedEvent<RectF>) {
        updateGraphics()
    }

    protected open fun generateGraphics(): FormulaGraphics = FormulaGraphics(
        Path(),
        painting,
        sumOfRects(ch.map { it.realBounds })
    )

    private var generatedGraphics: FormulaGraphics? = null
    private fun updateGraphics(regenerate: Boolean) {
        if (regenerate) {
            val g = generateGraphics()
            foregroundPaint = g.painting.getPaint(foreground)
            generatedGraphics = g
        }
        generatedGraphics?.run {
            graphics = withBounds { r -> padding.applyOnRect(r) }
        }
    }
    fun updateGraphics() = updateGraphics(true)

    fun getLength(o: Orientation) = when (o) {
        Orientation.H -> bounds.width()
        Orientation.V -> bounds.height()
    }

    private val notifyPictureChanged = Callback<FormulaBox, Unit>(this)
    val onPictureChanged = notifyPictureChanged.Listener()

    fun drawOnCanvas(canvas: Canvas) {
        transform.applyOnCanvas(canvas)

        fun draw() {
            if (isRoot) {
                caret?.preDrawOnCanvas(canvas)
            }

            canvas.drawRect(bounds, backgroundPaint)
            canvas.drawPath(path, foregroundPaint)
            // canvas.drawRect(bounds, FormulaView.red)
            for (b in children) {
                b.drawOnCanvas(canvas)
            }

            if (isRoot) {
                caret?.postDrawOnCanvas(canvas)
            }
        }
        draw()

        for (p in caret?.positions.orEmpty()) {
            if (isRoot && p is CaretPosition.Single) p.absPos?.also { ap ->
                canvas.translate(ap.x, ap.y-DEFAULT_TEXT_RADIUS*4)
                canvas.drawPath(magnifierPath, FormulaView.backgroundPaint)
                canvas.drawPath(magnifierPath, FormulaView.magnifierBorder)
                canvas.withClip(magnifierPath) {
                    canvas.translate(-ap.x, -ap.y)
                    canvas.scale(MAGNIFIER_FACTOR, MAGNIFIER_FACTOR, ap.x, ap.y)
                    draw()
                }
                canvas.translate(0f, DEFAULT_TEXT_RADIUS*4)
            }
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
            if (e.old.path != e.new.path || e.old.painting != e.new.painting) {
                notifyPictureChanged()
            }
        }
        onTransformChanged += { _, _ ->
            notifyPictureChanged()
        }
        onBoundsChanged += { _, _ ->
            parentWithIndex?.let {
                for (i in it.box.ch.indices) {
                    if (i != it.index) {
                        it.box.ch[i].notifyBrothersBoundsChanged()
                    }
                }
            }
        }
    }

    companion object {
        const val DEFAULT_TEXT_SIZE = 96f
        const val DEFAULT_TEXT_RADIUS = DEFAULT_TEXT_SIZE * 0.5f
        const val DEFAULT_TEXT_WIDTH = DEFAULT_TEXT_SIZE * 0.6f
        const val DEFAULT_LINE_WIDTH = 4f
        const val MAGNIFIER_FACTOR = 1f
        const val SELECTION_CARET_RADIUS = 14f
        const val CARET_OVERFLOW_RADIUS = 18f
        const val MAGNIFIER_RADIUS = DEFAULT_TEXT_SIZE
        val magnifierPath = Path().apply {
            val rx = DEFAULT_TEXT_WIDTH * 3
            val ry = DEFAULT_TEXT_SIZE * 0.75f
            val r = RectF(-rx, -ry, rx, ry)
            addRoundRect(r, MAGNIFIER_RADIUS, MAGNIFIER_RADIUS, Path.Direction.CCW)
        }

        fun commonParent(vararg boxes: FormulaBox) = commonParent(boxes.asIterable())
        fun commonParent(boxes: Iterable<FormulaBox>): CommonParentWithIndices? {
            val pss = boxes.map { it.parents.iterator() }.toList()
            var res: CommonParentWithIndices? = null
            while (true) {
                if (pss.all { it.hasNext() }) {
                    val cpss = pss.map { it.next() }
                    if (cpss.all { it.box == cpss[0].box }) {
                        res = CommonParentWithIndices(cpss[0].box, cpss.map { it.index })
                    } else break
                } else break
            }
            return res
        }

        fun commonParentWithThis(vararg boxes: FormulaBox) = commonParentWithThis(boxes.asIterable())
        fun commonParentWithThis(boxes: Iterable<FormulaBox>): CommonParentWithIndices? {
            val pss = boxes.map { it.parentsAndThis.iterator() }.toList()
            var res: CommonParentWithIndices? = null
            while (true) {
                if (pss.all { it.hasNext() }) {
                    val cpss = pss.map { it.next() }
                    if (cpss.all { it.box == cpss[0].box }) {
                        res = CommonParentWithIndices(cpss[0].box, cpss.map { it.index })
                    } else break
                } else break
            }
            return res
        }
    }
}
