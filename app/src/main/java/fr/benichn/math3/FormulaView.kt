package fr.benichn.math3

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

class FormulaView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    var box = SequenceFormulaBox()
    var caret = BoxCaret(box)
    private val boxView = object : View(context) {
        private lateinit var cache: Bitmap
        private var hasPictureChanged = true
        fun pictureHasChanged() {
            hasPictureChanged = true
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (hasPictureChanged) {
                cache = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val cv = Canvas(cache)
                cv.translate(0f, height * 0.5f)
                box.drawOnCanvas(cv)
                hasPictureChanged = false
            }
            canvas.drawBitmap(cache, 0f, 0f, null)
        }
    }
    private val caretView = object : View(context) {
        private lateinit var cache: Bitmap
        private var hasPictureChanged = true
        fun pictureHasChanged() {
            hasPictureChanged = true
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (hasPictureChanged) {
                cache = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val cv = Canvas(cache)
                cv.translate(0f, height * 0.5f)
                caret.drawOnCanvas(cv)
                hasPictureChanged = false
            }
            canvas.drawBitmap(cache, 0f, 0f, null)
        }
    }
    init {
        setWillNotDraw(false)
        box.onPictureChanged += { _, _ ->
            boxView.pictureHasChanged()
            boxView.invalidate() }
        caret.onPictureChanged += { _, _ ->
            caretView.pictureHasChanged()
            caretView.invalidate() }
        addView(boxView)
        addView(caretView)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) {
            val b = box.findBox(e.x, e.y - height*0.5f)
            // b?.box?.alert()
            // Log.d("clic", "${e.x}, ${e.y - height*0.5f}, $b")
            // Log.d("coord", "${b?.box?.getCoord()} ~ ${b?.toSeqCoord()}")
            caret.position = b?.toSeqCoord()
        }
        return super.onTouchEvent(e)
    }

    companion object {
        val red = Paint().also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = 1f
            it.color = Color.RED }
        val cyan = Paint().also {
            it.style = Paint.Style.FILL
            it.color = Color.CYAN }
    }
}