package fr.benichn.math3.types.callback

data class ValueChangedEvent<out T>(val old: T, val new: T) {
    val changed
        get() = old != new
}