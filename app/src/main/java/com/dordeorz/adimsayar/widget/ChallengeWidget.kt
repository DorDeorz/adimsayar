package com.dordeorz.adimsayar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.data.Challenge
import com.dordeorz.adimsayar.data.ChallengeMath
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.ui.Tab
import com.dordeorz.adimsayar.ui.challengeProgress
import com.dordeorz.adimsayar.ui.challengeText
import java.time.LocalDate

class ChallengeWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = StepRepository.get(context)
        val today = LocalDate.now()
        val initial = repository.loadHistory()
        provideContent {
            val history by remember { repository.observeHistory() }.collectAsState(initial)
            val settings by SettingsStore.get(context).settings.collectAsState()
            val localized = remember(settings.language) { Locales.wrap(context) }
            val challenges = remember(history, settings.dailyGoal, settings.weekStart) {
                ChallengeMath.summary(history, today, settings.dailyGoal, settings.weekStart).current
            }
            WidgetTheme(settings.dynamicColor) {
                WidgetSurface(Tab.Medals, settings.widgetOpacity) {
                    ChallengeContent(localized, challenges)
                }
            }
        }
    }
}

class ChallengeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChallengeWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Schedules.requestRead(context)
    }
}

private val PADDING = 12.dp
private val TITLE_HEIGHT = 20.dp
private val ROW_WITH_PROGRESS = 44.dp

@Composable
private fun ChallengeContent(context: Context, challenges: List<Challenge>) {
    val height = LocalSize.current.height - PADDING * 2
    val showProgress = challenges.isNotEmpty() && (height - TITLE_HEIGHT) / challenges.size >= ROW_WITH_PROGRESS
    Column(modifier = GlanceModifier.fillMaxSize()) {
        WidgetTitle(context.getString(R.string.challenges_title) + " · " + "${challenges.count { it.done }}/${challenges.size}")
        Spacer(GlanceModifier.height(4.dp))
        challenges.forEach { ChallengeRow(context, it, showProgress) }
    }
}

@Composable
private fun ColumnScope.ChallengeRow(context: Context, challenge: Challenge, showProgress: Boolean) {
    Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (challenge.done) {
                Image(
                    provider = ImageProvider(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.tertiary),
                )
                Spacer(GlanceModifier.width(4.dp))
            }
            Text(
                text = challengeText(context, challenge),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
            )
        }
        Spacer(GlanceModifier.height(3.dp))
        LinearProgressIndicator(
            progress = (challenge.progress.toFloat() / challenge.target).coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(5.dp),
            color = if (challenge.done) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.secondaryContainer,
        )
        if (showProgress) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = challengeProgress(context, challenge),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            )
        }
    }
}
