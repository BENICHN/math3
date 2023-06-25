package fr.benichn.math3.types.callback

data class ValueChangedEvent<T>(val old: T, val new: T)