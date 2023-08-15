package fr.benichn.math3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.zeromq.ZMQ
import java.math.BigInteger
import kotlin.math.max
import kotlin.math.min

class Utils {
    companion object {
        fun pos(x: Float) = max(0f, x)
        fun pos(x: Int) = max(0, x)
        fun neg(x: Float) = pos(-x)
        fun neg(x: Int) = pos(-x)
        fun clamp(x: Float, mn: Float, mx: Float) = min(mx, max(mn, x))
        fun clamp(x: Int, mn: Int, mx: Int) = min(mx, max(mn, x))
        infix fun Int.pow(x: Int): Int = when {
            x == 0 -> 1
            x == 1 -> this
            x % 2 == 1 -> pow(x-1) * this
            else -> {
                val p = pow(x/2)
                p * p
            }
        }

        private val dpScale: Float = App.instance.applicationContext.resources.displayMetrics.density
        fun Int.dp() = (this * dpScale + 0.5f).toInt()
        fun Float.dp() = (this * dpScale + 0.5f).toInt()

        fun <T> List<T>.intercalateIndexed(f: (Int) -> T) = flatMapIndexed { i, e ->
            if (i == size-1) listOf(e)
            else listOf(e, f(i))
        }

        fun <T> List<T>.intercalateIndexed(f: (Int, T) -> T) = flatMapIndexed { i, e ->
            if (i == size-1) listOf(e)
            else listOf(e, f(i, e))
        }

        fun <T> List<T>.intercalate(f: (T) -> T) = flatMapIndexed { i, e ->
            if (i == size-1) listOf(e)
            else listOf(e, f(e))
        }

        fun <T> List<T>.intercalate(element: T) = flatMapIndexed { i, e ->
            if (i == size-1) listOf(e)
            else listOf(e, element)
        }
    }
}