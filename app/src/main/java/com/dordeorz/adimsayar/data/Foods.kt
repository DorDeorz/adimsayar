package com.dordeorz.adimsayar.data

import androidx.annotation.StringRes
import com.dordeorz.adimsayar.R

/** Burned calories put as everyday food: "≈ 1 simit". */
object Foods {

    class Food(@StringRes val name: Int, val kcal: Long)

    private val FOODS = listOf(
        Food(R.string.food_burger, 500L),
        Food(R.string.food_simit, 280L),
        Food(R.string.food_chocolate, 230L),
        Food(R.string.food_cola, 140L),
        Food(R.string.food_apple, 95L),
    )

    /** The biggest food the calories cover, and how many of it; null under one apple. */
    fun equivalent(kcal: Long): Pair<Food, Long>? = FOODS.firstOrNull { kcal >= it.kcal }?.let { it to kcal / it.kcal }
}
