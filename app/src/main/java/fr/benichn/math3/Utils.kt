package fr.benichn.math3

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import androidx.core.animation.doOnEnd
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class ValueChangedEvent<T>(val old: T, val new: T)

class Callback<S, T>(val source: S) {
    private val listeners: MutableList<(S, T) -> Unit> = mutableListOf()
    operator fun invoke(e: T) {
        for (l in listeners) {
            l(source, e)
        }
    }
    operator fun plusAssign(l: (S, T) -> Unit) {
        listeners.add(l)
    }
    operator fun minusAssign(l: (S, T) -> Unit) {
        listeners.remove(l)
    }

    // fun clear() {
    //     listeners.clear()
    // }
}

typealias VCC<S, T> = Callback<S, ValueChangedEvent<T>>
operator fun <S, T> VCC<S, T>.invoke(old: T, new: T) = invoke(ValueChangedEvent(old, new))

open class SwipeTouchListener : OnTouchListener {
    private var x0: Float = -1f
    private var y0: Float = -1f
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (x0 == -1f) {
                    x0 = e.x
                    y0 = e.y
                }
            }
            MotionEvent.ACTION_UP -> {
                 x0 = -1f
                 y0 = -1f
            }
            MotionEvent.ACTION_MOVE -> {
                if (x0 != -1f) {
                    val dx = e.x - x0
                    val dy = e.y - y0
                    if (abs(dx) > abs(dy)) {
                        if (dx > SWIPE_DISTANCE) {
                            onSwipeRight()
                            x0 = -1f
                            y0 = -1f
                        }
                        if (dx < -SWIPE_DISTANCE) {
                            onSwipeLeft()
                            x0 = -1f
                            y0 = -1f
                        }
                    } else {
                        if (dy > SWIPE_DISTANCE) {
                            onSwipeBottom()
                            x0 = -1f
                            y0 = -1f
                        }
                        if (dy < -SWIPE_DISTANCE) {
                            onSwipeTop()
                            x0 = -1f
                            y0 = -1f
                        }
                    }
                }
            }
            else -> {
            }
        }
        return false
    }

    open fun onSwipeRight() {}
    open fun onSwipeLeft() {}
    open fun onSwipeTop() {}
    open fun onSwipeBottom() {}

    companion object {
        const val SWIPE_DISTANCE = 100f
    }
}

class Utils {
    companion object {
        fun setViewPosition(v: View, x: Int, y: Int) {
            val lp = v.layoutParams
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.leftMargin = x
                lp.topMargin = y
                lp.rightMargin = -x
                lp.bottomMargin = -y
            }
            v.layoutParams = lp
        }

        fun animatePos(deltaX: Int, deltaY: Int, duration: Long, vararg views: View, onEnd: () -> Unit = {}) {
            val lps = views.map { v -> v.layoutParams as ViewGroup.MarginLayoutParams }
            val x0s = lps.map { lp -> lp.leftMargin }
            val y0s = lps.map { lp -> lp.topMargin }
            val va = ValueAnimator.ofFloat(0f, 1f)
            va.addUpdateListener {
                val t = easeOutExpo(it.animatedValue as Float)
                val dx = (deltaX * t).toInt()
                val dy = (deltaY * t).toInt()
                val xs = x0s.map { x0 -> x0 + dx }
                val ys = y0s.map { y0 -> y0 + dy }
                for (i in views.indices) {
                    setViewPosition(views[i], xs[i], ys[i])
                }
            }
            va.doOnEnd {
                onEnd()
            }
            va.duration = duration
            va.start()
        }

        fun easeInv(b: Float, t: Float): Float {
            return 1 - 1 / (1 + (t / (1 - t)).pow(b))
        }
        fun easeOutPoly(b: Float, t: Float): Float {
            return 1 - (1 - t).pow(b)
        }
        fun easeOutExpo(t: Float): Float {
            return if (t == 1f) 1f else 1f - 2f.pow(-10f * t)
        }
        fun easeOutCirc(b: Float, t: Float): Float {
            return 1 - (1 - sqrt(1 - (t - 1).pow(2))).pow(b)
        }

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

        fun sumOfRects(rects: Iterable<RectF>): RectF = rects.fold(RectF()) { acc, r ->
            RectF(min(acc.left, r.left), min(acc.top, r.top), max(acc.right, r.right), max(acc.bottom, r.bottom))
        }

        operator fun PointF.times(scale: Float): PointF = PointF(x*scale,y*scale)
        operator fun PointF.div(scale: Float): PointF = PointF(x/scale,y/scale)
    }
}

data class MeasuredPath(
    val path: Path,
    val w: Float,
    val h: Float,
)