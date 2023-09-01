package fr.benichn.math3.numpad.types

import com.google.gson.JsonPrimitive
import fr.benichn.math3.Utils.toJsonArray
import kotlin.math.abs

data class Pt(val x: Int, val y: Int) {
    val sum: Int
        get() = x+y
    val l1: Int
        get() = abs(x) + abs(y)
    val l2: Int
        get() = x*x+ y*y
    operator fun plus(p: Pt): Pt {
        return Pt(x + p.x, y + p.y)
    }
    operator fun minus(p: Pt): Pt {
        return Pt(x - p.x, y - p.y)
    }
    operator fun times(p: Pt): Pt {
        return Pt(x * p.x, y * p.y)
    }
    operator fun times(f: Int): Pt {
        return Pt(x * f, y * f)
    }
    fun toJson() = listOf(x, y).map { JsonPrimitive(it) }.toJsonArray()
    fun and(p: Pt): Pt {
        return Pt(
            if (p.x != 0) x else 0,
            if (p.y != 0) y else 0
        )
    }
    companion object {
        val z = Pt(0, 0)
        val l = Pt(-1, 0)
        val r = Pt(1, 0)
        val t = Pt(0, -1)
        val b = Pt(0, 1)
    }
}