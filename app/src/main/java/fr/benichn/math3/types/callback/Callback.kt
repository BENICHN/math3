package fr.benichn.math3.types.callback

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
}