package com.dordeorz.adimsayar.ui

import android.content.Context
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.Challenge
import com.dordeorz.adimsayar.data.ChallengeKind

internal fun challengeText(context: Context, challenge: Challenge): String = when (challenge.kind) {
    ChallengeKind.WeekTotal -> context.getString(R.string.challenge_week_total, format(challenge.target))
    ChallengeKind.GoalDays -> context.getString(R.string.challenge_goal_days, challenge.target.toInt())
    ChallengeKind.BigDay -> context.getString(R.string.challenge_big_day, format(challenge.target))
    ChallengeKind.EveryDay -> context.getString(R.string.challenge_every_day, format(challenge.threshold))
    ChallengeKind.Weekend -> context.getString(R.string.challenge_weekend, format(challenge.target))
    ChallengeKind.BeatLastWeek -> context.getString(R.string.challenge_beat_last_week, format(challenge.target - 1))
}

internal fun challengeProgress(context: Context, challenge: Challenge): String = when (challenge.kind) {
    ChallengeKind.GoalDays, ChallengeKind.EveryDay ->
        context.getString(R.string.challenges_days_progress, challenge.progress.coerceAtMost(challenge.target).toInt(), challenge.target.toInt())
    else -> context.getString(R.string.challenges_steps_progress, format(challenge.progress), format(challenge.target))
}
