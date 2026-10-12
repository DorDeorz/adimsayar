package com.dordeorz.adimsayar.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.DeviceKind
import com.dordeorz.adimsayar.data.HealthType
import com.dordeorz.adimsayar.data.SourceReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class WearableBrand(@param:StringRes val label: Int, @param:StringRes val steps: Int) {
    Xiaomi(R.string.setup_brand_xiaomi, R.string.setup_steps_xiaomi),
    Samsung(R.string.setup_brand_samsung, R.string.setup_steps_samsung),
    Google(R.string.setup_brand_google, R.string.setup_steps_google),
    Garmin(R.string.setup_brand_garmin, R.string.setup_steps_garmin),
    Huawei(R.string.setup_brand_huawei, R.string.setup_steps_huawei),
    Other(R.string.setup_brand_other, R.string.setup_steps_other),
}

private val KNOWN_APPS = mapOf(
    "com.mi.health" to "Mi Fitness",
    "com.xiaomi.wearable" to "Mi Fitness",
    "com.xiaomi.hm.health" to "Zepp Life",
    "com.huami.watch.hmwatchmanager" to "Zepp",
    "com.sec.android.app.shealth" to "Samsung Health",
    "com.google.android.apps.fitness" to "Google Fit",
    "com.fitbit.FitbitMobile" to "Fitbit",
    "com.garmin.android.apps.connectmobile" to "Garmin Connect",
)

@Composable
fun SourceWarningCard(onSetup: () -> Unit) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_warning_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Text(
                stringResource(R.string.setup_warning_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSetup) { Text(stringResource(R.string.setup_open)) }
        }
    }
}

@Composable
fun WearableSetupScreen(state: MainUiState, actions: MainActions, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    var brand by rememberSaveable { mutableStateOf<WearableBrand?>(null) }
    val report by produceState<SourceReport?>(null, state.wearableEnabled, state.healthRefresh, refresh) {
        value = null
        value = if (state.wearableEnabled) withContext(Dispatchers.IO) { actions.loadSources() } else null
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            SetupStep(1, stringResource(R.string.setup_step_hc)) {
                when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> SetupText(stringResource(R.string.setup_hc_too_old))
                    state.wearableAvailable -> SetupText(stringResource(R.string.setup_hc_ready))
                    else -> {
                        SetupText(stringResource(R.string.setup_hc_missing))
                        Button(onClick = actions.onInstallHealthConnect) { Text(stringResource(R.string.setup_hc_install)) }
                    }
                }
            }
        }
        item {
            SetupStep(2, stringResource(R.string.setup_step_brand)) {
                SetupText(stringResource(R.string.setup_brand_question))
                BrandChips(brand) { brand = it }
                brand?.let { SetupText(stringResource(it.steps)) }
                if (state.wearableAvailable) {
                    OutlinedButton(onClick = actions.onOpenHealthConnect) { Text(stringResource(R.string.setup_open_hc)) }
                }
            }
        }
        item {
            SetupStep(3, stringResource(R.string.setup_step_permission)) {
                when {
                    !state.wearableAvailable -> SetupText(stringResource(R.string.setup_permission_wait))
                    !state.wearableEnabled -> {
                        SetupText(stringResource(R.string.setup_permission_enable))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.wearable_card_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = false, onCheckedChange = { actions.onWearableEnabledChange(true) })
                        }
                    }
                    report?.missingPermissions == true -> {
                        SetupText(stringResource(R.string.health_permissions_missing))
                        Button(onClick = actions.onGrantHealthPermissions) { Text(stringResource(R.string.health_grant)) }
                    }
                    else -> SetupText(stringResource(R.string.setup_permission_ready))
                }
            }
        }
        item {
            SetupStep(4, stringResource(R.string.setup_step_check)) {
                val data = report
                when {
                    !state.wearableEnabled -> SetupText(stringResource(R.string.setup_check_wait))
                    data == null -> SetupText(stringResource(R.string.health_loading))
                    data.apps.isEmpty() -> SetupText(stringResource(R.string.setup_check_none))
                    else -> {
                        SetupText(stringResource(R.string.setup_check_found))
                        data.apps.forEach { app ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(appLabel(LocalContext.current, app.packageName), style = MaterialTheme.typography.bodyLarge)
                                app.devices.forEach { device ->
                                    Text(
                                        listOfNotNull(device.name, stringResource(deviceLabel(device.kind))).joinToString(" · "),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Text(
                                    app.types.map { stringResource(typeLabel(it)) }.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        SetupText(stringResource(if (data.hasWearable) R.string.setup_check_ok else R.string.setup_check_no_wearable))
                    }
                }
                if (state.wearableEnabled) {
                    OutlinedButton(onClick = { refresh++ }) { Text(stringResource(R.string.setup_check_again)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrandChips(selected: WearableBrand?, onSelect: (WearableBrand) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WearableBrand.entries.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(stringResource(option.label)) })
        }
    }
}

@Composable
private fun SetupStep(number: Int, title: String, content: @Composable () -> Unit) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_step_title, number, title), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SetupText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun deviceLabel(kind: DeviceKind): Int = when (kind) {
    DeviceKind.Watch -> R.string.device_watch
    DeviceKind.Band -> R.string.device_band
    DeviceKind.Ring -> R.string.device_ring
    DeviceKind.Phone -> R.string.device_phone
    DeviceKind.Other -> R.string.device_other
}

private fun typeLabel(type: HealthType): Int = when (type) {
    HealthType.Steps -> R.string.source_steps
    HealthType.Heart -> R.string.source_heart
    HealthType.Sleep -> R.string.source_sleep
    HealthType.Oxygen -> R.string.source_oxygen
    HealthType.Exercise -> R.string.source_exercise
    HealthType.Distance -> R.string.source_distance
    HealthType.Calories -> R.string.source_calories
}

private fun appLabel(context: Context, packageName: String): String = try {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    context.packageManager.getApplicationLabel(info).toString()
} catch (e: PackageManager.NameNotFoundException) {
    KNOWN_APPS[packageName] ?: packageName
}
