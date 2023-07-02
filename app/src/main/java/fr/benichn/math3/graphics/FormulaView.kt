package fr.benichn.math3.graphics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.plus
import androidx.core.view.GestureDetectorCompat
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
import fr.benichn.math3.types.callback.CallbackLink
import kotlin.math.pow

class FormulaView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var box = TransformerFormulaBox(InputFormulaBox(), BoundsTransformer.Align(RectPoint.BOTTOM_CENTER))
    private var caret: BoxCaret

    private data class ContextMenuWithOrigin(
        val contextMenu: ContextMenu,
        val origin: PointF
    ) {
        val bounds
            get() = contextMenu.box.bounds + origin
    }
    private var contextMenuConnection: CallbackLink<FormulaBox, Unit>? = null
    private var contextMenuWithOrigin: ContextMenuWithOrigin? = null
        set(value) {
            contextMenuConnection?.disconnect()
            contextMenuConnection = null
            field = value
            value?.let {
                contextMenuConnection = CallbackLink(it.contextMenu.onPictureChanged) { _, _ ->
                    invalidate()
                }
            }
            invalidate()
        }
    private val contextMenu
        get() = contextMenuWithOrigin?.contextMenu
    private var currentContextMenuEntry: ContextMenuEntry? = null

    private val offset
        get() = PointF(width * 0.5f, height - 48f)

    init {
        setWillNotDraw(false)
        box.onPictureChanged += { _, _ ->
            invalidate() }
        caret = box.createCaret()
        caret.onPositionChanged += { _, e ->
            when (e.new) {
                is CaretPosition.None, is CaretPosition.Single -> {
                    if (fixedXOnDown != null) {
                        isMovingCaret = false
                        fixedXOnDown = null
                        selectionModificationStart = null
                    }
                    selectionStartSingle = null
                    caret.fixedX = null
                    caret.absolutePosition = null
                }
                else -> { }
            }
        }
    }
    private var caretPosOnDown: CaretPosition.Single? = null
    private var fixedXOnDown: Float? = null
    private var selectionModificationStart: CaretPosition.Single? = null
    private var isMovingCaret = false
    private var selectionStartSingle: CaretPosition.Single? = null
    private fun getRootPos(e: MotionEvent) = offset.run { PointF(e.x - x, e.y - y) }
    private fun findBox(e: MotionEvent) = box.findBox(getRootPos(e))

    private val gestureDetector = GestureDetectorCompat(context, object : OnGestureListener {
        override fun onDown(e: MotionEvent): Boolean {
            val s = findBox(e).toSingle()
            val p = caret.position
            when {
                p == s -> { // => p is Single
                    caretPosOnDown = s
                }
                p is CaretPosition.Selection && p.isMutable -> {
                    val pos = getRootPos(e)
                    contextMenuWithOrigin?.bounds?.let {
                        if (it.contains(pos.x, pos.y)) {
                            val entry = contextMenu!!.findEntry(pos)
                            currentContextMenuEntry = entry
                        }
                    } ?: p.bounds.apply {
                        if (Utils.squareDistFromLineToPoint(right, top, bottom, pos.x, pos.y) < MAX_TOUCH_DIST_SQ) {
                            contextMenuWithOrigin = null
                            fixedXOnDown = left
                            selectionModificationStart = p.leftSingle
                        } else if (Utils.squareDistFromLineToPoint(left, top, bottom, pos.x, pos.y) < MAX_TOUCH_DIST_SQ) {
                            contextMenuWithOrigin = null
                            fixedXOnDown = right
                            selectionModificationStart = p.rightSingle
                        } else if (contains(pos.x, pos.y)) {
                        }
                        else {
                            contextMenuWithOrigin = null
                        }
                    }
                }
                else -> { }
            }
            return true
        }

        override fun onShowPress(e: MotionEvent) {
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val p = caret.position
            val pos = getRootPos(e)
            if (p is CaretPosition.Selection && p.bounds.contains(pos.x, pos.y)) {
                contextMenuWithOrigin = ContextMenuWithOrigin(
                    selectionContextMenu,
                    RectPoint.TOP_CENTER.get(p.bounds)
                )
            } else {
                val b = findBox(e)
                caret.position = b.toCaretPosition()
            }
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (!isMovingCaret) {
                caretPosOnDown = null
                selectionStartSingle = findBox(e).toSingle()
            }
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
            return true
        }

    })

    fun sendAdd(newBox: FormulaBox) {
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
        contextMenuWithOrigin?.let {
            it.origin.apply { canvas.translate(x, y) }
            it.contextMenu.box.drawOnCanvas(canvas)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        selectionStartSingle?.let { sp ->
            when (e.action) {
                MotionEvent.ACTION_MOVE -> {
                    if (caret.fixedX == null) {
                        caret.fixedX = sp.getAbsPosition().x
                    }
                    caret.position = findBox(e).toSingle()?.let { p -> CaretPosition.Selection.fromSingles(sp, p) } ?: CaretPosition.None
                    caret.absolutePosition = getRootPos(e)
                }

                MotionEvent.ACTION_UP -> {
                    selectionStartSingle = null
                    caret.absolutePosition = null
                    caret.fixedX = null
                    when (val p = caret.position) {
                        is CaretPosition.Selection -> {
                            if (p.isMutable && p.indexRange.start == p.indexRange.end) {
                                caret.position = CaretPosition.Single(
                                    p.box as InputFormulaBox,
                                    p.indexRange.start
                                )
                            }
                        }
                        else -> { }
                    }
                }

                else -> {
                }
            }
            true
        } ?: caretPosOnDown?.let { cp ->
            when (e.action) {
                MotionEvent.ACTION_MOVE -> {
                    val p = findBox(e).toCaretPosition()
                    if (p != cp) {
                        isMovingCaret = true
                    }
                    caret.position = p
                    caret.absolutePosition = getRootPos(e)
                }

                MotionEvent.ACTION_UP -> {
                    isMovingCaret = false
                    caretPosOnDown = null
                    caret.absolutePosition = null
                }

                else -> {
                }
            }
            true
        } ?: fixedXOnDown?.let { x ->
            when (e.action) {
                MotionEvent.ACTION_MOVE -> {
                    isMovingCaret = true
                    if (caret.fixedX == null) {
                        caret.fixedX = x
                    }
                    caret.position = findBox(e).toSingle()?.let { p -> CaretPosition.Selection.fromSingles(selectionModificationStart!!, p) } ?: CaretPosition.None
                    caret.absolutePosition = getRootPos(e)
                }

                MotionEvent.ACTION_UP -> {
                    isMovingCaret = false
                    fixedXOnDown = null
                    selectionModificationStart = null
                    caret.absolutePosition = null
                    caret.fixedX = null
                    when (val p = caret.position) {
                        is CaretPosition.Selection -> {
                            if (p.isMutable && p.indexRange.start == p.indexRange.end) {
                                caret.position = CaretPosition.Single(
                                    p.box as InputFormulaBox,
                                    p.indexRange.start
                                )
                            }
                        }
                        else -> { }
                    }
                }

                else -> {
                }
            }
            true
        } ?: currentContextMenuEntry?.let {
            when (e.action) {
                MotionEvent.ACTION_UP -> {
                    if (it == contextMenu!!.findEntry(getRootPos(e))) {
                        it.action(caret.position)
                    }
                    contextMenuWithOrigin = null
                }
            }
        }
        gestureDetector.onTouchEvent(e)
        return true
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

        val MAX_TOUCH_DIST_SQ = 18f.pow(2)

        val selectionContextMenu = ContextMenu(
            ContextMenuEntry.create<CaretPosition.Selection>(TextFormulaBox("copy")) {  },
            ContextMenuEntry.create<CaretPosition.Selection>(TextFormulaBox("cut")) {  },
            ContextMenuEntry.create<CaretPosition.Selection>(TextFormulaBox("paste")) {  }
        )
    }
}