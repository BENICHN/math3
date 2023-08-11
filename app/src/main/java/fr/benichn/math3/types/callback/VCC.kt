package fr.benichn.math3.types.callback

typealias VCC<S, T> = Callback<S, ValueChangedEvent<T>>
typealias VCL<S, T> = Callback<S, ValueChangedEvent<T>>.Listener
operator fun <S, T> VCC<S, T>.invoke(old: T, new: T) = if (old != new) invoke(ValueChangedEvent(old, new)) else false