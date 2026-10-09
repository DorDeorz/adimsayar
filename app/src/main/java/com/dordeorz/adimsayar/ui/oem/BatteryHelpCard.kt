package com.dordeorz.adimsayar.ui.oem

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R

object BatteryOptimization {

    fun isExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun open(context: Context, profile: OemProfile, target: OemTarget): Boolean = when (target) {
        OemTarget.Autostart -> profile.autostartComponents.any { tryStart(context, Intent().setComponent(it)) } ||
            openAppDetails(context) || openBatteryList(context)
        OemTarget.BatterySettings -> openBatteryList(context) || openAppDetails(context)
        OemTarget.AppDetails -> openAppDetails(context) || openBatteryList(context)
        OemTarget.Manual -> false
    }

    fun openAppDetails(context: Context): Boolean =
        tryStart(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))

    private fun openBatteryList(context: Context): Boolean =
        tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    private fun tryStart(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
}

@Composable
fun BatteryHelpCard(profile: OemProfile, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val done = remember { mutableStateListOf<Int>() }
    val failed = remember { mutableStateListOf<Int>() }
    val containerColor = if (profile.aggressive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(if (profile.aggressive) R.string.oem_title else R.string.oem_title_stock),
                style = MaterialTheme.typography.titleMedium,
            )
            if (profile.aggressive) {
                Text(text = stringResource(R.string.oem_intro), style = MaterialTheme.typography.bodyMedium)
            }
            profile.steps.forEachIndexed { index, step ->
                StepRow(
                    step = step,
                    checked = index in done,
                    failed = index in failed,
                    onCheckedChange = { if (it) done.add(index) else done.remove(index) },
                    onOpen = { if (!BatteryOptimization.open(context, profile, step.target)) failed.add(index) },
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.oem_dismiss))
            }
        }
    }
}

@Composable
private fun StepRow(
    step: OemStep,
    checked: Boolean,
    failed: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(step.text), style = MaterialTheme.typography.bodyMedium)
            if (failed) {
                Text(
                    text = stringResource(R.string.oem_open_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (step.target != OemTarget.Manual && !failed) {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.oem_open)) }
        }
    }
}
