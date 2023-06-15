package fr.benichn.math3

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.widget.RelativeLayout
import androidx.core.graphics.PathParser
import dev.romainguy.graphics.path.toSvg

class FormulaView(context: Context, attrs: AttributeSet? = null) : RelativeLayout(context, attrs) {
    var box = SequenceFormulaBox().also {
        it.onGraphicsChanged += { s, e ->
            invalidate()
        }
    }
    init {
        setWillNotDraw(false)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.translate(0f, height*0.5f)
        box.drawOnCanvas(canvas)
    }

    companion object {
        val red = Paint().also {
            it.style = Paint.Style.STROKE
            it.strokeWidth = 3f
            it.color = Color.RED }
        val cyan = Paint().also {
            it.style = Paint.Style.FILL
            it.color = Color.CYAN }
    }
}