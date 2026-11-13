package com.dordeorz.adimsayar.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Walks started and stopped by hand: dog walks and GPS-tracked walks. */
object Walks {

    const val KIND_DOG = "dog"
    const val KIND_GPS = "gps"

    fun observe(context: Context): Flow<List<Walk>> = StepDatabase.get(context).extras().observeWalks()

    suspend fun save(context: Context, walk: Walk): Long = StepDatabase.get(context).extras().upsertWalk(walk)

    suspend fun delete(context: Context, id: Long) = StepDatabase.get(context).extras().deleteWalk(id)

    /** The steps of a walk that started at [start], when the day's count was [startSteps] and is [todaySteps] now. */
    suspend fun stepsSince(context: Context, start: Long, startSteps: Long, todaySteps: Long): Long {
        val zone = ZoneId.systemDefault()
        val startDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        if (startDay == LocalDate.now(zone)) return stepsDuring(startSteps, todaySteps, null)
        return stepsDuring(startSteps, todaySteps, StepRepository.get(context).day(startDay) ?: startSteps)
    }

    /**
     * [firstDayTotal] is null for a walk within one day. After midnight the day's count starts again, so the
     * walk gets the rest of the day it started on (that day's whole count is [firstDayTotal]) and today's steps.
     */
    fun stepsDuring(startSteps: Long, todaySteps: Long, firstDayTotal: Long?): Long =
        if (firstDayTotal == null) {
            (todaySteps - startSteps).coerceAtLeast(0L)
        } else {
            (firstDayTotal - startSteps).coerceAtLeast(0L) + todaySteps.coerceAtLeast(0L)
        }
}
