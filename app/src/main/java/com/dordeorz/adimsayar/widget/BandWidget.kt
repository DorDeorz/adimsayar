package com.dordeorz.adimsayar.widget

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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.band.BandModels
import com.dordeorz.adimsayar.data.BandBattery
import com.dordeorz.adimsayar.data.BandBatteryState
import com.dordeorz.adimsayar.data.BandDirect
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.ui.Tab
import com.dordeorz.adimsayar.ui.deviceImage

/** The band's battery level, with the band's name and when it last reported. */
class BandWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val battery by remember { BandBattery.observe(context) }.collectAsState()
            val settings by SettingsStore.get(context).settings.collectAsState()
            val localized = remember(settings.language) { Locales.wrap(context) }
            val status = remember(battery) { BandDirect.status(context) }
            WidgetTheme(settings.dynamicColor) {
                WidgetSurface(Tab.Settings, settings.widgetOpacity) {
                    BandContent(localized, status.name, battery)
                }
            }
        }
    }
}

class BandWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BandWidget()
}

@Composable
private fun BandContent(context: Context, name: String?, battery: BandBatteryState?) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(deviceImage(BandModels.find(name)?.shape ?: BandModels.shapeOf(name))),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            )
            Spacer(GlanceModifier.width(6.dp))
            WidgetTitle(BandModels.displayName(name) ?: context.getString(R.string.widget_band_label))
        }
        Spacer(GlanceModifier.height(6.dp))
        if (battery == null) {
            Text(
                text = context.getString(R.string.widget_band_empty),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            return@Column
        }
        val low = battery.level <= BandBattery.LOW_LEVEL && battery.charging != true
        val accent = if (low) GlanceTheme.colors.error else GlanceTheme.colors.primary
        Text(
            text = context.getString(R.string.band_battery_percent, battery.level),
            maxLines = 1,
            style = TextStyle(color = if (low) accent else GlanceTheme.colors.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold),
        )
        if (battery.charging == true) {
            Text(
                text = context.getString(R.string.band_battery_charging),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = battery.level / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = accent,
            backgroundColor = GlanceTheme.colors.secondaryContainer,
        )
        Spacer(GlanceModifier.defaultWeight())
        UpdatedText(context, battery.atMs.takeIf { it > 0L })
    }
}
