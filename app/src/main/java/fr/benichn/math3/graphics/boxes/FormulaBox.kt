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
import fr.benichn.math3.graphics.Utils.Companion.sumOfRects
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.CommonParentWithIndices
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.PaintedPath
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.boxes.types.ParentWithIndex
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.caret.ContextMenu
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

    val contextMenu: ContextMenu?
        get() = generateContextMenu()?.also { it.source = this } ?: parent?.contextMenu
    open fun generateContextMenu(): ContextMenu? = null

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

    fun replaceBox(i: Int, b: FormulaBox) {
        removeBoxAt(i)
        addBox(i, b)
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

    open val isFilled: Boolean
        get() = children.any { it.isFilled }
    open fun clear() {
        children.forEach { it.clear() }
    }

    open fun getFinalBoxes() = FinalBoxes()

    protected inline fun doOnChildrenIfNotFilled(vararg boxes: FormulaBox, action: () -> Unit) =
        deleteChildrenIfNotFilled(*boxes) {
            action()
            DeletionResult()
        }
    protected inline fun deleteChildrenIfNotFilled(vararg boxes: FormulaBox, deletion: () -> DeletionResult) =
        if (boxes.any { it.isFilled }) {
            DeletionResult(CaretPosition.DiscreteSelection(this, boxes.map { ch.indexOf(it) }))
        } else {
            deletion()
        }
    protected fun deleteIfNotFilled(vararg boxesToCheck: FormulaBox = arrayOf(this), anticipation: Array<out FormulaBox> = boxesToCheck) =
        if (boxesToCheck.any { it.isFilled }) {
            delete(*anticipation)
        } else {
            delete()
        }
    // protected inline fun selectChIfThisFilledOrDelete(vararg boxes: FormulaBox, deletion: () -> DeletionResult = { delete() }) =
    //     if (parent !is InputFormulaBox) {
    //         delete()
    //     } else {
    //         if (isFilled) {
    //             if (boxes.isEmpty()) DeletionResult.fromSelection(this) else DeletionResult(CaretPosition.DiscreteSelection(this, boxes.map { ch.indexOf(it) }))
    //         } else {
    //             deletion()
    //         }
    //     }

    protected open fun onChildRequiresDelete(b: FormulaBox, vararg anticipation: FormulaBox): DeletionResult =
        if (anticipation.isNotEmpty() || isFilled) {
            delete(this)
        } else {
            delete()
        }
    fun delete(vararg anticipation: FormulaBox) = parent?.onChildRequiresDelete(this, *anticipation) ?: DeletionResult()

    fun forceDelete(): CaretPosition.Single? = parentWithIndex?.let { (p, i) ->
        if (p is InputFormulaBox) {
            p.removeBoxAt(i)
            CaretPosition.Single(p, i)
        }
        else p.forceDelete()
    }

    open fun deleteMultiple(boxes: List<FormulaBox>) = when (boxes.size) {
        0 -> DeletionResult()
        1 -> boxes[0].delete()
        else -> delete()
    }

    open fun getInitialSingle(): CaretPosition.Single? = null

    open fun shouldEnterInChild(c: FormulaBox, pos: PointF) =
        true

    open fun findChildBox(pos: PointF) : FormulaBox {
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
                CaretPosition.Single.fromBox(c, accTransform.applyOnPoint(pos))
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
            if (old.pictures != value.pictures) notifyPaintedPathsChanged(old.pictures, value.pictures)
            if (old.bounds != value.bounds) notifyBoundsChanged(old.bounds, value.bounds)
        }
    private val notifyBoundsChanged = VCC<FormulaBox, RectF>(this)
    val onBoundsChanged = notifyBoundsChanged.Listener()
    val bounds
        get() = graphics.bounds
    private val notifyPaintedPathsChanged = VCC<FormulaBox, List<PaintedPath>>(this)
    val onPaintedPathsChanged = notifyPaintedPathsChanged.Listener()
    val paintedPaths
        get() = graphics.pictures

    private var backgroundPaint = Paints.transparent
    val dlgBackground = BoxProperty<FormulaBox, Int?>(this, null, false).apply {
        onChanged += { _, e ->
            backgroundPaint = Paints.fill(e.new ?: generatedGraphics?.background ?: Color.TRANSPARENT)
            notifyPictureChanged()
        }
    }
    var background by dlgBackground
    fun setBackgroundRecursive(color: Int) = setBackgroundRecursive { _ -> color }
    fun setBackgroundRecursive(color: (FormulaBox) -> Int) {
        background = color(this)
        for (c in children) c.setBackgroundRecursive(color)
    }

    val dlgForeground = BoxProperty<FormulaBox, Int?>(this, null, false).apply {
        onChanged += { _, e ->
            generatedGraphics?.pictures?.forEach { p ->
                p.forcedColor = e.new
            }
            notifyPictureChanged()
        }
    }
    var foreground by dlgForeground
    fun setForegroundRecursive(color: Int) = setForegroundRecursive { _ -> color }
    fun setForegroundRecursive(color: (FormulaBox) -> Int) {
        foreground = color(this)
        for (c in children) c.setForegroundRecursive(color)
    }

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
        PaintedPath(),
        bounds = sumOfRects(ch.map { it.realBounds })
    )

    private var generatedGraphics: FormulaGraphics? = null
    private fun updateGraphics(regenerate: Boolean) {
        if (regenerate) {
            val g = generateGraphics()
            g.pictures.forEach { p -> p.forcedColor = foreground }
            backgroundPaint = Paints.fill(background ?: g.background)
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
            for (p in paintedPaths) {
                canvas.drawPath(p.path, p.realPaint)
            }
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
            if (e.old.pictures != e.new.pictures) {
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

    open fun toSage(): String = children.joinToString("") { c -> c.toSage() }
    open fun toMaxima(): String = children.joinToString("") { c -> c.toMaxima() }
    open fun toWolfram(): String = children.joinToString("") { c -> c.toWolfram() }

    companion object {
        const val DEFAULT_TEXT_SIZE = 80f
        const val DEFAULT_TEXT_RADIUS = DEFAULT_TEXT_SIZE * 0.5f
        const val DEFAULT_TEXT_WIDTH = DEFAULT_TEXT_SIZE * 0.6f
        const val DEFAULT_LINE_WIDTH = 3.4f
        const val MAGNIFIER_FACTOR = 1f
        // const val SELECTION_CARET_RADIUS = 14f
        // const val CARET_OVERFLOW_RADIUS = 18f
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
