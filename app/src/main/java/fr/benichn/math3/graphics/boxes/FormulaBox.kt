package fr.benichn.math3.graphics.boxes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fr.benichn.math3.graphics.Utils.moveToEnd
import fr.benichn.math3.graphics.caret.BoxCaret
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.FormulaGraphics
import fr.benichn.math3.types.ImmutableList
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.types.Orientation
import fr.benichn.math3.graphics.Utils.sumOfRects
import fr.benichn.math3.graphics.boxes.types.BoxProperty
import fr.benichn.math3.graphics.boxes.types.CommonParentWithIndices
import fr.benichn.math3.graphics.boxes.types.FinalBoxes
import fr.benichn.math3.graphics.boxes.types.FormulaBoxDeserializer
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.PaintedPath
import fr.benichn.math3.graphics.boxes.types.Paints
import fr.benichn.math3.graphics.boxes.types.ParentWithIndex
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.types.Chain
import fr.benichn.math3.types.callback.*
import java.util.LinkedList
import java.util.Queue
import kotlin.math.max

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

    open val sortedChildren: List<FormulaBox>
        get() = ch

    val parentWithIndex
        get() = parent?.let { ParentWithIndex(it, it.ch.indexOf(this)) }
    private fun buildParents(tail: Chain<ParentWithIndex>): Chain<ParentWithIndex> =
        parent?.let{ it.buildParents(Chain.Node(ParentWithIndex(it, it.ch.indexOf(this)), tail)) } ?: tail
    val parents: Chain<ParentWithIndex>
        get() = buildParents(Chain.Empty)
    val parentsAndThis
        get() = buildParents(Chain.singleton(ParentWithIndex(this, -1)))

    fun findNextSingleAfter(child: FormulaBox?, askParent: Boolean = true): CaretPosition.Single? =
        sortedChildren.let { sch ->
            val i = child?.let { c -> sch.indexOf(c) } ?: -1
            (if (i == sch.lastIndex) null else sch.subList(i+1, sch.size).firstNotNullOfOrNull { c ->
                (c as? InputFormulaBox)?.let { inp -> CaretPosition.Single(inp, 0) }
                    ?: c.findNextSingleAfter(null, false)
            }) ?: if (askParent) parent?.let { box ->
                if (box is InputFormulaBox) {
                    val index = box.ch.indexOf(this)
                    CaretPosition.Single(box, index)
                } else box.findNextSingleAfter(this, true)
            } else null
        }

    fun findPreviousSingleBefore(child: FormulaBox?, askParent: Boolean = true): CaretPosition.Single? =
        sortedChildren.let { sch ->
            val i = child?.let { c -> sch.indexOf(c) } ?: sch.size
            sch.subList(0, i).asReversed().firstNotNullOfOrNull { c ->
                (c as? InputFormulaBox)?.let { inp -> CaretPosition.Single(inp, inp.ch.lastIndex) }
                    ?: c.findPreviousSingleBefore(null, false)
            } ?: if (askParent) parent?.let { box ->
                if (box is InputFormulaBox) {
                    val index = box.ch.indexOf(this)
                    CaretPosition.Single(box, index - 1)
                } else box.findPreviousSingleBefore(this, true)
            } else null
        }

    private var caret: BoxCaret? = null
        get() = parent?.caret ?: field

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
    fun addBoxes(vararg boxes: FormulaBox) = addBoxes(children.size, *boxes)
    fun addBoxes(i: Int, vararg boxes: FormulaBox) = addBoxes(i, boxes.asList())
    fun addBoxes(boxes: List<FormulaBox>) =
        addBoxes(children.size, boxes)
    open fun addBoxes(i: Int, boxes: List<FormulaBox>) {
        boxes.forEachIndexed { j, b ->
            b.delete()
            b.parent = this
            children.add(i+j, b)
            connect(b.onBoundsChanged) { s, e -> onChildBoundsChanged(s, e) }
        }
        for (c in children) {
            c.notifyBrothersBoundsChanged()
        }
    }
    fun addBoxesBefore(i: Int, boxes: List<FormulaBox>) = addBoxes(max(1, i), boxes)
    fun addBoxesBefore(i: Int, vararg boxes: FormulaBox) = addBoxesBefore(i, boxes.asList())
    fun addBoxesBefore(c: FormulaBox, boxes: List<FormulaBox>) = addBoxesBefore(children.indexOf(c), boxes)
    fun addBoxesBefore(c: FormulaBox, vararg boxes: FormulaBox) = addBoxesBefore(children.indexOf(c), *boxes)
    fun addBoxesAfter(i: Int, boxes: List<FormulaBox>) = addBoxes(i+1, boxes)
    fun addBoxesAfter(i: Int, vararg boxes: FormulaBox) = addBoxesAfter(i, boxes.asList())
    fun addBoxesAfter(c: FormulaBox, boxes: List<FormulaBox>) = addBoxesAfter(children.indexOf(c), boxes)
    fun addBoxesAfter(c: FormulaBox, vararg boxes: FormulaBox) = addBoxesAfter(children.indexOf(c), *boxes)
    fun removeAllBoxes() = removeBoxes(children.toList())
    fun removeLastBox() { if (children.isNotEmpty()) removeBoxes(children.last()) }
    fun removeBoxes(vararg boxes: FormulaBox) =
        removeBoxes(boxes.asList())
    open fun removeBoxes(boxes: List<FormulaBox>) {
        boxes.forEach { b ->
            assert(b.parent == this)
            disconnectFrom(b)
            setChildTransform(b, BoxTransform())
            children.remove(b)
            b.parent = null
        }
        for (c in boxes) {
            c.notifyBrothersBoundsChanged()
        }
        for (c in children) {
            c.notifyBrothersBoundsChanged()
        }
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

    protected open fun onChildRequiresDelete(b: FormulaBox, vararg anticipation: FormulaBox): DeletionResult =
        if (anticipation.isNotEmpty() || isFilled) {
            delete(this)
        } else {
            delete()
        }
    fun delete(vararg anticipation: FormulaBox) = parent?.onChildRequiresDelete(this, *anticipation) ?: DeletionResult()

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
            CaretPosition.Single.fromBox(c, accTransform.applyOnPoint(pos))
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

    protected fun setChildTransform(b: FormulaBox, bt: BoxTransform) {
        assert(b.parent == this)
        b.transform = bt
    }
    protected fun modifyChildTransform(b: FormulaBox, t: (BoxTransform) -> BoxTransform) = setChildTransform(b, t(b.transform))

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

    protected val updatesQueue: Queue<() -> Unit> = LinkedList()
    var canUpdate = true
        private set

    protected inline fun enqueueOrRun(noinline method: () -> Unit, block: () -> Unit) {
        if (canUpdate) block()
        else updatesQueue.offer(method)
    }

    fun preventUpdate() {
        canUpdate = false
    }
    fun retrieveUpdate() {
        if (!canUpdate) {
            val fs = mutableListOf<() -> Unit>()
            var f: (() -> Unit)?
            val s = updatesQueue.size
            while (updatesQueue.poll().also { f = it } != null) {
                if (fs.lastOrNull() != f) {
                    val i = fs.indexOf(f)
                    if (i >= 0) fs.moveToEnd(i)
                    else fs.add(f!!)
                }
            }
            Log.d("go", "${fs.size} ~ $s")
            canUpdate = true
            for (fn in fs) fn()
        }
    }
    fun discardUpdate() {
        canUpdate = false
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
    fun updateGraphics() = enqueueOrRun(::updateGraphics) { updateGraphics(true) }

    fun getLength(o: Orientation) = when (o) {
        Orientation.H -> bounds.width()
        Orientation.V -> bounds.height()
    }

    private val notifyPictureChanged = Callback<FormulaBox, Unit>(this)
    val onPictureChanged = notifyPictureChanged.Listener()

    fun drawOnCanvas(canvas: Canvas) {
        transform.applyOnCanvas(canvas)

        if (isRoot) {
            caret?.preDrawOnCanvas(canvas)
        }
        canvas.drawRect(bounds, backgroundPaint)
        val (av, bl) = paintedPaths.partition { it.aboveChildren }
        for (p in bl) {
            canvas.drawPath(p.path, p.realPaint)
        }
        for (b in children) {
            b.drawOnCanvas(canvas)
        }
        for (p in av) {
            canvas.drawPath(p.path, p.realPaint)
        }
        if (isRoot) {
            caret?.postDrawOnCanvas(canvas)
        }

        // for (p in caret?.positions.orEmpty()) {
        //     if (isRoot && p is CaretPosition.Single) p.absPos?.also { ap ->
        //
        //     }
        // }

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
    open fun toJson(): JsonElement = throw UnsupportedOperationException(parentsAndThis.joinToString("\n") { it.box.toWolfram() }) // !

    companion object {
        const val DEFAULT_TEXT_SIZE = 70f
        const val DEFAULT_TEXT_RADIUS = DEFAULT_TEXT_SIZE * 0.5f
        const val DEFAULT_TEXT_WIDTH = DEFAULT_TEXT_SIZE * 0.6f
        const val DEFAULT_LINE_WIDTH = 3.4f
        const val MAGNIFIER_FACTOR = 1f
        // const val SELECTION_CARET_RADIUS = 14f
        // const val CARET_OVERFLOW_RADIUS = 18f
        const val MAGNIFIER_RADIUS = DEFAULT_TEXT_SIZE

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

        val deserializers = mutableListOf<FormulaBoxDeserializer<*>>()

        fun makeJsonObject(tag: String, block: JsonObject.() -> Unit) = JsonObject().apply {
            addProperty("tag", tag)
            block()
        }
    }
}
