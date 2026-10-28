package com.dordeorz.adimsayar.ui

import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.ExtraSettings
import com.dordeorz.adimsayar.data.ExtrasStore

const val PIN_MIN = 4
const val PIN_MAX = 8

/** The app lock: a PIN pad, and the fingerprint or face when that is turned on. */
@Composable
internal fun LockScreen(extras: ExtraSettings, onUnlock: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val biometric = extras.lockBiometric && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val prompt = stringResource(R.string.lock_title)
    val cancel = stringResource(R.string.lock_use_pin)
    LaunchedEffect(biometric) {
        if (biometric && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) askBiometric(context, prompt, cancel, onUnlock)
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(prompt, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
            Text(
                stringResource(if (wrong) R.string.lock_wrong else R.string.lock_enter_pin),
                style = MaterialTheme.typography.bodyMedium,
                color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(maxOf(PIN_MIN, pin.length)) { index ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                if (index < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                    )
                }
            }
            PinPad(
                modifier = Modifier.padding(top = 24.dp),
                onDigit = { digit ->
                    if (pin.length < PIN_MAX) pin += digit
                    wrong = false
                    if (pin.length >= PIN_MIN && ExtrasStore.hashPin(pin) == extras.pinHash) onUnlock()
                },
                onDelete = { pin = pin.dropLast(1) },
                onDone = { if (ExtrasStore.hashPin(pin) == extras.pinHash) onUnlock() else { wrong = true; pin = "" } },
            )
            if (biometric) {
                TextButton(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) askBiometric(context, prompt, cancel, onUnlock) }) {
                    Text(stringResource(R.string.lock_biometric_again))
                }
            }
        }
    }
}

@Composable
private fun PinPad(modifier: Modifier, onDigit: (Char) -> Unit, onDelete: () -> Unit, onDone: () -> Unit) {
    val rows = listOf("123", "456", "789")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { digit -> PadKey(digit.toString()) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PadKey("⌫", onDelete)
            PadKey("0") { onDigit('0') }
            PadKey("✓", onDone)
        }
    }
}

@Composable
private fun PadKey(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.size(72.dp), shape = CircleShape) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun askBiometric(context: Context, title: String, cancel: String, onUnlock: () -> Unit) {
    val executor = context.mainExecutor
    runCatching {
        BiometricPrompt.Builder(context)
            .setTitle(title)
            .setNegativeButton(cancel, executor) { _, _ -> }
            .build()
            .authenticate(
                CancellationSignal(),
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onUnlock()
                },
            )
    }
}
