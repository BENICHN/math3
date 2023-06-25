package fr.benichn.math3.types

sealed class Chain<out T> : Iterable<T> {
    data object Empty : Chain<Nothing>() {
        override val isEmpty: Boolean
            get() = true
        override fun conputeSize(): Int = 0
    }
    data class Node<out T>(val head: T, val tail: Chain<T>) : Chain<T>() {
        override val isEmpty: Boolean
            get() = false
        override fun conputeSize(): Int = 1 + tail.size
    }

    abstract val isEmpty: Boolean

    val size by lazy { conputeSize() }
    protected abstract fun conputeSize(): Int

    fun asNode(): Node<T> = this as Node<T>

    fun toList(): List<T> {
        val a = ArrayList<T>(size)
        for ((i, e) in this.withIndex()) {
            a[i] = e
        }
        return a
    }

    override fun iterator() = object : Iterator<T> {
        private var next: Chain<T> = this@Chain
        override fun hasNext(): Boolean = !next.isEmpty
        override fun next(): T {
            if (hasNext()) {
                val (h, t) = next as Node<T>
                next = t
                return h
            } else {
                throw NoSuchElementException("No additional element available")
            }
        }

    }

    companion object {
        fun <T> fromList(l: List<T>): Chain<T> {
            var current: Chain<T> = Empty
            for (i in l.size - 1 downTo 0) {
                current = Node(l[i], current)
            }
            return current
        }
    }
}