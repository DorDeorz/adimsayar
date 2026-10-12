package com.dordeorz.adimsayar.ui

import androidx.annotation.StringRes
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.BirdItem
import com.dordeorz.adimsayar.data.BirdMood
import com.dordeorz.adimsayar.data.BirdStage

@StringRes
internal fun BirdStage.title(): Int = when (this) {
    BirdStage.Egg -> R.string.bird_stage_egg
    BirdStage.Hatchling -> R.string.bird_stage_hatchling
    BirdStage.Chick -> R.string.bird_stage_chick
    BirdStage.Fledgling -> R.string.bird_stage_fledgling
    BirdStage.Bird -> R.string.bird_stage_bird
    BirdStage.Elder -> R.string.bird_stage_elder
}

@StringRes
internal fun BirdMood.text(): Int = when (this) {
    BirdMood.Sleepy -> R.string.bird_mood_sleepy
    BirdMood.Sad -> R.string.bird_mood_sad
    BirdMood.Calm -> R.string.bird_mood_calm
    BirdMood.Happy -> R.string.bird_mood_happy
    BirdMood.Excited -> R.string.bird_mood_excited
}

@StringRes
internal fun BirdItem.title(): Int = when (this) {
    BirdItem.Scarf -> R.string.bird_item_scarf
    BirdItem.Ribbon -> R.string.bird_item_ribbon
    BirdItem.Sun -> R.string.bird_item_sun
    BirdItem.Cap -> R.string.bird_item_cap
    BirdItem.Crown -> R.string.bird_item_crown
}

@StringRes
internal fun BirdItem.unlock(): Int = when (this) {
    BirdItem.Scarf -> R.string.bird_unlock_scarf
    BirdItem.Ribbon -> R.string.bird_unlock_ribbon
    BirdItem.Sun -> R.string.bird_unlock_sun
    BirdItem.Cap -> R.string.bird_unlock_cap
    BirdItem.Crown -> R.string.bird_unlock_crown
}
