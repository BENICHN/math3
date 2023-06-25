package fr.benichn.math3.graphics.boxes.types

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.types.callback.*

class BoxProperty<S: FormulaBox, T>(private val source: S, private val defaultValue: T, val updatesGraphics: Boolean = true) :
    ReadWriteProperty<S, T> {
    private var field = defaultValue
    val onChanged = VCC<S, T>(source)
    fun get() = field
    fun set(value: T) {
        val old = field
        field = value
        onChanged(old, value)
        if (updatesGraphics) {
            source.updateGraphics()
        }
    }
    private val connections = mutableListOf<CallbackLink<*, *>>()
    fun <A, B> connectValue(callback: VCC<A, B>, mapper: (A, B) -> T) {
        connections.add(CallbackLink(callback) { s, e ->
            set(mapper(s, e.new))
        })
    }
    fun <A, B> connectValue(callback: VCC<A, B>, currentValue: B, mapper: (A, B) -> T) {
        connectValue(callback, mapper)
        set(mapper(callback.source, currentValue))
    }
    fun <A, B> connect(callback: VCC<A, B>, mapper: (A, ValueChangedEvent<B>) -> T) {
        connections.add(CallbackLink(callback) { s, e ->
            set(mapper(s, e))
        })
    }
    fun <A, B> disconnect(callback: VCC<A, B>) {
        connections.removeIf {
            if (it.callback == callback) {
                it.disconnect()
                true
            } else {
                false
            }
        }
        if (!isConnected) {
            set(defaultValue)
        }
    }
    val isConnected
        get() = connections.isEmpty()
    override fun getValue(thisRef: S, property: KProperty<*>): T = get()
    override fun setValue(thisRef: S, property: KProperty<*>, value: T) {
        if (!isConnected) {
            set(value)
        }
    }
}