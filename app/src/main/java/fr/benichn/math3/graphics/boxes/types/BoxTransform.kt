package fr.benichn.math3.graphics.boxes.types

import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.plus
import androidx.core.graphics.times
import androidx.core.graphics.unaryMinus
import fr.benichn.math3.graphics.Utils.Companion.div
import fr.benichn.math3.graphics.Utils.Companion.times

data class BoxTransform(val origin: PointF = PointF(), val scale: Float = 1f) {
    operator fun times(bt: BoxTransform) =
        BoxTransform(origin * bt.scale + bt.origin, scale * bt.scale) // [f*g](x) = g(f(x))
    val invert
        get() = BoxTransform(-origin / scale, 1 / scale)
    fun applyOnCanvas(canvas: Canvas) {
        canvas.translate(origin.x, origin.y)
        canvas.scale(scale, scale)
    }
    fun applyOnRect(r: RectF): RectF = r * scale + origin
    companion object {
        fun xOffset(l: Float): BoxTransform = BoxTransform(PointF(l, 0f))
        fun yOffset(l: Float): BoxTransform = BoxTransform(PointF(0f, l))
        fun scale(a: Float): BoxTransform = BoxTransform(PointF(), a)
    }
}