package fr.benichn.math3.types.callback

typealias VCC<S, T> = Callback<S, ValueChangedEvent<T>>
typealias VCL<S, T> = Callback<S, ValueChangedEvent<T>>.Listener
operator fun <S, T> VCC<S, T>.invoke(old: T, new: T) = invoke(ValueChangedEvent(old, new))