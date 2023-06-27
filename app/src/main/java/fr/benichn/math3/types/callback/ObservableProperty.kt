package fr.benichn.math3.types.callback

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class ObservableProperty<S, T>(val source: S, private var field: T, onChanged: ((S, ValueChangedEvent<T>) -> Unit)? = null) : ReadWriteProperty<S, T> {
    private val notifyOnChanged = VCC<S, T>(source)
    val onChanged = notifyOnChanged.Listener()

    init {
        onChanged?.also { this.onChanged += it }
    }

    override fun getValue(thisRef: S, property: KProperty<*>): T {
        return field
    }

    override fun setValue(thisRef: S, property: KProperty<*>, value: T) {
        val old = field
        field = value
        notifyOnChanged(old, value)
    }
}