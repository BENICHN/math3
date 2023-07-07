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
import fr.benichn.math3.Utils.Companion.trim
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
import fr.benichn.math3.types.callback.*

class FormulaView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var box = TransformerFormulaBox(InputFormulaBox(), BoundsTransformer.Align(RectPoint.BOTTOM_CENTER))
    private var caret: BoxCaret
    private val origin
        get() = PointF(width * 0.5f, height - FormulaBox.DEFAULT_TEXT_RADIUS)
    var offset by ObservableProperty(this, PointF()) { _, _ ->
        contextMenu = null
        invalidate()
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
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

        override fun onMove() {
            Log.d("mov", lastAbsDiff.toString())
            offset += lastAbsDiff
            adjustOffset()
        }

    }

    private inner class PlaceCaretAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateDoubleAction().also { it.launch(downAbsPosition, downIndex) })
        }

        override fun onUp() {
            caret.position = box.findSingle(lastPos).noneIfNull()
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

        override fun onMove() {
            replace(MoveViewAction().also { it.launch(downAbsPosition, downIndex) })
        }

    }

    private inner class MoveCaretAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateDoubleAction().also { it.launch(downAbsPosition, downIndex) })
        }

        override fun onUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
            if (replacement == null) {
                caret.absolutePosition = null
            }
        }

        override fun onMove() {
            caret.position = box.findSingle(lastPos).noneIfNull()
            caret.absolutePosition = lastPos
        }

    }

    private inner class CreateDoubleAction : FormulaViewAction() {
        private var downSingle: CaretPosition.Single? = null

        override fun onDown() {
            downSingle = box.findSingle(lastPos)
            caret.absolutePosition = lastPos
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

        override fun beforeFinish(replacement: TouchAction?) {
            caret.absolutePosition = null
            caret.fixedX = null
        }

        override fun onMove() {
            if (downSingle != null) {
                caret.position = box.findSingle(lastPos)?.let { CaretPosition.Double.fromSingles(downSingle!!, it) }.noneIfNull()
                caret.absolutePosition = lastPos
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
            caret.absolutePosition = lastPos
            fixedSingle?.getAbsPosition()?.let { caret.fixedX = it.x }
        }

        override fun onLongDown() {
            replace(CreateDoubleAction().also { it.launch(downAbsPosition, downIndex) })
        }

        override fun onUp() {
            val p = caret.position
            if (p is CaretPosition.Double && p.indexRange.start == p.indexRange.end) {
                caret.position = CaretPosition.Single(p.box as InputFormulaBox, p.indexRange.start)
            }
        }

        override fun beforeFinish(replacement: TouchAction?) {
            if (replacement == null) {
                caret.absolutePosition = null
                caret.fixedX = null
            }
        }

        override fun onMove() {
            fixedSingle?.let { fs ->
                caret.position = box.findSingle(lastPos)?.let { CaretPosition.Double.fromSingles(fs, it) }.noneIfNull()
                caret.absolutePosition = lastPos
            }
        }

    }

    private inner class DoubleInteriorAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateDoubleAction().also { it.launch(downAbsPosition, downIndex) })
        }

        override fun onMove() {
            replace(MoveViewAction().also { it.launch(downAbsPosition, downIndex) })
        }

        override fun onUp() {
            contextMenu = getSelectionContextMenu().also {
                it.origin = RectPoint.TOP_CENTER.get((caret.position as CaretPosition.Double).bounds)
            }
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

    }

    private inner class ContextMenuAction : FormulaViewAction() {
        var downEntry: ContextMenuEntry? = null

        override fun onDown() {
            downEntry = contextMenu!!.findEntry(lastPos)
            downEntry?.let {
                it.box.background = Color.LTGRAY
            }
        }

        override fun onLongDown() {
        }

        override fun onMove() {
            val entry = contextMenu!!.findEntry(lastPos)
            downEntry?.let {
                it.box.background = if (it == entry) Color.LTGRAY else Color.WHITE
            }
        }

        override fun onUp() {
            val entry = contextMenu!!.findEntry(lastPos)
            if (downEntry == entry) {
                entry?.action?.invoke(caret.position)
                contextMenu = null
            }
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

    }

    private inner class SelectionToDoubleAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateDoubleAction().also { it.launch(downAbsPosition, downIndex) })
        }

        override fun onMove() {
            replace(MoveViewAction().also { it.launch(downAbsPosition, downIndex) })
        }

        override fun onUp() {
            val p = caret.position as CaretPosition.DiscreteSelection
            caret.position = CaretPosition.Double.fromBoxes(p.selectedBoxes).noneIfNull()
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
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchAction = when(p) {
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
                    else -> { }
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

        val defaultPadding = Padding(FormulaBox.DEFAULT_TEXT_RADIUS)
    }
}