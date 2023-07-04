package fr.benichn.math3.graphics.boxes.types

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.types.callback.*

class BoxProperty<S: FormulaBox, T>(private val source: S, private val defaultValue: T, val updatesGraphics: Boolean = true) :
    ReadWriteProperty<S, T> {
    private var field = defaultValue
    private val notifyChanged = VCC<S, T>(source)
    val onChanged = notifyChanged.Listener()
    fun get() = field
    fun set(value: T) {
        val old = field
        field = value
        notifyChanged(old, value)
        if (updatesGraphics) {
            source.updateGraphics()
        }
    }
    private val connections = mutableListOf<VCCLink<*, *>>()
    fun <A, B> connectValue(listener: VCL<A, B>, mapper: (B) -> T) =
        connectValue(listener) { _, x -> mapper(x) }
    fun <A, B> connectValue(listener: VCL<A, B>, mapper: (A, B) -> T) {
        connections.add(VCCLink(listener) { s, e ->
            set(mapper(s, e.new))
        })
    }
    fun <A, B> connectValue(listener: VCL<A, B>, currentValue: B, mapper: (B) -> T) =
        connectValue(listener, currentValue) { _, x -> mapper(x) }
    fun <A, B> connectValue(listener: VCL<A, B>, currentValue: B, mapper: (A, B) -> T) {
        connectValue(listener, mapper)
        set(mapper(listener.source, currentValue))
    }
    fun <A, B> connect(listener: VCL<A, B>, mapper: (ValueChangedEvent<B>) -> T) =
        connect(listener) { _, x -> mapper(x) }
    fun <A, B> connect(listener: VCL<A, B>, mapper: (A, ValueChangedEvent<B>) -> T) {
        connections.add(VCCLink(listener) { s, e ->
            set(mapper(s, e))
        })
    }
    fun <A, B> disconnect(listener: VCL<A, B>) {
        connections.removeIf {
            if (it.listener == listener) {
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
        get() = connections.isNotEmpty()
    override fun getValue(thisRef: S, property: KProperty<*>): T = get()
    override fun setValue(thisRef: S, property: KProperty<*>, value: T) {
        if (!isConnected) {
            set(value)
        }
    }
}