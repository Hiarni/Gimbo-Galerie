package de.luma.gallery.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ToneCurveTest {
    @Test fun neutralCurveIsExactForEveryEightBitInput() {
        assertArrayEquals(IntArray(256) { it }, ToneCurveSampler(ToneCurve()).lookup())
        assertTrue(PhotoCurves().isNeutral)
    }

    @Test fun smoothInterpolationHitsEveryKnotAndNeverOvershootsAnySegment() {
        val random = Random(42)
        repeat(100) {
            val curve = ToneCurve(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat())
            val points = curve.values(); val sample = ToneCurveSampler(curve)
            points.forEachIndexed { index, value -> assertEquals(value, sample.value(index / 4f), .00001f) }
            for (segment in 0..3) for (step in 0..100) {
                val value = sample.value((segment + step / 100f) / 4)
                assertTrue(value >= minOf(points[segment], points[segment + 1]) - .00001f)
                assertTrue(value <= maxOf(points[segment], points[segment + 1]) + .00001f)
            }
        }
    }

    @Test fun increasingDecreasingAndFlatCurvesStayMonotoneAndKeepEndpoints() {
        for (curve in listOf(ToneCurve(shadows = .1f, highlights = .9f), ToneCurve(1f, .8f, .5f, .2f, 0f),
            ToneCurve(.4f, .4f, .4f, .4f, .4f), ToneCurve(0f, 0f, .5f, 1f, 1f))) {
            val table = ToneCurveSampler(curve).lookup()
            assertEquals((curve.black * 255).toInt(), table.first())
            assertEquals((curve.white * 255).toInt(), table.last())
            for (index in 1..255) assertTrue(if (curve.black <= curve.white) table[index] >= table[index - 1] else table[index] <= table[index - 1])
        }
    }

    @Test fun invalidPointsAreRejectedAndChannelChangesAreIndependent() {
        for (index in 0..4) for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.01f, 1.01f))
            assertThrows(IllegalArgumentException::class.java) { ToneCurve().withPoint(index, value) }
        assertThrows(IllegalArgumentException::class.java) { ToneCurve().withPoint(5, .5f) }
        assertThrows(IllegalArgumentException::class.java) { ToneCurveSampler(ToneCurve()).value(Float.NaN) }
        val changed = PhotoCurves().withCurve(CurveChannel.RED, ToneCurve(midtones = .8f))
        assertEquals(.8f, changed.red.midtones, 0f)
        assertEquals(ToneCurve(), changed.green); assertEquals(ToneCurve(), changed.blue); assertEquals(ToneCurve(), changed.rgb)
    }
}
