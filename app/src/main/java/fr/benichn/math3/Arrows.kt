package fr.benichn.math3

class Arrow<in P, out R>(private val f: (P) -> R) {
    private val listeners = mutableListOf<Arrow<R, Any>>()

    operator fun plusAssign(l: Arrow<R, Any>) {
        listeners.add(l)
    }
    operator fun minusAssign(l: Arrow<R, Any>) {
        listeners.remove(l)
    }

    operator fun invoke(p: P) {
        val res = f(p)
        for (l in listeners) {
            l(res)
        }
    }
}