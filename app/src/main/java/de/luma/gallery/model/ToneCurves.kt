package de.luma.gallery.model

import de.luma.gallery.localization.*
import de.luma.gallery.R
import kotlin.math.roundToInt


data class ToneCurve(val black: Float = 0f, val shadows: Float = .25f, val midtones: Float = .5f,
    val highlights: Float = .75f, val white: Float = 1f) {
    init { requireMessage(values().all { it.isFinite() && it in 0f..1f }, R.string.invalid_curve) }
    fun values() = listOf(black, shadows, midtones, highlights, white)
    fun withPoint(index: Int, value: Float): ToneCurve = when (index) {
        0 -> copy(black = value); 1 -> copy(shadows = value); 2 -> copy(midtones = value)
        3 -> copy(highlights = value); 4 -> copy(white = value)
        else -> throw MessageArgumentException(R.string.invalid_curve_point)
    }
}

enum class CurveChannel(val labelRes: Int) { RGB(R.string.rgb), RED(R.string.red), GREEN(R.string.green), BLUE(R.string.blue) }

data class PhotoCurves(val rgb: ToneCurve = ToneCurve(), val red: ToneCurve = ToneCurve(),
    val green: ToneCurve = ToneCurve(), val blue: ToneCurve = ToneCurve()) {
    fun curve(channel: CurveChannel) = when (channel) {
        CurveChannel.RGB -> rgb; CurveChannel.RED -> red; CurveChannel.GREEN -> green; CurveChannel.BLUE -> blue
    }
    fun withCurve(channel: CurveChannel, value: ToneCurve) = when (channel) {
        CurveChannel.RGB -> copy(rgb = value); CurveChannel.RED -> copy(red = value)
        CurveChannel.GREEN -> copy(green = value); CurveChannel.BLUE -> copy(blue = value)
    }
    val isNeutral get() = this == PhotoCurves()
}


class ToneCurveSampler(curve: ToneCurve) {
    private val values = curve.values().map { it.toDouble() }.toDoubleArray()
    private val slopes = DoubleArray(5)
    init {
        val delta = DoubleArray(4) { (values[it + 1] - values[it]) * 4 }
        fun edge(first: Double, second: Double): Double {
            val slope = (3 * first - second) / 2
            return when {
                slope * first <= 0 -> 0.0
                first * second < 0 && kotlin.math.abs(slope) > 3 * kotlin.math.abs(first) -> 3 * first
                else -> slope
            }
        }
        slopes[0] = edge(delta[0], delta[1]); slopes[4] = edge(delta[3], delta[2])
        for (i in 1..3) slopes[i] = if (delta[i - 1] * delta[i] <= 0) 0.0
            else 2 * delta[i - 1] * delta[i] / (delta[i - 1] + delta[i])
    }

    fun value(input: Float): Float {
        require(input.isFinite() && input in 0f..1f)
        val index = minOf((input * 4).toInt(), 3)
        val t = input.toDouble() * 4 - index
        val square = t * t; val cube = square * t
        return ((2 * cube - 3 * square + 1) * values[index] + (cube - 2 * square + t) * .25 * slopes[index] +
            (-2 * cube + 3 * square) * values[index + 1] + (cube - square) * .25 * slopes[index + 1])
            .coerceIn(0.0, 1.0).toFloat()
    }
    fun lookup() = IntArray(256) { (value(it / 255f) * 255).roundToInt().coerceIn(0, 255) }
}
