package fr.benichn.math3.graphics.types

import android.graphics.Path

data class MeasuredPath(
    val path: Path,
    val w: Float,
    val h: Float,
)