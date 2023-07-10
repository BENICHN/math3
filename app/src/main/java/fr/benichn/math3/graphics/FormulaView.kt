package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import fr.benichn.math3.Utils.Companion.neg
import fr.benichn.math3.Utils.Companion.pos
import fr.benichn.math3.graphics.Utils.Companion.times
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.boxes.types.Padding
import fr.benichn.math3.graphics.caret.BoxCaret
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.caret.ContextMenuEntry
import fr.benichn.math3.graphics.caret.noneIfNull
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.types.Side
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.graphics.types.TouchData
import fr.benichn.math3.types.callback.*
import kotlin.concurrent.fixedRateTimer
import kotlin.math.sign

class FormulaView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var box = TransformerFormulaBox(InputFormulaBox(), BoundsTransformer.Align(RectPoint.BOTTOM_CENTER))
    private var caret: BoxCaret
    private val origin
        get() = PointF(width * 0.5f, height - FormulaBox.DEFAULT_TEXT_RADIUS)
    var offset by ObservableProperty(this, PointF()) { _, _ ->
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
        adjustOffset()
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
            adjustOffset()
        }
    }

    private fun adjustOffset() {
        val r = defaultPadding.applyOnRect(box.bounds + origin + offset)
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
        offset = PointF(x, y)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        touchAction?.finish()
        contextMenu = null
        adjustOffset()
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

    private abstract inner class FormulaViewAction : TouchAction({ it - (origin + offset) })

    private inner class MoveViewAction : TouchAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
        }

        override fun onUp() {
            accVelocity += velocity
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

        override fun onMove() {
            offset += prim.lastAbsDiff
            adjustOffset()
        }

    }

    private inner class PlaceCaretAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateDoubleAction().also { it.launch(prim.downAbsPosition, prim.id) })
        }

        override fun onUp() {
            caret.position = box.findSingle(prim.lastPosition).noneIfNull()
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

        override fun onMove() {
            replace(MoveViewAction().also { it.launch(prim.downAbsPosition, prim.id) })
        }

    }

    private inner class MoveCaretAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateDoubleAction().also { it.launch(prim.downAbsPosition, prim.id) })
        }

        override fun onUp() {
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

    private inner class CreateDoubleAction : FormulaViewAction() {
        private var downSingle: CaretPosition.Single? = null

        override fun onDown() {
            downSingle = box.findSingle(prim.lastPosition)
            caret.absolutePosition = prim.lastPosition
            downSingle?.getAbsPosition()?.let { caret.fixedX = it.x }
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
            caret.fixedX = null
        }

        override fun onMove() {
            if (downSingle != null) {
                caret.position = box.findSingle(prim.lastPosition)?.let { CaretPosition.Double.fromSingles(downSingle!!, it) }.noneIfNull()
                caret.absolutePosition = prim.lastPosition
            }
        }

    }

    private inner class ModifyDoubleAction(val downSide: Side) : FormulaViewAction() {
        private var fixedSingle: CaretPosition.Single? = null

        override fun onDown() {
            val p = caret.position as CaretPosition.Double
            fixedSingle = when (downSide) {
                Side.L -> p.rightSingle
                Side.R -> p.leftSingle
            }
            caret.absolutePosition = prim.lastPosition
            fixedSingle?.getAbsPosition()?.let { caret.fixedX = it.x }
        }

        override fun onLongDown() {
            replace(CreateDoubleAction().also { it.launch(prim.downAbsPosition, prim.id) })
        }

        override fun onUp() {
            val p = caret.position
            if (p is CaretPosition.Double && p.indexRange.start == p.indexRange.end) {
                caret.position = CaretPosition.Single(p.box as InputFormulaBox, p.indexRange.start)
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
                caret.fixedX = null
            }
        }

        override fun onMove() {
            fixedSingle?.let { fs ->
                caret.position = box.findSingle(prim.lastPosition)?.let { CaretPosition.Double.fromSingles(fs, it) }.noneIfNull()
                caret.absolutePosition = prim.lastPosition
            }
        }

    }

    private inner class DoubleInteriorAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateDoubleAction().also { it.launch(prim.downAbsPosition, prim.id) })
        }

        override fun onMove() {
            replace(MoveViewAction().also { it.launch(prim.downAbsPosition, prim.id) })
        }

        override fun onUp() {
            contextMenu = getSelectionContextMenu().also {
                it.origin = RectPoint.TOP_CENTER.get((caret.position as CaretPosition.Double).bounds)
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
            replace(CreateDoubleAction().also { it.launch(prim.downAbsPosition, prim.id) })
        }

        override fun onMove() {
            replace(MoveViewAction().also { it.launch(prim.downAbsPosition, prim.id) })
        }

        override fun onUp() {
            val p = caret.position as CaretPosition.DiscreteSelection
            caret.position = CaretPosition.Double.fromBoxes(p.selectedBoxes).noneIfNull()
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

    fun sendAdd(newBox: FormulaBox) {
        touchAction?.finish()
        contextMenu = null
        var initialBoxes: InitialBoxes? = null
        val pos = when (val p = caret.position) {
            is CaretPosition.None, is CaretPosition.DiscreteSelection -> { null }
            is CaretPosition.Single -> {
                p
            }
            is CaretPosition.Double -> {
                initialBoxes = InitialBoxes.Selection(
                    p.box.ch.subList(0, p.indexRange.start).toList(),
                    p.box.ch.subList(p.indexRange.start, p.indexRange.end).toList(),
                    p.box.ch.subList(p.indexRange.end, p.box.ch.size).toList())
                for (c in p.selectedBoxes) {
                    c.delete()
                }
                CaretPosition.Single(p.box, p.indexRange.start)
            }
        }
        pos?.also {
            val (box, i) = it
            box.addBox(i, newBox)
            val fb = newBox.addInitialBoxes(initialBoxes ?:
            InitialBoxes.BeforeAfter(
                box.ch.take(i),
                box.ch.takeLast(box.ch.size - i)
            ))
            box.addBoxes(i+1, fb.boxesAfter)
            box.addBoxes(i, fb.boxesBefore)
            caret.position = newBox.getInitialSingle() ?: CaretPosition.Single(box, i+1+fb.boxesBefore.size)
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
                    val (box, i) = p
                    if (i == 0) {
                        fun isInputRoot(b: FormulaBox): Boolean =
                            b.parent?.let { if (it is InputFormulaBox) false else isInputRoot(it) } ?: true
                        if (!isInputRoot(box)) {
                            box.delete()
                        } else {
                            DeletionResult(p)
                        }
                    } else {
                        val b = box.ch[i - 1]
                        if (b.selectBeforeDeletion) {
                            DeletionResult.fromSelection(b)
                        } else {
                            b.delete()
                        }
                    }
                }

                is CaretPosition.Double -> {
                    var res = DeletionResult()
                    for (c in p.selectedBoxes) {
                        res = c.delete()
                    }
                    res
                }

                is CaretPosition.DiscreteSelection -> {
                    var res = DeletionResult()
                    for (c in p.selectedBoxes) {
                        res = c.delete()
                    }
                    res
                }
            }
        val (newPos, fb) = deletionResult
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
        canvas.drawLine(0f, y, o.x + box.bounds.left - FormulaBox.DEFAULT_TEXT_WIDTH * 0.5f, y, baselinePaint)
        canvas.drawLine(width.toFloat(), y, o.x + box.bounds.right + FormulaBox.DEFAULT_TEXT_WIDTH * 0.5f, y, baselinePaint)
        (origin + offset).let { canvas.translate(it.x, it.y) }
        box.drawOnCanvas(canvas)
        contextMenu?.box?.drawOnCanvas(canvas)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        Log.d("touch", "${e.actionIndex}, ${e.getPointerId(e.actionIndex)} ~ $e")
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                accVelocity = PointF()
                if (touchAction == null) {
                    val pos = PointF(e.x, e.y) - (offset + origin)
                    val p = caret.position
                    contextMenu?.also {
                        when (it.findElement(pos)) {
                            ContextMenu.Element.INTERIOR -> {
                                touchAction = ContextMenuAction()
                            }
                            ContextMenu.Element.NONE -> {
                                contextMenu = null
                                if (p is CaretPosition.Double && p.getElement(pos) == CaretPosition.Double.Element.INTERIOR) {
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

                            is CaretPosition.None -> {
                                PlaceCaretAction()
                            }

                            is CaretPosition.Double -> {
                                when (p.getElement(pos)) {
                                    CaretPosition.Double.Element.LEFT_BAR -> ModifyDoubleAction(Side.L)
                                    CaretPosition.Double.Element.RIGHT_BAR -> ModifyDoubleAction(Side.R)
                                    CaretPosition.Double.Element.INTERIOR -> DoubleInteriorAction()
                                    CaretPosition.Double.Element.NONE -> PlaceCaretAction()
                                }
                            }

                            is CaretPosition.DiscreteSelection -> {
                                when (p.getElement(pos)) {
                                    CaretPosition.DiscreteSelection.Element.INTERIOR -> SelectionToDoubleAction()
                                    CaretPosition.DiscreteSelection.Element.NONE -> PlaceCaretAction()
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
    }
}