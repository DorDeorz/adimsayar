package com.dordeorz.adimsayar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HistoryImportTest {

    private val zone: ZoneId = ZoneId.of("Europe/Istanbul")

    private fun parse(text: String) = HistoryImport.read(ByteArrayInputStream(text.toByteArray()), zone)

    @Test
    fun readsOwnExport() {
        val result = parse("tarih,adim\n2026-10-01,5400\n2026-10-02,0\n")
        assertEquals(mapOf(LocalDate.of(2026, 10, 1) to 5_400L, LocalDate.of(2026, 10, 2) to 0L), result)
    }

    @Test
    fun readsHeaderlessLines() {
        assertEquals(mapOf(LocalDate.of(2026, 10, 1) to 12L), parse("2026-10-01;12\nbozuk\n"))
    }

    @Test
    fun readsSamsungDaySummaryAndKeepsLargestDevice() {
        val day = LocalDate.of(2026, 9, 30).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val text = "com.samsung.shealth.tracker.pedometer_day_summary,6315006,4\n" +
            "create_sh_ver,step_count,run_step_count,deviceuuid,day_time,\n" +
            "6315006,8123,10,phone,$day,\n" +
            "6315006,9000,10,watch,$day,\n"
        assertEquals(mapOf(LocalDate.of(2026, 9, 30) to 9_000L), parse(text))
    }

    @Test
    fun readsMiFitnessDailyReport() {
        val seconds = LocalDate.of(2026, 9, 29).atStartOfDay(zone).toEpochSecond()
        val text = "Uid,Sid,Tag,Key,Time,Value,UpdateTime\n" +
            "1,dev,daily_report,steps,$seconds,\"{\"\"time\"\":$seconds,\"\"steps\"\":7380,\"\"distance\"\":5100}\",0\n" +
            "1,dev,daily_report,calories,$seconds,\"{\"\"calories\"\":300}\",0\n"
        assertEquals(mapOf(LocalDate.of(2026, 9, 29) to 7_380L), parse(text))
    }

    @Test
    fun readsGoogleFitDailyMetrics() {
        val text = "Date,Move Minutes count,Calories (kcal),Step count\n2026-09-28,40,250.5,6543\n2026-09-27,10,100,\n"
        assertEquals(mapOf(LocalDate.of(2026, 9, 28) to 6_543L), parse(text))
    }

    @Test
    fun readsCsvInsideZip() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("readme.txt"))
            zip.write("hello".toByteArray())
            zip.putNextEntry(ZipEntry("ACTIVITY/ACTIVITY_1.csv"))
            zip.write("date,lastSyncTime,steps,distance\n2026-09-26,0,4321,3000\n".toByteArray())
            zip.closeEntry()
        }
        val result = HistoryImport.read(ByteArrayInputStream(bytes.toByteArray()), zone)
        assertEquals(mapOf(LocalDate.of(2026, 9, 26) to 4_321L), result)
    }

    @Test
    fun splitsQuotedFields() {
        assertEquals(listOf("a", "b,c", "d\"e"), HistoryImport.splitCsv("a,\"b,c\",\"d\"\"e\""))
    }
}
