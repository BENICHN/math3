package fr.benichn.math3.graphics

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.App
import fr.benichn.math3.R
import fr.benichn.math3.Utils.Companion.pos
import fr.benichn.math3.graphics.types.*
import kotlin.math.max
import kotlin.math.min

class Utils {
    companion object {
        fun getTextPathAndSize(textSize: Float, text: String): MeasuredPath {
            val res = Path()
            val paint = Paint()
            paint.typeface = App.instance.resources.getFont(R.font.iosevka_fixed_extended_extralight)
            paint.textSize = textSize
            paint.getTextPath(text, 0, text.length, 0f, 0f-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2, res)
            return MeasuredPath(res, paint.measureText(text), textSize)
        }
        fun getPathBounds(path: Path): RectF {
            val res = RectF()
            path.computeBounds(res, false)
            return res
        }

        fun sumOfRects(vararg rects: RectF): RectF = sumOfRects(rects.asIterable())

        fun sumOfRects(rects: Iterable<RectF>): RectF = rects.fold(RectF(Float.NaN, Float.NaN, Float.NaN, Float.NaN)) { acc, r ->
            RectF(
                if (acc.left.isNaN()) r.left else min(acc.left, r.left),
                if (acc.top.isNaN()) r.top else min(acc.top, r.top),
                if (acc.right.isNaN()) r.right else max(acc.right, r.right),
                if (acc.bottom.isNaN()) r.bottom else max(acc.bottom, r.bottom))
        }

        fun corners(rect: RectF) = listOf(
            PointF(rect.left, rect.top),
            PointF(rect.right, rect.top),
            PointF(rect.right, rect.bottom),
            PointF(rect.left, rect.bottom)
        )

        operator fun PointF.times(scale: Float): PointF = PointF(x*scale,y*scale)
        operator fun PointF.div(scale: Float): PointF = PointF(x/scale,y/scale)

        fun squareDistFromLineToPoint(lineX: Float, lineYStart: Float, lineYEnd: Float, x: Float, y: Float): Float {
            val dx = x - lineX
            val dy = pos(y - lineYEnd) + pos(lineYStart - y)
            return dx * dx + dy * dy
        }

        fun l2(p: PointF) = p.x * p.x + p.y * p.y

        fun <T> List<T>.moveToEnd(index: Int): List<T> = indices.map { i ->
            if (i < index) this[i]
            else if (i == size-1) this[index]
            else this[i+1]
        }

        fun <T> List<T>.with(index: Int, element: T): List<T> = (0 .. max(index, size-1)).map { i ->
            if (i == index) element
            else this[i]
        }

        fun <T> List<T>.prepend(element: T): List<T> = (0 .. size).map { i ->
            if (i == 0) element
            else this[i-1]
        }

        fun <T> List<T>.append(element: T): List<T> = (0 .. size).map { i ->
            if (i == size) element
            else this[i]
        }
    }
}