package com.dordeorz.adimsayar.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Walks started and stopped by hand: dog walks and GPS-tracked walks. */
object Walks {

    const val KIND_DOG = "dog"
    const val KIND_GPS = "gps"

    fun observe(context: Context): Flow<List<Walk>> = StepDatabase.get(context).extras().observeWalks()

    suspend fun save(context: Context, walk: Walk): Long = StepDatabase.get(context).extras().upsertWalk(walk)

    suspend fun delete(context: Context, id: Long) = StepDatabase.get(context).extras().deleteWalk(id)
}
