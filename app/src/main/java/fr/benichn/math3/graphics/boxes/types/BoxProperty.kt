package fr.benichn.math3.graphics.boxes.types

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import fr.benichn.math3.graphics.boxes.FormulaBox
import fr.benichn.math3.types.callback.*

class BoxProperty<S: FormulaBox, T>(private val source: S, val defaultValue: T, val updatesGraphics: Boolean = true) :
    ReadWriteProperty<S, T> {
    private var field = defaultValue
    private val notifyChanged = VCC<S, T>(source)
    val onChanged = notifyChanged.Listener()
    fun get() = field
    fun set(value: T) {
        val old = field
        field = value
        isSet = true
        if (old != value) {
            notifyChanged(old, value)
            if (updatesGraphics) {
                source.updateGraphics()
            }
        }
    }
    fun reset() {
        set(defaultValue)
        isSet = false
    }
    private val connections = mutableListOf<CallbackLink<*, *>>()
    fun <A : FormulaBox> connectTo(property: BoxProperty<A, T>) =
        connectValue(property.onChanged, property.get()) { t -> t }
    fun <A> connectValue(listener: VCL<A, T>) =
        connectValue(listener) { _, x -> x }
    fun <A, B> connectValue(listener: VCL<A, B>, mapper: (B) -> T) =
        connectValue(listener) { _, x -> mapper(x) }
    fun <A, B> connectValue(listener: VCL<A, B>, mapper: (A, B) -> T) {
        connections.add(CallbackLink(listener) { s, e ->
            set(mapper(s, e.new))
        })
    }
    fun <A, B> connectValue(listener: VCL<A, B>, currentValue: B, mapper: (B) -> T) =
        connectValue(listener, currentValue) { _, x -> mapper(x) }
    fun <A, B> connectValue(listener: VCL<A, B>, currentValue: B, mapper: (A, B) -> T) {
        connectValue(listener, mapper)
        set(mapper(listener.source, currentValue))
    }
    fun <A, B> connect(listener: Callback<A, B>.Listener, mapper: (B) -> T) =
        connect(listener) { _, x -> mapper(x) }
    fun <A, B> connect(listener: Callback<A, B>.Listener, mapper: (A, B) -> T) {
        connections.add(CallbackLink(listener) { s, e ->
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
    var isSet = false
        private set
    override fun getValue(thisRef: S, property: KProperty<*>): T = get()
    override fun setValue(thisRef: S, property: KProperty<*>, value: T) {
        if (!isConnected) {
            set(value)
        }
    }
}