package fr.benichn.math3.numpad

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import fr.benichn.math3.graphics.FormulaViewer
import fr.benichn.math3.graphics.boxes.types.BoundsTransformer
import fr.benichn.math3.graphics.types.RectPoint
import fr.benichn.math3.graphics.types.TouchAction
import fr.benichn.math3.types.callback.Callback
import kotlin.math.ceil

class NamesBarView(context: Context, attrs: AttributeSet? = null) : FormulaViewer(context, attrs) {
    init {
        child = NamesBar()
        fitToBox = true
    }

    override val initialBoxTransformers: Array<BoundsTransformer>
        get() = arrayOf(BoundsTransformer.Align(RectPoint.TOP_LEFT))

    val barBox = child as NamesBar

    val notifyButtonClicked = Callback<NamesBarView, String>(this)
    val onButtonClicked = notifyButtonClicked.Listener()

    override fun createTouchAction(e: MotionEvent): TouchAction = NamesBarTouchAction()

    private inner class NamesBarTouchAction : TouchAction() {
        private var downButton : NameButton? = null

        override fun onDown() {
            downButton = box.findBox(prim.lastPosition) as? NameButton
        }

        override fun onLongDown() {

        }

        override fun onMove() {

        }

        override fun onUp() {
            val b = box.findBox(prim.lastPosition)
            downButton?.let { db ->
                if (b == db) {
                    Log.d("btn", db.textBox.text)
                    notifyButtonClicked(db.textBox.text)
                }
            }
        }

        override fun onPinchDown() {

        }

        override fun onPinchMove() {

        }

        override fun onPinchUp() {

        }

        override fun beforeFinish() {

        }

    }

    // override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    //     box.realBounds.run {
    //         setMeasuredDimension(
    //             widthMeasureSpec,
    //             ceil(height()).toInt()
    //         )
    //     }
    // }
}