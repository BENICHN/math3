package fr.benichn.math3.types.callback

class Callback<S, T>(val source: S) {
    inner class Listener {
        val source
            get() = this@Callback.source
        operator fun plusAssign(l: (S, T) -> Unit) {
            action.add(l)
        }
        operator fun minusAssign(l: (S, T) -> Unit) {
            action.remove(l)
        }
    }
    private val action: MutableList<(S, T) -> Unit> = mutableListOf()
    operator fun invoke(e: T) {
        for (l in action) {
            l(source, e)
        }
    }
    // operator fun plusAssign(l: (S, T) -> Unit) {
    //     listeners.add(l)
    // }
    // operator fun minusAssign(l: (S, T) -> Unit) {
    //     listeners.remove(l)
    // }
}

operator fun <S> Callback<S, Unit>.invoke() = this.invoke(Unit)