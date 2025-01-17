package fr.benichn.math3.types.callback

data class CallbackLink<S, T>(val listener: Callback<S, T>.Listener, val action: (S, T) -> Unit) {
    init {
        listener += action
    }
    fun disconnect() {
        listener -= action
    }
}

data class VCCLink<S, T>(val listener: VCL<S, T>, val action: (S, ValueChangedEvent<T>) -> Unit) {
    init {
        listener += action
    }
    fun disconnect() {
        listener -= action
    }
}