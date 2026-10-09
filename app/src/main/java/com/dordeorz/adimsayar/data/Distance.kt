package com.dordeorz.adimsayar.data

object Distance {

    private const val STRIDE_PER_HEIGHT = 0.415
    private const val KCAL_PER_KG_KM = 0.5
    private const val KM_PER_MILE = 1.609344

    fun km(steps: Long, heightCm: Int): Double = steps * heightCm * STRIDE_PER_HEIGHT / 100_000.0

    fun kcal(steps: Long, heightCm: Int, weightKg: Int): Long = Math.round(km(steps, heightCm) * weightKg * KCAL_PER_KG_KM)

    fun inUnit(km: Double, unit: DistanceUnit): Double = if (unit == DistanceUnit.Mile) km / KM_PER_MILE else km
}
