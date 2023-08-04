package fr.benichn.math3

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import fr.benichn.math3.graphics.FormulaViewer
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.caret.ContextMenu
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.types.callback.ObservableProperty
import fr.benichn.math3.graphics.caret.ContextMenuEntry

class ContextMenuView(context: Context, attrs: AttributeSet? = null) : FormulaViewer(context, attrs) {
    override val initialBoxTransformers: Array<BoundsTransformer>
        get() = arrayOf(BoundsTransformer.Align(RectPoint.TOP_LEFT))

    var contextMenu by ObservableProperty<ContextMenuView, ContextMenu?>(this, null).apply {
        onChanged += { _, e ->
            child = e.new?.box ?: FormulaBox()
        }
    }

    init {
        fitToBox = true
    }

    private fun findContextMenuEntry(pos: PointF) = contextMenu?.findEntry(child.transform.invert.applyOnPoint(pos))

    private inner class ContextMenuAction : TouchAction() {
        var downEntry: ContextMenuEntry? = null
        val downEntryIndex
            get() = contextMenu!!.ents.indexOf(downEntry)

        override fun onDown() {
            downEntry = findContextMenuEntry(prim.lastPosition)
            downEntry?.let {
                contextMenu!!.fb.pressedItem = downEntryIndex
            }
        }

        override fun onLongDown() {
        }

        override fun onMove() {
            val entry = findContextMenuEntry(prim.lastPosition)
            downEntry?.let {
                contextMenu!!.fb.pressedItem = if (it == entry) downEntryIndex else null
            }
        }

        override fun onUp() {
            contextMenu?.let { cm ->
                val entry = findContextMenuEntry(prim.lastPosition)
                if (downEntry == entry) {
                    entry?.finalAction?.invoke()
                    cm.destroyPopup()
                }
                else cm.fb.pressedItem = null
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

    override fun createTouchAction(e: MotionEvent) {
        touchAction = ContextMenuAction()
    }
}