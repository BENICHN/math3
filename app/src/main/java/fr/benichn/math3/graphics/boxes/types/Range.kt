package fr.benichn.math3.graphics.boxes.types

import kotlin.math.max
import kotlin.math.min

data class Range(val start: Int, val end: Int) {
    companion object {
        fun sum(r1: Range, r2: Range) = Range(min(r1.start, r2.start), max(r1.end, r2.end))
    }
}