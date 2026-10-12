package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RoutesTest {

    private val start = LocalDate.of(2026, 10, 1)
    private val route = Routes.byId("istanbul_ankara")!!

    @Test
    fun stopsAreOrderedAndIdsUnique() {
        assertEquals(Routes.all.size, Routes.all.map { it.id }.toSet().size)
        Routes.all.forEach { r ->
            assertEquals(0, r.stops.first().km)
            assertTrue(r.stops.zipWithNext().all { (a, b) -> b.km > a.km })
        }
    }

    @Test
    fun progressCountsOnlyDaysFromStart() {
        val history = mapOf(start.minusDays(1) to 100_000L, start to 10_000L, start.plusDays(1) to 10_000L)
        val progress = Routes.progress(route, history, start, start.plusDays(1), 170)
        assertEquals(Distance.km(20_000L, 170), progress.km, 1e-9)
        assertNull(progress.finishedOn)
        assertEquals(0, progress.reached)
        assertEquals("İzmit", progress.next?.name)
    }

    @Test
    fun finishDateIsFirstDayPastTotal() {
        val history = (0L..9L).associate { start.plusDays(it) to 100_000L }
        val progress = Routes.progress(route, history, start, start.plusDays(9), 170)
        assertEquals(start.plusDays(6), progress.finishedOn)
        assertEquals(1f, progress.fraction)
        assertNull(progress.next)
    }

    @Test
    fun positionInterpolatesBetweenStops() {
        val (lat, lon) = Routes.position(route, 55.5)
        assertEquals((41.01 + 40.77) / 2, lat, 1e-9)
        assertEquals((28.98 + 29.92) / 2, lon, 1e-9)
    }
}
