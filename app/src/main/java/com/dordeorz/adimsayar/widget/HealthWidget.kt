package com.dordeorz.adimsayar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.ui.Tab
import com.dordeorz.adimsayar.data.HealthSnapshot
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.WearableHealth
import com.dordeorz.adimsayar.data.WearableSteps
import com.dordeorz.adimsayar.ui.CLOCK_FORMAT
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val enabled = WearableSteps.isEnabled(context)
        val snapshot = if (enabled) HealthCache.load(context) else null
        provideContent {
            val settings by SettingsStore.get(context).settings.collectAsState()
            val localized = remember(settings.language) { Locales.wrap(context) }
            WidgetTheme(settings.dynamicColor) {
                WidgetSurface(Tab.Health, settings.widgetOpacity) {
                    HealthContent(localized, enabled, snapshot)
                }
            }
        }
    }
}

class HealthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HealthWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Schedules.requestRead(context)
    }
}

internal object HealthCache {

    private const val PREFS = "health_widget"
    private const val KEY_READ_AT = "read_at"
    private const val KEY_DATE = "date"
    private const val KEY_SLEEP = "sleep"
    private const val KEY_RESTING = "resting"
    private const val KEY_LATEST = "latest"
    private const val KEY_LATEST_AT = "latest_at"
    private const val TTL_MS = 15 * 60 * 1000L

    suspend fun load(context: Context): HealthSnapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val cached = if (prefs.getString(KEY_DATE, null) == today) {
            HealthSnapshot(
                prefs.getLong(KEY_SLEEP, -1L).takeIf { it >= 0L },
                prefs.getLong(KEY_RESTING, -1L).takeIf { it >= 0L },
                prefs.getLong(KEY_LATEST, -1L).takeIf { it >= 0L },
                prefs.getLong(KEY_LATEST_AT, -1L).takeIf { it >= 0L }?.let(Instant::ofEpochMilli),
            )
        } else {
            null
        }
        if (cached != null && System.currentTimeMillis() - prefs.getLong(KEY_READ_AT, 0L) < TTL_MS) return cached
        val fresh = WearableHealth.snapshot(context) ?: return cached
        prefs.edit {
            putLong(KEY_READ_AT, System.currentTimeMillis())
            putString(KEY_DATE, today)
            putLong(KEY_SLEEP, fresh.sleepMinutes ?: -1L)
            putLong(KEY_RESTING, fresh.resting ?: -1L)
            putLong(KEY_LATEST, fresh.latest ?: -1L)
            putLong(KEY_LATEST_AT, fresh.latestAt?.toEpochMilli() ?: -1L)
        }
        return fresh
    }
}

@Composable
private fun HealthContent(context: Context, enabled: Boolean, snapshot: HealthSnapshot?) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Text(
            text = context.getString(R.string.widget_health_title),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(GlanceModifier.height(6.dp))
        if (!enabled) {
            Text(
                text = context.getString(R.string.widget_health_off),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            return@Column
        }
        val sleep = snapshot?.sleepMinutes?.takeIf { it > 0L }
        InfoRow(
            R.drawable.ic_moon,
            if (sleep == null) "-" else context.getString(R.string.band_sleep_value, sleep / 60, sleep % 60),
            context.getString(R.string.widget_health_sleep),
        )
        InfoRow(
            R.drawable.ic_heart,
            snapshot?.resting?.let { context.getString(R.string.health_bpm, it) } ?: "-",
            context.getString(R.string.heart_resting),
        )
        val latestAt = snapshot?.latestAt
        InfoRow(
            R.drawable.ic_heart,
            snapshot?.latest?.let { context.getString(R.string.health_bpm, it) } ?: "-",
            if (latestAt == null) {
                context.getString(R.string.widget_health_latest)
            } else {
                context.getString(R.string.widget_health_latest_at, CLOCK_FORMAT.format(latestAt.atZone(ZoneId.systemDefault())))
            },
        )
    }
}
