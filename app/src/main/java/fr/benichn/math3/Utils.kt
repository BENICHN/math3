package fr.benichn.math3

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
    }
}