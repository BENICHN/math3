package fr.benichn.math3.types

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

typealias VCC<S, T> = Callback<S, ValueChangedEvent<T>>
operator fun <S, T> VCC<S, T>.invoke(old: T, new: T) = invoke(ValueChangedEvent(old, new))

data class ValueChangedEvent<T>(val old: T, val new: T)

data class CallbackLink<S, T>(val callback: VCC<S, T>, val listener: (S, ValueChangedEvent<T>) -> Unit) {
    init {
        callback += listener
    }
    fun disconnect() {
        callback -= listener
    }
}
