package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.CountDownTimer
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.minus
import androidx.core.graphics.plus
import androidx.core.view.GestureDetectorCompat
import fr.benichn.math3.graphics.Utils.Companion.l2
import fr.benichn.math3.graphics.boxes.TransformerFormulaBox
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.InputFormulaBox
import fr.benichn.math3.graphics.boxes.TextFormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.boxes.types.DeletionResult
import fr.benichn.math3.graphics.boxes.types.InitialBoxes
import fr.benichn.math3.graphics.caret.BoxCaret
import fr.benichn.math3.graphics.caret.CaretPosition
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.caret.ContextMenuEntry
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.types.Side
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.types.callback.*
import fr.benichn.math3.types.callback.ValueChangedEvent
import kotlin.math.pow

class FormulaView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var box = TransformerFormulaBox(InputFormulaBox(), BoundsTransformer.Align(RectPoint.BOTTOM_CENTER))
    private var caret: BoxCaret
    private val offset
        get() = PointF(width * 0.5f, height - 48f)
    private fun getRootPos(e: MotionEvent) = offset.run { PointF(e.x - x, e.y - y) }

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

    private abstract inner class FormulaViewAction : TouchAction({ getRootPos(it) })

    private inner class MoveViewAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
        }

        override fun onUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

        override fun onMove() {
            Log.d("mov", "aaa")
        }

    }

    private inner class PlaceCaretAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateSelectionAction().also { it.launch(downPosition, downIndex) })
        }

        override fun onUp() {
            caret.position = box.findBox(lastPos).toCaretPosition()
        }

        override fun beforeFinish(replacement: TouchAction?) {
        }

        override fun onMove() {
            replace(MoveViewAction().also { it.launch(downPosition, downIndex) })
        }

    }

    private inner class MoveCaretAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateSelectionAction().also { it.launch(downPosition, downIndex) })
        }

        override fun onUp() {
        }

        override fun beforeFinish(replacement: TouchAction?) {
            if (replacement == null) {
                caret.absolutePosition = null
            }
        }

        override fun onMove() {
            caret.position = box.findBox(lastPos).toCaretPosition()
            caret.absolutePosition = lastPos
        }

    }

    private inner class CreateSelectionAction : FormulaViewAction() {
        private var downSingle: CaretPosition.Single? = null

        override fun onDown() {
            downSingle = box.findBox(lastPos).toSingle()
            caret.absolutePosition = lastPos
            downSingle?.getAbsPosition()?.let { caret.fixedX = it.x }
            caret.position = downSingle?.let { CaretPosition.Selection.fromSingles(it, it) } ?: CaretPosition.None
        }

        override fun onLongDown() {
        }

        override fun onUp() {
            val p = caret.position
            if (p is CaretPosition.Selection && p.indexRange.start == p.indexRange.end) {
                caret.position = CaretPosition.Single(p.box as InputFormulaBox, p.indexRange.start)
            }
        }

        override fun beforeFinish(replacement: TouchAction?) {
            caret.absolutePosition = null
            caret.fixedX = null
        }

        override fun onMove() {
            if (downSingle != null) {
                caret.position = box.findBox(lastPos).toSingle()?.let { CaretPosition.Selection.fromSingles(downSingle!!, it) } ?: CaretPosition.None
                caret.absolutePosition = lastPos
            }
        }

    }

    private inner class ModifySelectionAction(val downSide: Side) : FormulaViewAction() {
        private var fixedSingle: CaretPosition.Single? = null

        override fun onDown() {
            val p = caret.position as CaretPosition.Selection
            fixedSingle = when (downSide) {
                Side.L -> p.rightSingle
                Side.R -> p.leftSingle
            }
            caret.absolutePosition = lastPos
            fixedSingle?.getAbsPosition()?.let { caret.fixedX = it.x }
        }

        override fun onLongDown() {
            replace(CreateSelectionAction().also { it.launch(downPosition, downIndex) })
        }

        override fun onUp() {
            val p = caret.position
            if (p is CaretPosition.Selection && p.indexRange.start == p.indexRange.end) {
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
                caret.position = box.findBox(lastPos).toSingle()?.let { CaretPosition.Selection.fromSingles(fs, it) } ?: CaretPosition.None
                caret.absolutePosition = lastPos
            }
        }

    }

    private inner class SelectionInteriorAction : FormulaViewAction() {
        override fun onDown() {
        }

        override fun onLongDown() {
            replace(CreateSelectionAction().also { it.launch(downPosition, downIndex) })
        }

        override fun onMove() {
            replace(MoveViewAction().also { it.launch(downPosition, downIndex) })
        }

        override fun onUp() {
            contextMenu = getSelectionContextMenu().apply {
                origin = RectPoint.TOP_CENTER.get((caret.position as CaretPosition.Selection).bounds)
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

    init {
        setWillNotDraw(false)
        box.onPictureChanged += { _, _ ->
            invalidate() }
        caret = box.createCaret()
    }

    fun sendAdd(newBox: FormulaBox) {
        touchAction?.finish()
        contextMenu = null
        var initialBoxes: InitialBoxes? = null
        val pos = when (val p = caret.position) {
            is CaretPosition.None -> { null }
            is CaretPosition.Single -> {
                p
            }
            is CaretPosition.Selection -> {
                initialBoxes = InitialBoxes.Selection(p.selectedBoxes)
                for (c in p.selectedBoxes) {
                    c.delete()
                }
                when (p.box) {
                    is InputFormulaBox -> CaretPosition.Single(p.box, p.indexRange.start)
                    else -> null
                }
            }
        }
        pos?.also {
            val (box, i) = it
            box.addBox(i, newBox)
            newBox.addInitialBoxes(initialBoxes ?:
            InitialBoxes.BeforeAfter(
                box.ch.take(i),
                box.ch.takeLast(box.ch.size - i)
            ))
            caret.position = newBox.getInitialCaretPos().toCaretPosition()
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

                is CaretPosition.Selection -> {
                    var res = DeletionResult()
                    for (c in p.selectedBoxes) {
                        res = c.delete()
                    }
                    res
                }
            }
        val (newPos, fb) = deletionResult
        caret.position = when (newPos) {
            is CaretPosition.None -> { newPos }
            is CaretPosition.Single -> {
                if (!fb.isEmpty) {
                    newPos.box.addFinalBoxes(newPos.index, fb)
                } else {
                    newPos
                }
            }
            is CaretPosition.Selection -> { newPos }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(backgroundPaint.color)
        offset.apply { canvas.translate(x, y) }
        box.drawOnCanvas(canvas)
        contextMenu?.box?.drawOnCanvas(canvas)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (touchAction == null) {
            val pos = getRootPos(e)
            val p = caret.position
            contextMenu?.also {
                when (it.findElement(pos)) {
                    ContextMenu.Element.INTERIOR -> {
                        touchAction = ContextMenuAction()
                    }
                    ContextMenu.Element.NONE -> {
                        contextMenu = null
                        if (p is CaretPosition.Selection && p.getElement(pos) == CaretPosition.Selection.Element.INTERIOR) {
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
                            is CaretPosition.Selection -> {
                                when (p.getElement(pos)) {
                                    CaretPosition.Selection.Element.LEFT_BAR -> ModifySelectionAction(Side.L)
                                    CaretPosition.Selection.Element.RIGHT_BAR -> ModifySelectionAction(Side.R)
                                    CaretPosition.Selection.Element.INTERIOR -> SelectionInteriorAction()
                                    CaretPosition.Selection.Element.NONE -> PlaceCaretAction()
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
            ContextMenuEntry.create<CaretPosition.Selection>(TextFormulaBox("copy")) {  },
            ContextMenuEntry.create<CaretPosition.Selection>(TextFormulaBox("cut")) {  },
            ContextMenuEntry.create<CaretPosition.Selection>(TextFormulaBox("paste")) {  }
        )
    }
}