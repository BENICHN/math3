package fr.benichn.math3.graphics.boxes.types

import fr.benichn.math3.numpad.types.Pt
import kotlin.math.max
import kotlin.math.min

data class PtsRange(val tl: Pt, val br: Pt): Iterable<Pt> {
    val bl
        get() = Pt(tl.x, br.y)
    val tr
        get() = Pt(br.x, tl.y)
    operator fun rangeTo(pt: Pt) = PtsRange(
        Pt(if (tl.x == -1) pt.x else min(pt.x, tl.x), if (tl.y == -1) pt.y else min(pt.y, tl.y)),
        Pt(if (br.x == -1) pt.x else max(pt.x, br.x), if (br.y == -1) pt.y else max(pt.y, br.y))
    )
    operator fun contains(pt: Pt) =
        pt.x in tl.x .. br.x && pt.y in tl.y .. br.y
    override fun iterator(): Iterator<Pt> = (tl.y ..  br.y).flatMap { j ->
        (tl.x .. br.x).map { i ->
            Pt(i, j)
        }
    }.iterator()

    companion object {
        val nan = PtsRange(Pt(-1, -1), Pt(-1, -1))
        fun fromPts(pts: Iterable<Pt>) = pts.fold(nan) { acc, pt -> acc .. pt }
    }
}