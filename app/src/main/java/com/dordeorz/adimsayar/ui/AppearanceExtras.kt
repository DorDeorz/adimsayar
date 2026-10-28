package com.dordeorz.adimsayar.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.ExtrasStore
import com.dordeorz.adimsayar.wallpaper.BirdWallpaper

/** Look and privacy extras: quote of the day, simple view, app lock and the live wallpaper. */
@Composable
internal fun AppearanceExtras(state: MainUiState, actions: MainActions) {
    val extras = state.extra.extras
    val context = LocalContext.current
    var pinDialog by rememberSaveable { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    SwitchRow(
        title = stringResource(R.string.quote_setting),
        hint = stringResource(R.string.quote_setting_hint),
        checked = extras.showQuote,
        onCheckedChange = { on -> actions.extra.update { it.copy(showQuote = on) } },
    )
    SwitchRow(
        title = stringResource(R.string.simple_view),
        hint = stringResource(R.string.simple_view_hint),
        checked = extras.simpleView,
        onCheckedChange = { on -> actions.extra.update { it.copy(simpleView = on) } },
    )
    SwitchRow(
        title = stringResource(R.string.app_lock),
        hint = stringResource(R.string.app_lock_hint),
        checked = extras.appLock && extras.pinHash.isNotEmpty(),
        onCheckedChange = { on ->
            if (on) pinDialog = true else actions.extra.update { it.copy(appLock = false, lockBiometric = false, pinHash = "") }
        },
    )
    if (extras.appLock && extras.pinHash.isNotEmpty()) {
        if (biometricAvailable(context)) {
            SwitchRow(
                title = stringResource(R.string.app_lock_biometric),
                hint = stringResource(R.string.app_lock_biometric_hint),
                checked = extras.lockBiometric,
                onCheckedChange = { on -> actions.extra.update { it.copy(lockBiometric = on) } },
            )
        }
        ClickRow(stringResource(R.string.app_lock_change), stringResource(R.string.app_lock_change_hint)) { pinDialog = true }
    }
    ClickRow(stringResource(R.string.wallpaper_name), stringResource(R.string.wallpaper_set_hint)) { openWallpaper(context) }
    if (pinDialog) {
        PinDialog(
            onDismiss = { pinDialog = false },
            onSave = { pin ->
                actions.extra.update { it.copy(appLock = true, pinHash = ExtrasStore.hashPin(pin)) }
                pinDialog = false
            },
        )
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var again by rememberSaveable { mutableStateOf("") }
    val valid = pin.length in PIN_MIN..PIN_MAX && pin.all { it.isDigit() }
    val match = valid && pin == again
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_lock_set_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_lock_pin_hint, PIN_MIN, PIN_MAX))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(PIN_MAX) },
                    label = { Text(stringResource(R.string.app_lock_pin)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = again,
                    onValueChange = { again = it.filter(Char::isDigit).take(PIN_MAX) },
                    label = { Text(stringResource(R.string.app_lock_pin_again)) },
                    singleLine = true,
                    isError = again.isNotEmpty() && again != pin,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(pin) }, enabled = match) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun biometricAvailable(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val manager = context.getSystemService(BiometricManager::class.java) ?: return false
    @Suppress("DEPRECATION")
    return manager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
}

private fun openWallpaper(context: Context) {
    val component = ComponentName(context, BirdWallpaper::class.java)
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(direct)
    } catch (e: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
