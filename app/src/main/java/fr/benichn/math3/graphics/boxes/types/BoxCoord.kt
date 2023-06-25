package fr.benichn.math3.graphics.boxes.types

import fr.benichn.math3.types.Chain

@JvmInline
value class BoxCoord(private val indices: Chain<Int>) {
    val depth
        get() = indices.size
    operator fun iterator() = indices.iterator()

    constructor(h: Int, t: BoxCoord) : this(Chain.Node(h, t.indices))

    operator fun compareTo(bc: BoxCoord): Int =
        when {
            indices.isEmpty && bc.indices.isEmpty -> 0
            indices.isEmpty && !bc.indices.isEmpty -> -1
            !indices.isEmpty && bc.indices.isEmpty -> 1
            else -> {
                val (h1, t1) = indices.asNode()
                val (h2, t2) = bc.indices.asNode()
                if (h1 == h2) {
                    BoxCoord(t1).compareTo(BoxCoord(t2))
                } else {
                    0
                }
            }
        }

    companion object {
        val root: BoxCoord = BoxCoord(Chain.Empty)
    }
}