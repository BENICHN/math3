package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import fr.benichn.math3.Utils.Companion.neg
import fr.benichn.math3.Utils.Companion.pos
import fr.benichn.math3.graphics.Utils.Companion.l2
import fr.benichn.math3.graphics.Utils.Companion.times
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.BoxTransform
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.boxes.types.Range
import fr.benichn.math3.graphics.caret.BoxCaret
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.caret.ContextMenuEntry
import fr.benichn.math3.graphics.caret.noneIfNull
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.types.callback.*
import kotlin.concurrent.fixedRateTimer
import kotlin.math.abs
import kotlin.math.sign

class FormulaView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var box = TransformerFormulaBox(InputFormulaBox(), BoundsTransformer.Align(RectPoint.BOTTOM_CENTER))
    private var caret: BoxCaret
    private val origin
        get() = PointF(width * 0.5f, height - FormulaBox.DEFAULT_TEXT_RADIUS)
    var offset = PointF()
        set(value) {
            field = adjustOffset(value)
            invalidate()
        }

    private var accVelocity = PointF()
    private val velocityTimer = fixedRateTimer(period = VELOCITY_REDUCTION_PERIOD) {
        // Log.d("fix", accVelocity.toString())
        val diff = accVelocity * VELOCITY_REDUCTION_MULT * 0.25f
        val newVel = accVelocity.run { PointF(
            x - VELOCITY_REDUCTION * VELOCITY_REDUCTION_MULT * x.sign,
            y - VELOCITY_REDUCTION * VELOCITY_REDUCTION_MULT * y.sign
        ) }
        accVelocity = newVel.run { PointF(
            if (x.sign == accVelocity.x.sign) x else 0f,
            if (y.sign == accVelocity.y.sign) y else 0f
        ) }
        offset += diff
    }

    private fun moveToCaret() {
        val p = caret.position
        val pos = caret.absolutePosition ?: (p as? CaretPosition.Single)?.getAbsPosition()
        pos?.let { it + origin + offset }?.run {
            // val radius = (p as? CaretPosition.Single)?.radius ?: FormulaBox.DEFAULT_TEXT_RADIUS
            val o = DEFAULT_PADDING // + radius
            val oxr = pos(x + o - width)
            val oxl = neg(x - o)
            val ox =
                if (oxr > 0f) {
                    if (oxl > 0f) {
                        0.5f * (oxl - oxr)
                    } else {
                        -oxr
                    }
                } else {
                    oxl
                }
            val oyb = pos(y + o - height)
            val oyt = neg(y - o)
            val oy =
                if (oyb > 0f) {
                    if (oyt > 0f) {
                        0.5f * (oyt - oyb)
                    } else {
                        -oyb
                    }
                } else {
                    oyt
                }
            offset += PointF(ox, oy)
        }
    }

    fun adjustOffset() {
        offset = offset
    }

    private fun adjustOffset(offset: PointF): PointF {
        val r = defaultPadding.applyOnRect(box.realBounds + origin + offset)
        val x = if (r.width() <= width) 0f else {
            val ol = r.left
            val or = r.right - width
            if (ol > 0) { // marge à gauche
                offset.x - ol
            } else { // depassement gauche
                if (or > 0) { // depassement droite
                    offset.x
                } else {
                    offset.x - or
                }
            }
        }
        val y = if (r.height() <= height) 0f else {
            val ot = r.top
            val ob = r.bottom - height
            if (ot > 0) { // marge en haut
                offset.y - ot
            } else { // depassement haut
                if (ob > 0) { // depassement bas
                    offset.y
                } else {
                    offset.y - ob
                }
            }
        }
        accVelocity = PointF(
            if (x == offset.x) accVelocity.x else 0f,
            if (y == offset.y) accVelocity.y else 0f
        )
        return PointF(x, y)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        touchAction?.finish()
        contextMenu = null
        invalidate()
    }

    var contextMenu by ObservableProperty<FormulaView, ContextMenu?>(this, null) { _, e ->
        e.new?.run {
            onPictureChanged += { _, _ -> invalidate() }
        }
        invalidate()
    }

    var touchAction: TouchAction? by ObservableProperty<FormulaView, TouchAction?>(this, null) { _, e ->
        e.new?.apply {
            onFinished += { _, _ -> touchAction = null }
            onReplaced += { _, ev -> touchAction = ev.new }
        }
    }

    var lastPlaceUp: TouchAction.Move? = null
    var lastWordUp: TouchAction.Move? = null

    private abstract inner class FormulaViewAction : TouchAction({ it - (origin + offset) })

    private inner class MoveViewAction : FormulaViewAction() {
        var baseGap = 0f
        var baseScale = 0f
        var baseOffset = PointF()

        override fun onDown() {
        }

        override fun onLongDown() {
        }

        override fun onUp() {
            accVelocity += velocity
        }

        override fun onPinchDown() {
            baseScale = box.transformer(RectF()).scale
            baseGap = (prim.lastAbsPosition - pinch.lastAbsPosition).length()
            baseOffset = offset
        }

        override fun onPinchMove() {
            val newGap = (prim.lastAbsPosition - pinch.lastAbsPosition).length()
            val ratio = (newGap / baseGap).let { if (abs(baseScale * it - 1f) < ZOOM_TOLERENCE) 1f/baseScale else it }
            val totalDiff = (prim.totalAbsDiff + pinch.totalAbsDiff) * 0.5f
            fun getPos(p: PointF) = p - (origin + baseOffset + totalDiff)
            val c = (getPos(prim.lastAbsPosition) + getPos(pinch.lastAbsPosition)) * 0.5f
            box.transformer = BoundsTransformer.Align(RectPoint.BOTTOM_CENTER) * BoundsTransformer.Constant(BoxTransform.scale(baseScale * ratio))
            offset = baseOffset + totalDiff + c * (1 - ratio)
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

        override fun onMove() {
            offset += prim.lastAbsDiff
        }

    }

    private inner class PlaceCaretAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateSelectionAction())
        }

        override fun onUp() {
            caret.position = box.findSingle(prim.downPosition).noneIfNull()
            lastPlaceUp = Move(prim.downAbsPosition, System.currentTimeMillis())
        }

        override fun onPinchDown() {
            replace(MoveViewAction())
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

        override fun onMove() {
            replace(MoveViewAction())
        }

    }

    private inner class MoveCaretAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateSelectionAction())
        }

        override fun onUp() {
            lastPlaceUp = Move(prim.downAbsPosition, System.currentTimeMillis())
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
            if (replacement == null) {
                caret.absolutePosition = null
            }
        }

        override fun onMove() {
            caret.position = box.findSingle(prim.lastPosition).noneIfNull()
            caret.absolutePosition = prim.lastPosition
        }

    }

    private inner class MoveMultiCaretAction(val index: Int) : FormulaViewAction() {
        override fun onDown() {
            caret.movingSingle = index
        }

        override fun onLongDown() {
            replace(CreateSelectionAction())
        }

        override fun onUp() {
            val ss = (caret.position as CaretPosition.MultiSingle).singles
            caret.position = CaretPosition.MultiSingle(ss.filterIndexed{ i, _ -> i != index })
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
            caret.movingSingle = null
            val np = CaretPosition.MultiSingle((caret.position as CaretPosition.MultiSingle).singles.distinct())
            caret.position = if (np.singles.size == 1) np.singles[0] else np
            if (replacement == null) {
                caret.absolutePosition = null
            }
        }

        override fun onMove() {
            caret.position = CaretPosition.MultiSingle((caret.position as CaretPosition.MultiSingle).singles.mapIndexed { i, s ->
                if (i == index) {
                    box.findSingle(prim.lastPosition)
                } else s
            }.filterNotNull())
            caret.absolutePosition = prim.lastPosition
        }

    }

    private inner class CreateSelectionAction : FormulaViewAction() {
        private var downSingle: CaretPosition.Single? = null

        override fun onDown() {
            downSingle = box.findSingle(prim.lastPosition)
            caret.absolutePosition = prim.lastPosition
            downSingle?.getAbsPosition()?.let { caret.fixedAbsPos = it }
            caret.position = downSingle?.let { CaretPosition.Double.fromSingles(it, it) }.noneIfNull()
        }

        override fun onLongDown() {
        }

        override fun onUp() {
            val p = caret.position
            if (p is CaretPosition.Double && p.indexRange.start == p.indexRange.end) {
                caret.position = CaretPosition.Single(p.box, p.indexRange.start)
            }
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
            caret.absolutePosition = null
            caret.fixedAbsPos = null
        }

        override fun onMove() {
            downSingle?.let { ds ->
                caret.position = box.findSingle(prim.lastPosition)?.let {
                    CaretPosition.GridSelection.fromBoxes(ds.box, it.box) ?:
                        CaretPosition.Double.fromSingles(ds, it)
                }.noneIfNull()
                caret.absolutePosition = prim.lastPosition
            }
        }

    }

    private inner class ModifySelectionAction(val downRectPoint: RectPoint) : FormulaViewAction() {
        private var fixedSingle: CaretPosition.Single? = null

        override fun onDown() {
            fixedSingle = when (val p = caret.position) {
                is CaretPosition.Double -> when (downRectPoint) {
                    RectPoint.CENTER_LEFT -> p.rightSingle
                    RectPoint.CENTER_RIGHT -> p.leftSingle
                    else -> null
                }
                is CaretPosition.GridSelection -> when (downRectPoint) {
                    RectPoint.TOP_LEFT -> p.bottomRightSingle
                    RectPoint.TOP_RIGHT -> p.bottomLeftSingle
                    RectPoint.BOTTOM_LEFT -> p.topRightSingle
                    RectPoint.BOTTOM_RIGHT -> p.topLeftSingle
                    else -> null
                }
                else -> null
            }
            caret.absolutePosition = prim.lastPosition
            fixedSingle?.getAbsPosition()?.let { caret.fixedAbsPos = it }
        }

        override fun onLongDown() {
            replace(CreateSelectionAction())
        }

        override fun onUp() {
            val p = caret.position
            if (p is CaretPosition.Double && p.indexRange.start == p.indexRange.end) {
                caret.position = CaretPosition.Single(p.box, p.indexRange.start)
            }
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
            if (replacement == null) {
                caret.absolutePosition = null
                caret.fixedAbsPos = null
            }
        }

        override fun onMove() {
            fixedSingle?.let { fs ->
                caret.position = box.findSingle(prim.lastPosition)?.let {
                    CaretPosition.GridSelection.fromBoxes(fs.box, it.box) ?:
                    CaretPosition.Double.fromSingles(fs, it)
                }.noneIfNull()
                caret.absolutePosition = prim.lastPosition
            }
        }

    }

    private inner class DisplayContextMenuAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateSelectionAction())
        }

        override fun onMove() {
            replace(MoveViewAction())
        }

        override fun onUp() {
            contextMenu = getSelectionContextMenu().also {
                it.origin = RectPoint.TOP_CENTER.get((caret.position as? CaretPosition.Double)?.bounds ?: (caret.position as CaretPosition.GridSelection).bounds)
            }
        }

        override fun onPinchDown() {
            replace(MoveViewAction())
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

    }

    private inner class ContextMenuAction : FormulaViewAction() {
        var downEntry: ContextMenuEntry? = null

        override fun onDown() {
            downEntry = contextMenu!!.findEntry(prim.lastPosition)
            downEntry?.let {
                it.box.background = Color.LTGRAY
            }
        }

        override fun onLongDown() {
        }

        override fun onMove() {
            val entry = contextMenu!!.findEntry(prim.lastPosition)
            downEntry?.let {
                it.box.background = if (it == entry) Color.LTGRAY else Color.WHITE
            }
        }

        override fun onUp() {
            val entry = contextMenu!!.findEntry(prim.lastPosition)
            if (downEntry == entry) {
                entry?.action?.invoke(caret.position)
                contextMenu = null
            }
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

    }

    private inner class SelectionToDoubleAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateSelectionAction())
        }

        override fun onMove() {
            replace(MoveViewAction())
        }

        override fun onUp() {
            val p = caret.position as CaretPosition.DiscreteSelection
            caret.position = CaretPosition.Double.fromBoxes(p.selectedBoxes).noneIfNull()
        }

        override fun onPinchDown() {
            replace(MoveViewAction())
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

    }

    private inner class SelectWordAction: FormulaViewAction() {
        override fun onDown() {

        }

        override fun onLongDown() {
            caret.position = box.findSingle(prim.downPosition)?.let { s -> CaretPosition.MultiSingle(listOf(s)) }.noneIfNull()
        }

        override fun onMove() {

        }

        override fun onUp() {
            if (!isLongPressed) {
                caret.position = box.findSingle(prim.downPosition)?.let { s ->
                    CaretPosition.Double(s.box, Range(0, s.box.ch.size))
                }.noneIfNull()
            }
        }

        override fun onPinchDown() {

        }

        override fun onPinchMove() {

        }

        override fun onPinchUp() {

        }

        override fun beforeFinish(replacement: TouchAction?) {

        }

    }

    private inner class PlaceMultiCaretAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateSelectionAction())
        }

        override fun onUp() {
            box.findSingle(prim.downPosition)?.let { s ->
                val ss = (caret.position as CaretPosition.MultiSingle).singles
                caret.position = CaretPosition.MultiSingle(if (s in ss) ss - s else ss + s)
            }
        }

        override fun onPinchDown() {
            replace(MoveViewAction())
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

        override fun onMove() {
            replace(MoveViewAction())
        }

    }

    init {
        setWillNotDraw(false)
        box.onPictureChanged += { _, _ ->
            invalidate() }
        box.onBoundsChanged += { _, _ ->
            adjustOffset()
        }
        caret = box.createCaret()
        caret.onPositionChanged += { _, _ ->
            moveToCaret()
        }
        caret.onAbsolutePositionChanged += { _, _ ->
            moveToCaret()
        }
    }

    private fun updateSingle(old: CaretPosition.Single, s: CaretPosition.Single, addedAfter: Int) =
        if (s.box == old.box) {
            if (old.index >= s.index) CaretPosition.Single(old.box, old.index + addedAfter)
            else old
        } else old

    fun sendAdd(newBox: () -> FormulaBox) {
        touchAction?.finish()
        contextMenu = null

        val p = caret.position
        var ss: MutableList<CaretPosition.Single?> = (p as? CaretPosition.MultiSingle)?.singles?.toMutableList() ?: mutableListOf()

        fun addInInput(box: InputFormulaBox, selectionRange: Range): CaretPosition.Single {
            val nb = newBox()
            val i = selectionRange.start
            return if (p is CaretPosition.MultiSingle && p.singles.count { s -> s.box == box } >= 2) {
                box.addBox(i, nb)
                ss.indices.forEach { j -> ss[j] = ss[j]?.let { updateSingle(it, CaretPosition.Single(box, i), 1) } }
                nb.getInitialSingle() ?: CaretPosition.Single(box, i+1)
            } else {
                val ib = InitialBoxes(
                    box.ch.subList(0, selectionRange.start).toList(),
                    box.ch.subList(selectionRange.start, selectionRange.end).toList(),
                    box.ch.subList(selectionRange.end, box.ch.size).toList()
                )
                for (b in ib.selectedBoxes) {
                    b.delete()
                }
                box.addBox(i, nb)
                val fb = nb.addInitialBoxes(ib)
                box.addBoxes(i+1, fb.boxesAfter)
                box.addBoxes(i, fb.boxesBefore)
                ss.indices.forEach { j -> ss[j] = ss[j]?.let { updateSingle(it, CaretPosition.Single(box, i), 1+fb.boxesAfter.size+fb.boxesBefore.size) } }
                nb.getInitialSingle() ?: CaretPosition.Single(box, i+1+fb.boxesBefore.size)
            }
        }

        when (p) {
            is CaretPosition.None, is CaretPosition.DiscreteSelection -> { }
            is CaretPosition.Single -> {
                caret.position = addInInput(p.box, Range(p.index, p.index))
            }
            is CaretPosition.MultiSingle -> {
                for (j in p.singles.indices) {
                    val s = ss[j]!!
                    ss[j] = addInInput(s.box, Range(s.index, s.index))
                }
                caret.position = CaretPosition.MultiSingle(ss.filterNotNull().filter { s -> s.box.root == box }.distinct())
            }
            is CaretPosition.Double -> {
                caret.position = addInInput(p.box, p.indexRange)
            }
            is CaretPosition.GridSelection -> {
                caret.position = CaretPosition.MultiSingle(p.selectedInputs.map { box ->
                    addInInput(box, Range(0, box.ch.size))
                })
            }
        }
    }

    fun sendDelete() {
        touchAction?.finish()
        contextMenu = null

        val deletionResult =
            when (val p = caret.position) {
                is CaretPosition.None -> {
                    DeletionResult()
                }

                is CaretPosition.Single -> {
                    if (p.index == 0) {
                        fun isInputRoot(b: FormulaBox): Boolean =
                            b.parent?.let { if (it is InputFormulaBox) false else isInputRoot(it) } ?: true
                        if (!isInputRoot(p.box)) {
                            p.box.delete()
                        } else {
                            DeletionResult(null, p)
                        }
                    } else {
                        val b = p.box.ch[p.index - 1]
                        if (b.selectBeforeDeletion) {
                            DeletionResult.fromSelection(b)
                        } else {
                            b.delete()
                        }
                    }
                }

                is CaretPosition.MultiSingle -> {
                    val ss = p.singles.toMutableList<CaretPosition.Single?>()
                    fun deleteOrForce(b: FormulaBox): CaretPosition.Single? = b.delete().let { dr ->
                        if (dr.newPos is CaretPosition.Single) {
                            dr.deletedSingle?.let { ds -> ss.indices.forEach { j -> ss[j] = ss[j]?.let { updateSingle(it, ds, -1) } } }
                            dr.newPos.box.addFinalBoxes(dr.newPos.index, dr.finalBoxes)
                            ss.indices.forEach { j -> ss[j] = ss[j]?.let { updateSingle(it, dr.newPos, dr.finalBoxes.boxesAfter.size + dr.finalBoxes.boxesBefore.size) } }
                            CaretPosition.Single(dr.newPos.box, dr.newPos.index + dr.finalBoxes.boxesBefore.size)
                        } else {
                            b.forceDelete()
                        }
                    }
                    p.singles.indices.forEach { j ->
                        val s = ss[j]!!
                        ss[j] = if (s.box.root == box) {
                            if (s.index == 0) {
                                fun isInputRoot(b: FormulaBox): Boolean =
                                    b.parent?.let {
                                        if (it is InputFormulaBox) false else isInputRoot(
                                            it
                                        )
                                    } ?: true
                                if (!isInputRoot(s.box)) {
                                    deleteOrForce(s.box)
                                } else {
                                    s
                                }
                            } else {
                                val b = s.box.ch[s.index - 1]
                                deleteOrForce(b)
                            }
                        } else null
                    }
                    DeletionResult(null, CaretPosition.MultiSingle(
                        ss.filterNotNull().filter { s -> s.box.root == box }.distinct()
                    ))
                }

                is CaretPosition.Double -> {
                    p.selectedBoxes.map { it.delete() }.last()
                }

                is CaretPosition.DiscreteSelection -> {
                    p.selectedBoxes.map { it.delete() }.last()
                }

                is CaretPosition.GridSelection -> {
                    DeletionResult(null, CaretPosition.MultiSingle(p.selectedInputs.map {
                        it.removeAllBoxes()
                        it.lastSingle
                    }))
                }
            }

        val (_, newPos, fb) = deletionResult
        caret.position = when (newPos) {
            is CaretPosition.Single -> {
                if (!fb.isEmpty) {
                    newPos.box.addFinalBoxes(newPos.index, fb)
                } else {
                    newPos
                }
            }
            else -> newPos
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundPaint.color)
        val o = origin + offset
        val y = box.child.transform.origin.y + o.y
        canvas.drawLine(0f, y, o.x + box.realBounds.left - FormulaBox.DEFAULT_TEXT_WIDTH * 0.5f, y, baselinePaint)
        canvas.drawLine(width.toFloat(), y, o.x + box.realBounds.right + FormulaBox.DEFAULT_TEXT_WIDTH * 0.5f, y, baselinePaint)
        (origin + offset).let { canvas.translate(it.x, it.y) }
        box.drawOnCanvas(canvas)
        contextMenu?.box?.drawOnCanvas(canvas)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Log.d("touch", "${e.actionIndex}, ${e.getPointerId(e.actionIndex)} ~ $e")
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                accVelocity = PointF()
                if (touchAction == null) {
                    val ap = PointF(e.x, e.y)
                    val pos = ap - (offset + origin)
                    val p = caret.position
                    lastPlaceUp?.also { (absPos, t) ->
                        if ((System.currentTimeMillis() - t < DOUBLE_TAP_DELAY) && l2(absPos - ap) <= BoxCaret.SINGLE_MAX_TOUCH_DIST_SQ) {
                            touchAction = SelectWordAction()
                        } else {
                            lastPlaceUp = null
                        }
                    } ?: contextMenu?.also {
                        when (it.findElement(pos)) {
                            ContextMenu.Element.INTERIOR -> {
                                touchAction = ContextMenuAction()
                            }
                            ContextMenu.Element.NONE -> {
                                contextMenu = null
                                if (p is CaretPosition.Double && p.getElement(pos) == CaretPosition.Double.Element.INTERIOR
                                    || p is CaretPosition.GridSelection && p.getElement(pos) == CaretPosition.GridSelection.Element.INTERIOR) {
                                    touchAction = PlaceCaretAction()
                                }
                            }
                        }
                    }
                    if (touchAction == null) {
                        touchAction = when (p) {
                            is CaretPosition.Single -> {
                                when (p.getElement(pos)) {
                                    CaretPosition.Single.Element.BAR ->
                                        MoveCaretAction()

                                    CaretPosition.Single.Element.NONE ->
                                        PlaceCaretAction()
                                }
                            }

                            is CaretPosition.MultiSingle -> {
                                when (val i = p.getBarIndex(pos)) {
                                    -1 ->
                                        PlaceMultiCaretAction()
                                    else ->
                                        MoveMultiCaretAction(i)
                                }
                            }

                            is CaretPosition.None -> {
                                PlaceCaretAction()
                            }

                            is CaretPosition.Double -> {
                                when (p.getElement(pos)) {
                                    CaretPosition.Double.Element.LEFT_BAR -> ModifySelectionAction(RectPoint.CENTER_LEFT)
                                    CaretPosition.Double.Element.RIGHT_BAR -> ModifySelectionAction(RectPoint.CENTER_RIGHT)
                                    CaretPosition.Double.Element.INTERIOR -> DisplayContextMenuAction()
                                    CaretPosition.Double.Element.NONE -> PlaceCaretAction()
                                }
                            }

                            is CaretPosition.DiscreteSelection -> {
                                when (p.getElement(pos)) {
                                    CaretPosition.DiscreteSelection.Element.INTERIOR -> SelectionToDoubleAction()
                                    CaretPosition.DiscreteSelection.Element.NONE -> PlaceCaretAction()
                                }
                            }

                            is CaretPosition.GridSelection -> {
                                when (p.getElement(pos)) {
                                    CaretPosition.GridSelection.Element.CORNER_TL -> ModifySelectionAction(RectPoint.TOP_LEFT)
                                    CaretPosition.GridSelection.Element.CORNER_TR -> ModifySelectionAction(RectPoint.TOP_RIGHT)
                                    CaretPosition.GridSelection.Element.CORNER_BR -> ModifySelectionAction(RectPoint.BOTTOM_RIGHT)
                                    CaretPosition.GridSelection.Element.CORNER_BL -> ModifySelectionAction(RectPoint.BOTTOM_LEFT)
                                    CaretPosition.GridSelection.Element.INTERIOR -> DisplayContextMenuAction()
                                    CaretPosition.GridSelection.Element.NONE -> PlaceCaretAction()
                                }
                            }
                        }
                    }
                }
            }
        }
        runTouchAction(e)
        return true
    }

    private fun runTouchAction(e: MotionEvent) {
        touchAction?.also {
            it.onTouchEvent(e)
            if (touchAction != it) { // en cas de remplacement
                runTouchAction(e)
            }
        }
    }

    companion object {
        val red = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.RED }
        val baselinePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.BLACK }
        val backgroundPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.DKGRAY
        }
        val magnifierBorder = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.LTGRAY
        }

        fun getSelectionContextMenu() = ContextMenu(
            ContextMenuEntry.create<CaretPosition.Double>(TextFormulaBox("copy")) {  },
            ContextMenuEntry.create<CaretPosition.Double>(TextFormulaBox("cut")) {  },
            ContextMenuEntry.create<CaretPosition.Double>(TextFormulaBox("paste")) {  }
        )

        const val DEFAULT_PADDING = FormulaBox.DEFAULT_TEXT_WIDTH
        val defaultPadding = Padding(DEFAULT_PADDING)
        const val VELOCITY_REDUCTION_PERIOD = 8L
        const val VELOCITY_REDUCTION_MULT = VELOCITY_REDUCTION_PERIOD * 0.001f
        const val VELOCITY_REDUCTION = 25000f // en px.s^-2
        const val ZOOM_TOLERENCE = 0.1f
        const val DOUBLE_TAP_DELAY = 150
    }
}