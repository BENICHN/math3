package fr.benichn.math3.types

class ImmutableList<T>(private val inner:List<T>) : List<T> by inner