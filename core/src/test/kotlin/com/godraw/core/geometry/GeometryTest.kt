package com.godraw.core.geometry

import com.godraw.core.model.SymmetryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class GeometryTest {

    @Test
    fun dabsAreEvenlySpaced() {
        val spacer = DabSpacer(0.25f) { 20f } // 5 px spacing
        val dabs = spacer.add(StrokePoint(0f, 0f)) + spacer.add(StrokePoint(100f, 0f))
        assertEquals(21, dabs.size)
        for (i in 1 until dabs.size) assertEquals(5f, dabs[i].x - dabs[i - 1].x, 1e-3f)
        // Spacing carries over between segments.
        val more = spacer.add(StrokePoint(102f, 0f))
        assertTrue(more.isEmpty())
        assertEquals(1, spacer.add(StrokePoint(105f, 0f)).size)
    }

    @Test
    fun smootherReducesJitter() {
        val raw = (0 until 100).map { StrokePoint(it * 2f, if (it % 2 == 0) 3f else -3f) }
        val smoother = StrokeSmoother(0.8f)
        val out = raw.mapNotNull { smoother.add(it) } + smoother.finish()
        val rawJitter = raw.zipWithNext { a, b -> kotlin.math.abs(a.y - b.y) }.average()
        val smoothJitter = out.zipWithNext { a, b -> kotlin.math.abs(a.y - b.y) }.average()
        assertTrue("smoothed $smoothJitter vs raw $rawJitter", smoothJitter < rawJitter / 3)
        // The stroke still ends at the pen.
        assertEquals(raw.last().x, out.last().x, 1e-3f)
    }

    @Test
    fun zeroSmoothingPassesPointsThrough() {
        val s = StrokeSmoother(0f)
        val p = StrokePoint(3f, 4f, 0.5f)
        s.add(StrokePoint(0f, 0f))
        assertEquals(p, s.add(p))
    }

    @Test
    fun symmetryMirrorsAroundCenter() {
        val v = Symmetry(SymmetryMode.VERTICAL, 50f, 50f)
        assertEquals(2, v.copies)
        assertEquals(80f to 10f, v.map(1, 20f, 10f))
        val q = Symmetry(SymmetryMode.QUADRANT, 50f, 50f)
        assertEquals(80f to 90f, q.map(3, 20f, 10f))
        val r = Symmetry(SymmetryMode.RADIAL, 0f, 0f, 4)
        val (x, y) = r.map(1, 10f, 0f)
        assertEquals(0f, x, 1e-4f)
        assertEquals(10f, y, 1e-4f)
    }

    @Test
    fun rulerProjectionAndPerspectiveSnapping() {
        val (px, py) = Guides.projectOntoLine(5f, 5f, 0f, 0f, 10f, 0f)
        assertEquals(5f, px, 1e-4f)
        assertEquals(0f, py, 1e-4f)

        // Stroke heading roughly toward the vanishing point at (100, 0) snaps onto that line.
        val (sx, sy) = Guides.snapToPerspective(0f, 50f, 40f, 32f, listOf(100f to 0f), includeAxes = false)
        val toVp = hypot(100f - 0f, 0f - 50f)
        val cross = ((sx - 0f) * (0f - 50f) - (sy - 50f) * (100f - 0f)) / toVp
        assertEquals(0f, cross, 1e-2f)
    }
}
