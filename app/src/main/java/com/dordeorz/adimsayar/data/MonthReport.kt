package com.dordeorz.adimsayar.data

import java.time.LocalDate
import java.time.YearMonth

class ReportDay(val date: LocalDate, val steps: Long, val waterMl: Int?)

/** Numbers behind the monthly PDF report (idea 16); days after [today] are left out. */
class MonthReport(
    val month: YearMonth,
    val days: List<ReportDay>,
    val goal: Long,
    val total: Long,
    val average: Long,
    val goalDays: Int,
    val activeDays: Int,
    val best: ReportDay?,
    val previousTotal: Long?,
    val waterAverage: Int?,
    val weightStart: Weight?,
    val weightEnd: Weight?,
    val weeks: List<Pair<LocalDate, Long>>,
) {
    companion object {
        fun build(
            month: YearMonth,
            today: LocalDate,
            history: Map<LocalDate, Long>,
            water: Map<LocalDate, Int>,
            weights: List<Weight>,
            goal: Long,
        ): MonthReport {
            val last = if (YearMonth.from(today) == month) today else month.atEndOfMonth()
            val dates = (1..last.dayOfMonth).map { month.atDay(it) }
            val days = dates.map { ReportDay(it, history[it] ?: 0L, water[it]?.takeIf { ml -> ml > 0 }) }
            val total = days.sumOf { it.steps }
            val previous = month.minusMonths(1)
            val previousTotal = (1..previous.lengthOfMonth()).sumOf { history[previous.atDay(it)] ?: 0L }.takeIf { it > 0L }
            val drunk = days.mapNotNull { it.waterMl }
            val inMonth = weights.filter { YearMonth.from(it.date) == month }.sortedBy { it.date }
            val weeks = days.groupBy { it.date.minusDays((it.date.dayOfMonth - 1L) % 7L) }
                .map { (start, rows) -> start to rows.sumOf { it.steps } }
            return MonthReport(
                month = month,
                days = days,
                goal = goal,
                total = total,
                average = if (days.isEmpty()) 0L else total / days.size,
                goalDays = days.count { it.steps >= goal },
                activeDays = days.count { it.steps > 0L },
                best = days.filter { it.steps > 0L }.maxByOrNull { it.steps },
                previousTotal = previousTotal,
                waterAverage = if (drunk.isEmpty()) null else drunk.sum() / drunk.size,
                weightStart = inMonth.firstOrNull(),
                weightEnd = inMonth.lastOrNull()?.takeIf { inMonth.size > 1 },
                weeks = weeks,
            )
        }
    }
}
