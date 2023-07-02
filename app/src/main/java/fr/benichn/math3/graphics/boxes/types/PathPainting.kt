package fr.benichn.math3.graphics.boxes.types

import android.graphics.Color
import android.graphics.Paint

abstract class PathPainting {
    data object Fill : PathPainting() {
        override fun getPaint(color: Int): Paint {
            return Paint().also {
                it.style = Paint.Style.FILL
                it.color = color
            }
        }
    }

    data class Stroke(val width: Float) : PathPainting() {
        override fun getPaint(color: Int): Paint {
            return Paint().also {
                it.style = Paint.Style.STROKE
                it.strokeWidth = width
                it.color = color
            }
        }
    }

    data object Transparent : PathPainting() {
        override fun getPaint(color: Int): Paint {
            return Paint().also {
                it.color = Color.TRANSPARENT
            }
        }
        fun getPaint() = getPaint(0)
    }

    abstract fun getPaint(color: Int) : Paint
}