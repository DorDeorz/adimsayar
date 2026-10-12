package com.dordeorz.adimsayar.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

object StreakFreezes {

    const val PER_MONTH = 2

    fun observe(context: Context): Flow<Set<LocalDate>> =
        StepDatabase.get(context).keep().observeFreezes().map { rows -> rows.map { LocalDate.parse(it.date) }.toSet() }

    suspend fun load(context: Context): Set<LocalDate> =
        StepDatabase.get(context).keep().loadFreezes().map { LocalDate.parse(it.date) }.toSet()

    suspend fun set(context: Context, date: LocalDate, frozen: Boolean) {
        val dao = StepDatabase.get(context).keep()
        if (frozen) dao.addFreeze(StreakFreeze(date.toString())) else dao.removeFreeze(date.toString())
    }

    fun left(freezes: Set<LocalDate>, month: YearMonth): Int = (PER_MONTH - freezes.count { YearMonth.from(it) == month }).coerceAtLeast(0)
}

object KeptMedals {

    fun observe(context: Context): Flow<Map<String, LocalDate>> =
        StepDatabase.get(context).keep().observeMedals().map { rows -> rows.associate { it.id to LocalDate.parse(it.date) } }

    fun key(medal: Medal): String = "${medal.group.name}:${medal.target}"

    fun apply(medals: List<Medal>, kept: Map<String, LocalDate>): List<Medal> = medals.map { medal ->
        val stored = kept[key(medal)] ?: return@map medal
        val computed = medal.earnedOn
        medal.copy(earnedOn = if (computed != null && computed < stored) computed else stored)
    }

    private fun newer(medals: List<Medal>, kept: Map<String, LocalDate>): List<KeptMedal> = medals.mapNotNull { medal ->
        val earned = medal.earnedOn ?: return@mapNotNull null
        val stored = kept[key(medal)]
        if (stored == null || earned < stored) KeptMedal(key(medal), earned.toString()) else null
    }

    suspend fun keep(context: Context, medals: List<Medal>) {
        val dao = StepDatabase.get(context).keep()
        val kept = dao.loadMedals().associate { it.id to LocalDate.parse(it.date) }
        val changed = newer(medals, kept)
        if (changed.isNotEmpty()) dao.keepMedals(changed)
    }
}
