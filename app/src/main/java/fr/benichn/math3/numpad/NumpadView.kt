package fr.benichn.math3.numpad

import android.content.Context
import android.util.AttributeSet
import android.util.SizeF
import android.view.MotionEvent
import fr.benichn.math3.graphics.FormulaViewer
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.numpad.types.Direction
import fr.benichn.math3.types.callback.Callback
import org.json.JSONObject

class NumpadView(context: Context, attrs: AttributeSet? = null) : FormulaViewer(context, attrs) {
    init {
        child = NumpadFormulaBox(
            JSONObject(context.assets.open("numpad_pages.json").reader().use { it.readText() }),
            SizeF(width.toFloat(), height.toFloat())
        )
    }

    override val initialBoxTransformers: Array<BoundsTransformer>
        get() = arrayOf(BoundsTransformer.Align(RectPoint.TOP_LEFT))

    private val numpadBox = child as NumpadFormulaBox
    private val page
        get() = numpadBox.currentPage
    private val pageBox
        get() = numpadBox.currentPageBox

    val notifyButtonClicked = Callback<NumpadView, String>(this)
    val onButtonClicked = notifyButtonClicked.Listener()

    override fun createTouchAction(e: MotionEvent): TouchAction = NumpadTouchAction()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        numpadBox.size = SizeF(w.toFloat(), h.toFloat())
    }

    private inner class NumpadTouchAction : TouchAction(longPressTimeout = 200L) {
        private var downButtonGroup: NumpadButtonGroup? = null
        private val downButton
            get() = downButtonGroup?.let { bg -> if (pageBox.isShift) bg.shift else bg.normal }

        override fun onDown() {
            val pt = pageBox.findCoords(prim.lastPosition)
            downButtonGroup = page.getButton(pt)
            pageBox.findId(prim.lastPosition)?.let { id ->
                when (id) {
                    "⇧" -> pageBox.isShift = true
                    "⇩" -> {
                        pageBox.isShiftLocked = false
                        pageBox.isShift = false
                    }
                }
            }
            pageBox.buttonPressed = pt
        }

        override fun onLongDown() {
            downButton?.let { db ->
                if (db.hasAux) {
                    pageBox.buttonExpanded = db
                }
            }
        }

        override fun onMove() {
            if (pageBox.buttonExpanded == null) {
                val d = prim.totalDiff
                when {
                    d.x > SWIPE_DISTANCE -> {
                        pageBox.buttonPressed = null
                        numpadBox.swipe(Direction.Right)
                        finish()
                    }
                    d.y > SWIPE_DISTANCE -> {
                        pageBox.buttonPressed = null
                        numpadBox.swipe(Direction.Down)
                        finish()
                    }
                    d.x < -SWIPE_DISTANCE -> {
                        pageBox.buttonPressed = null
                        numpadBox.swipe(Direction.Left)
                        finish()
                    }
                    d.y < -SWIPE_DISTANCE -> {
                        pageBox.buttonPressed = null
                        numpadBox.swipe(Direction.Up)
                        finish()
                    }
                }
            } else {
                pageBox.buttonPressed = pageBox.findCoords(prim.lastPosition)
            }
        }

        override fun onUp() {
            pageBox.findId(prim.lastPosition)?.let { id ->
                when (id) {
                    "⇪" -> {
                        pageBox.isShiftLocked = true
                    }
                    "⇩" -> { }
                    else -> {
                        if (!pageBox.isShiftLocked) {
                            pageBox.isShift = false
                        }
                    }
                }
                notifyButtonClicked(id)
            }
        }

        override fun onPinchDown() {
        }

        override fun onPinchMove() {
        }

        override fun onPinchUp() {
        }

        override fun beforeFinish() {
            pageBox.buttonExpanded = null
            pageBox.buttonPressed = null
        }
    }

    companion object {
        const val SWIPE_DISTANCE = 100f
    }
}