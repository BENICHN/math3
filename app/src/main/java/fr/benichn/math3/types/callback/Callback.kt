package fr.benichn.math3.types.callback

class Callback<S, T>(val source: S) {
    inner class Listener {
        val source
            get() = this@Callback.source
        fun add(l: (S, T) -> Any) {
            action.add(l)
        }
        fun remove(l: (S, T) -> Any) {
            action.remove(l)
        }
        operator fun plusAssign(l: (S, T) -> Unit) {
            action.add(l)
        }
        operator fun minusAssign(l: (S, T) -> Unit) {
            action.remove(l)
        }
    }
    private val action: MutableList<(S, T) -> Any> = mutableListOf()
    operator fun invoke(e: T): Boolean {
        for (l in action.toList()) {
            val r = l(source, e)
            if (r == true) {
                action.remove(l)
                return true
            }
        }
        return false
    }
}

operator fun <S> Callback<S, Unit>.invoke() = this.invoke(Unit)