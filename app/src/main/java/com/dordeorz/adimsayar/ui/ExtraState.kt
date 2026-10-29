package com.dordeorz.adimsayar.ui

import com.dordeorz.adimsayar.data.ExtraSettings
import com.dordeorz.adimsayar.data.Walk
import java.time.LocalDate

/** State of the extras added in 0.17. */
data class ExtraState(
    val extras: ExtraSettings = ExtraSettings(),
    val notes: Map<LocalDate, String> = emptyMap(),
    val overrides: Map<LocalDate, Long> = emptyMap(),
    val walks: List<Walk> = emptyList(),
    val floors: Map<LocalDate, Int> = emptyMap(),
    val hasPressure: Boolean = false,
)
