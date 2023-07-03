package fr.benichn.math3.graphics

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import fr.benichn.math3.App
import fr.benichn.math3.R
import fr.benichn.math3.graphics.types.*
import kotlin.math.max
import kotlin.math.min

class Utils {
    companion object {
        fun getTextPathAndSize(textSize: Float, text: String): MeasuredPath {
            val res = Path()
            val paint = Paint()
            paint.typeface = App.instance.resources.getFont(R.font.source_code_pro_light)
            paint.textSize = textSize
            paint.getTextPath(text, 0, text.length, 0f, 0f-(paint.fontMetrics.ascent+paint.fontMetrics.bottom)/2, res)
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

        operator fun PointF.times(scale: Float): PointF = PointF(x*scale,y*scale)
        operator fun PointF.div(scale: Float): PointF = PointF(x/scale,y/scale)

        fun squareDistFromLineToPoint(lineX: Float, lineYStart: Float, lineYEnd: Float, x: Float, y: Float): Float {
            val dx = x - lineX
            val dy = max(0f, y - lineYEnd) + max(0f, lineYStart - y)
            return dx * dx + dy * dy
        }

        fun l2(p: PointF) = p.x * p.x + p.y * p.y
    }
}