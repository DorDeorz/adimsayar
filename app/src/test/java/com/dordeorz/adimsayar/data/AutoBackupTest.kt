package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AutoBackupTest {

    private val zone = ZoneId.of("Europe/Istanbul")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun file(day: String, modified: Long = 0L, copy: String = "") = BackupFile("fitwren-otomatik-yedek-$day$copy.json", modified)

    @Test
    fun dueOnceACalendarDay() {
        assertTrue(AutoBackup.due(0L, 0L, at("2026-11-13T10:00"), zone))
        val last = at("2026-11-13T10:00")
        assertFalse(AutoBackup.due(last, last, at("2026-11-13T23:59"), zone))
        assertTrue(AutoBackup.due(last, last, at("2026-11-14T00:05"), zone))
    }

    @Test
    fun failedTryWaitsAnHour() {
        val last = at("2026-11-13T10:00")
        val tried = at("2026-11-14T00:05")
        assertFalse(AutoBackup.due(last, tried, at("2026-11-14T00:20"), zone))
        assertTrue(AutoBackup.due(last, tried, at("2026-11-14T01:05"), zone))
        // The first backup never worked: the same wait.
        assertFalse(AutoBackup.due(0L, tried, at("2026-11-14T00:20"), zone))
        assertTrue(AutoBackup.due(0L, tried, at("2026-11-14T01:05"), zone))
    }

    @Test
    fun clockSetBackDoesNotBlockTheBackup() {
        val last = at("2026-11-12T10:00")
        val tried = at("2026-11-14T00:05")
        assertTrue(AutoBackup.due(last, tried, at("2026-11-13T09:00"), zone))
        // A backup dated later than now is not written again.
        assertFalse(AutoBackup.due(at("2026-11-14T00:05"), tried, at("2026-11-13T09:00"), zone))
    }

    @Test
    fun fileNameCarriesTheDay() {
        assertEquals("fitwren-otomatik-yedek-2026-11-13.json", AutoBackup.fileName(LocalDate.of(2026, 11, 13)))
    }

    @Test
    fun keepsTheNewestDays() {
        val files = (5..13).map { file("2026-11-%02d".format(it)) }
        assertEquals(setOf(file("2026-11-05").name, file("2026-11-06").name), AutoBackup.outdated(files))
        assertEquals(emptySet<String>(), AutoBackup.outdated(files.takeLast(7)))
    }

    @Test
    fun keepsOnlyTheNewestCopyOfADay() {
        val first = file("2026-11-13", modified = 100L)
        val second = file("2026-11-13", modified = 200L, copy = " (1)")
        val yesterday = file("2026-11-12", modified = 50L)
        assertEquals(setOf(first.name), AutoBackup.outdated(listOf(first, second, yesterday)))
        // Copies of a day do not push older days out.
        val week = (6..12).map { file("2026-11-%02d".format(it)) }
        assertEquals(setOf(first.name, file("2026-11-06").name), AutoBackup.outdated(week + first + second))
    }

    @Test
    fun otherFilesAreNeverListed() {
        val files = (1..9).map { file("2026-11-%02d".format(it)) } + listOf(
            BackupFile("fitwren-yedek-2026-10-28.json", 0L),
            BackupFile("fitwren-otomatik-yedek-notlar.json", 0L),
            BackupFile("fitwren-otomatik-yedek-2026-11-01.txt", 0L),
            BackupFile("fotograf.jpg", 0L),
        )
        assertEquals(setOf(file("2026-11-01").name, file("2026-11-02").name), AutoBackup.outdated(files))
    }
}
