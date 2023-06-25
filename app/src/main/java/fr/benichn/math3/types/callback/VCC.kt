package fr.benichn.math3.types.callback

typealias VCC<S, T> = Callback<S, ValueChangedEvent<T>>
operator fun <S, T> VCC<S, T>.invoke(old: T, new: T) = invoke(ValueChangedEvent(old, new))