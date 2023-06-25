package fr.benichn.math3.types.callback

data class CallbackLink<S, T>(val callback: VCC<S, T>, val listener: (S, ValueChangedEvent<T>) -> Unit) {
    init {
        callback += listener
    }
    fun disconnect() {
        callback -= listener
    }
}