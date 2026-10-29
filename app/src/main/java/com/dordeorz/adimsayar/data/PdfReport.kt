package com.dordeorz.adimsayar.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.ui.FULL_DATE_FORMAT
import com.dordeorz.adimsayar.ui.MONTH_FORMAT
import java.io.OutputStream
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

/** Sleep and resting heart averages for the report, when a band or Health Connect has them. */
class ReportHealth(val sleepMinutes: Long?, val nights: Int, val resting: Long?)

/** Draws [MonthReport] as a two-page A4 PDF: summary, daily chart and weekly totals, then the daily table. */
object PdfReport {

    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 48f
    private const val ROW = 21f
    private const val PRIMARY = 0xFF2E7D32.toInt()
    private const val PRIMARY_LIGHT = 0xFFA5D6A7.toInt()
    private const val MUTED = 0xFF5F6368.toInt()
    private const val LINE = 0xFFDADCE0.toInt()

    fun write(context: Context, output: OutputStream, report: MonthReport, settings: AppSettings, health: ReportHealth?) {
        val strings = Locales.wrap(context)
        val locale = Locales.current
        val numbers = NumberFormat.getIntegerInstance(locale)
        val document = PdfDocument()
        try {
            val first = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, 1).create())
            summaryPage(first.canvas, strings, locale, numbers, report, settings, health)
            document.finishPage(first)
            val second = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, 2).create())
            tablePage(second.canvas, strings, locale, numbers, report, settings)
            document.finishPage(second)
            document.writeTo(output)
        } finally {
            document.close()
            output.close()
        }
    }

    private fun paint(size: Float, color: Int = Color.BLACK, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = align
        }

    private fun monthTitle(report: MonthReport, locale: Locale): String =
        MONTH_FORMAT.format(report.month).replaceFirstChar { it.titlecase(locale) }

    private fun header(canvas: Canvas, context: Context, title: String, page: Int): Float {
        canvas.drawText(context.getString(R.string.app_name), MARGIN, MARGIN + 4f, paint(11f, PRIMARY, bold = true))
        canvas.drawText(
            context.getString(R.string.report_page, page, 2),
            WIDTH - MARGIN,
            MARGIN + 4f,
            paint(9f, MUTED, align = Paint.Align.RIGHT),
        )
        canvas.drawText(title, MARGIN, MARGIN + 34f, paint(22f, bold = true))
        canvas.drawLine(MARGIN, MARGIN + 46f, WIDTH - MARGIN, MARGIN + 46f, paint(1f, LINE))
        return MARGIN + 72f
    }

    private fun summaryPage(
        canvas: Canvas,
        context: Context,
        locale: Locale,
        numbers: NumberFormat,
        report: MonthReport,
        settings: AppSettings,
        health: ReportHealth?,
    ) {
        var y = header(canvas, context, context.getString(R.string.report_title, monthTitle(report, locale)), 1)
        val steps = { value: Long -> context.getString(R.string.steps_value, numbers.format(value)) }
        val km = Distance.inUnit(Distance.km(report.total, settings.heightCm), settings.distanceUnit)
        val unit = context.getString(if (settings.distanceUnit == DistanceUnit.Mile) R.string.unit_mile else R.string.unit_km)
        val rows = buildList {
            add(context.getString(R.string.total) to steps(report.total))
            add(context.getString(R.string.daily_average) to steps(report.average))
            add(context.getString(R.string.record_goal_days) to context.getString(R.string.report_goal_days, report.goalDays, report.days.size, numbers.format(report.goal)))
            add(context.getString(R.string.report_active_days) to report.activeDays.toString())
            report.best?.let { add(context.getString(R.string.record_best_day) to "${steps(it.steps)} · ${FULL_DATE_FORMAT.format(it.date)}") }
            add(context.getString(R.string.report_distance) to String.format(locale, "%.1f %s", km, unit))
            add(context.getString(R.string.report_kcal) to context.getString(R.string.report_kcal_value, numbers.format(Distance.kcal(report.total, settings.heightCm, settings.weightKg))))
            report.previousTotal?.let { previous ->
                val change = Math.round((report.total - previous) * 100.0 / previous)
                val sign = if (change > 0) "+" else ""
                add(context.getString(R.string.report_vs_previous) to "$sign$change% · ${steps(previous)}")
            }
            report.waterAverage?.let { add(context.getString(R.string.report_water) to context.getString(R.string.report_ml_value, numbers.format(it.toLong()))) }
            val start = report.weightStart
            val end = report.weightEnd
            if (start != null) {
                val text = if (end == null) {
                    String.format(locale, "%.1f kg", start.kg)
                } else {
                    String.format(locale, "%.1f → %.1f kg (%+.1f)", start.kg, end.kg, end.kg - start.kg)
                }
                add(context.getString(R.string.weight) to text)
            }
            health?.sleepMinutes?.let {
                add(context.getString(R.string.health_sleep_average) to context.getString(R.string.band_sleep_value, it / 60, it % 60) + " · " + context.getString(R.string.health_nights, health.nights))
            }
            health?.resting?.let { add(context.getString(R.string.health_resting_chart) to context.getString(R.string.report_bpm_value, it)) }
        }
        val label = paint(11f, MUTED)
        val value = paint(11f, bold = true, align = Paint.Align.RIGHT)
        rows.forEach { (name, text) ->
            canvas.drawText(name, MARGIN, y, label)
            canvas.drawText(text, WIDTH - MARGIN, y, value)
            canvas.drawLine(MARGIN, y + 7f, WIDTH - MARGIN, y + 7f, paint(1f, LINE))
            y += ROW
        }

        y += 22f
        canvas.drawText(context.getString(R.string.report_daily_chart), MARGIN, y, paint(13f, bold = true))
        y += 14f
        y = chart(canvas, context, locale, numbers, report, y, 170f)

        y += 28f
        canvas.drawText(context.getString(R.string.report_weeks), MARGIN, y, paint(13f, bold = true))
        y += 20f
        report.weeks.forEach { (start, total) ->
            val end = minOf(start.plusDays(6), report.days.last().date)
            canvas.drawText("${start.dayOfMonth}–${end.dayOfMonth}", MARGIN, y, label)
            canvas.drawText(steps(total), WIDTH - MARGIN, y, value)
            y += ROW - 4f
        }
    }

    private fun chart(canvas: Canvas, context: Context, locale: Locale, numbers: NumberFormat, report: MonthReport, top: Float, height: Float): Float {
        val left = MARGIN + 34f
        val right = WIDTH - MARGIN
        val bottom = top + height
        val count = report.month.lengthOfMonth()
        val max = maxOf(report.days.maxOfOrNull { it.steps } ?: 0L, report.goal).coerceAtLeast(1L).toFloat()
        val slot = (right - left) / count
        val axis = paint(8f, MUTED, align = Paint.Align.RIGHT)
        canvas.drawText(numbers.format(max.toLong()), left - 4f, top + 8f, axis)
        canvas.drawText("0", left - 4f, bottom, axis)
        canvas.drawLine(left, bottom, right, bottom, paint(1f, LINE))
        val reached = paint(1f, PRIMARY)
        val missed = paint(1f, PRIMARY_LIGHT)
        report.days.forEachIndexed { index, day ->
            if (day.steps <= 0L) return@forEachIndexed
            val x = left + index * slot
            val barTop = bottom - height * (day.steps / max)
            canvas.drawRoundRect(RectF(x + slot * 0.15f, barTop, x + slot * 0.85f, bottom), 2f, 2f, if (day.steps >= report.goal) reached else missed)
        }
        val goalY = bottom - height * (report.goal / max)
        val dash = paint(1f, Color.DKGRAY).apply {
            strokeWidth = 1f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        canvas.drawLine(left, goalY, right, goalY, dash)
        canvas.drawText(context.getString(R.string.report_goal_line), right, goalY - 3f, paint(8f, Color.DKGRAY, align = Paint.Align.RIGHT))
        val dayLabel = paint(7f, MUTED, align = Paint.Align.CENTER)
        (1..count).filter { it == 1 || it % 5 == 0 }.forEach { day ->
            canvas.drawText(day.toString(), left + (day - 0.5f) * slot, bottom + 11f, dayLabel)
        }
        return bottom + 14f
    }

    private fun tablePage(canvas: Canvas, context: Context, locale: Locale, numbers: NumberFormat, report: MonthReport, settings: AppSettings) {
        var y = header(canvas, context, context.getString(R.string.report_days_title, monthTitle(report, locale)), 2)
        val water = report.days.any { it.waterMl != null }
        val columns = buildList {
            add(WIDTH - MARGIN - (if (water) 240f else 160f))
            add(WIDTH - MARGIN - (if (water) 160f else 80f))
            add(WIDTH - MARGIN - (if (water) 80f else 0f))
            if (water) add(WIDTH - MARGIN)
        }
        val head = paint(9f, MUTED, bold = true, align = Paint.Align.RIGHT)
        val unit = context.getString(if (settings.distanceUnit == DistanceUnit.Mile) R.string.unit_mile else R.string.unit_km)
        canvas.drawText(context.getString(R.string.report_day), MARGIN, y, paint(9f, MUTED, bold = true))
        val titles = listOfNotNull(
            context.getString(R.string.report_steps),
            unit,
            context.getString(R.string.report_kcal),
            if (water) context.getString(R.string.report_water_short) else null,
        )
        titles.forEachIndexed { index, title -> canvas.drawText(title, columns[index], y, head) }
        y += 6f
        canvas.drawLine(MARGIN, y, WIDTH - MARGIN, y, paint(1f, LINE))
        y += 13f
        val text = paint(9.5f)
        val number = paint(9.5f, align = Paint.Align.RIGHT)
        val goalNumber = paint(9.5f, PRIMARY, bold = true, align = Paint.Align.RIGHT)
        val rowHeight = minOf(ROW, (HEIGHT - MARGIN - y) / report.days.size.coerceAtLeast(1))
        report.days.forEach { day ->
            val name = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            canvas.drawText("${day.date.dayOfMonth} $name", MARGIN, y, text)
            val km = Distance.inUnit(Distance.km(day.steps, settings.heightCm), settings.distanceUnit)
            canvas.drawText(numbers.format(day.steps), columns[0], y, if (day.steps >= report.goal) goalNumber else number)
            canvas.drawText(String.format(locale, "%.1f", km), columns[1], y, number)
            canvas.drawText(numbers.format(Distance.kcal(day.steps, settings.heightCm, settings.weightKg)), columns[2], y, number)
            if (water) canvas.drawText(day.waterMl?.let { numbers.format(it.toLong()) } ?: "–", columns[3], y, number)
            y += rowHeight
        }
    }
}
