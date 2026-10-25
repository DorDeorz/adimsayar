package com.dordeorz.adimsayar.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.band.BandResult
import com.dordeorz.adimsayar.band.BandScanner
import com.dordeorz.adimsayar.band.FoundBand
import com.dordeorz.adimsayar.band.XiaomiCloud
import com.dordeorz.adimsayar.band.XiaomiCloudDevice
import com.dordeorz.adimsayar.data.BandDirect
import com.dordeorz.adimsayar.data.ReadSource
import com.dordeorz.adimsayar.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

private const val SCAN_MS = 15_000L

@Composable
fun BandSetupScreen(actions: MainActions, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val status = remember(refresh) { BandDirect.status(context) }
    var keyText by rememberSaveable { mutableStateOf("") }
    var keyMessage by rememberSaveable { mutableStateOf<Int?>(null) }
    var addressText by rememberSaveable { mutableStateOf("") }
    var addressInvalid by rememberSaveable { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<Int?>(null) }
    val found = remember { mutableStateListOf<FoundBand>() }
    var cloud by remember { mutableStateOf<CloudState>(CloudState.Idle) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<BandResult?>(null) }
    val scanner = remember { BandScanner(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { scanner.stop() } }

    fun startScan() {
        found.clear()
        scanMessage = null
        scanning = scanner.start { band ->
            if (found.none { it.address == band.address }) found.add(band)
        }
        if (!scanning) scanMessage = R.string.band_result_bluetooth_off
    }

    fun saveCloudDevice(device: XiaomiCloudDevice): CloudState {
        BandDirect.setKey(context, device.key)
        BandDirect.setDevice(context, device.address, device.name)
        return CloudState.Message(R.string.band_cloud_done, device.name)
    }

    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        delay(SCAN_MS)
        scanner.stop()
        scanning = false
        if (found.isEmpty()) scanMessage = R.string.band_scan_empty
    }

    val scanPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) startScan() else scanMessage = R.string.band_scan_denied
    }
    val connectPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

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
                Text(stringResource(R.string.band_title), style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            BandStep(1, stringResource(R.string.band_step_about)) {
                BandText(stringResource(R.string.band_about_text))
            }
        }
        item {
            BandStep(2, stringResource(R.string.band_step_key)) {
                val busy = cloud is CloudState.Starting || cloud is CloudState.Waiting || cloud is CloudState.Loading
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        cloud = CloudState.Starting
                        val client = XiaomiCloud()
                        val login = withContext(Dispatchers.IO) { client.startLogin() }
                        if (login == null) {
                            cloud = CloudState.Message(R.string.band_cloud_failed)
                            return@launch
                        }
                        val qr = withContext(Dispatchers.IO) {
                            client.qrImage(login)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                        }
                        cloud = CloudState.Waiting(login.loginUrl, qr)
                        if (!withContext(Dispatchers.IO) { client.waitForLogin(login) }) {
                            cloud = CloudState.Message(R.string.band_cloud_failed)
                            return@launch
                        }
                        cloud = CloudState.Loading
                        val devices = withContext(Dispatchers.IO) { client.devices() }
                        cloud = when (devices.size) {
                            0 -> CloudState.Message(R.string.band_cloud_none)
                            1 -> saveCloudDevice(devices.first())
                            else -> CloudState.Devices(devices)
                        }
                        refresh++
                    }
                }) { Text(stringResource(R.string.band_cloud_button)) }
                when (val state = cloud) {
                    CloudState.Idle -> Unit
                    CloudState.Starting -> BandText(stringResource(R.string.band_cloud_waiting))
                    is CloudState.Waiting -> {
                        BandText(stringResource(R.string.band_cloud_help))
                        OutlinedButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, state.loginUrl.toUri())) }
                        }) { Text(stringResource(R.string.band_cloud_open)) }
                        state.qr?.let { qr ->
                            Image(qr, contentDescription = null, modifier = Modifier.size(180.dp))
                            BandText(stringResource(R.string.band_cloud_qr))
                        }
                        BandText(stringResource(R.string.band_cloud_waiting))
                    }
                    CloudState.Loading -> BandText(stringResource(R.string.band_cloud_loading))
                    is CloudState.Devices -> state.devices.forEach { device ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    cloud = saveCloudDevice(device)
                                    refresh++
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(device.name, style = MaterialTheme.typography.bodyLarge)
                            Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is CloudState.Message -> BandText(state.arg?.let { stringResource(state.text, it) } ?: stringResource(state.text))
                }
                Text(stringResource(R.string.band_key_manual), style = MaterialTheme.typography.bodyLarge)
                BandText(stringResource(R.string.band_key_help))
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it.trim() },
                    label = { Text(stringResource(R.string.band_key_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {
                    keyMessage = if (BandDirect.setKey(context, keyText)) R.string.band_key_saved else R.string.band_key_invalid
                    if (keyMessage == R.string.band_key_saved) keyText = ""
                    refresh++
                }) { Text(stringResource(R.string.band_key_save)) }
                keyMessage?.let { BandText(stringResource(it)) }
                if (status.hasKey && keyMessage == null) BandText(stringResource(R.string.band_key_saved))
            }
        }
        item {
            BandStep(3, stringResource(R.string.band_step_device)) {
                BandText(
                    if (status.address == null) {
                        stringResource(R.string.band_device_none)
                    } else {
                        stringResource(R.string.band_device_selected, status.name ?: status.address)
                    },
                )
                OutlinedButton(enabled = !scanning, onClick = {
                    if (BandDirect.granted(context, BandDirect.SCAN_PERMISSIONS)) startScan() else scanPermission.launch(BandDirect.SCAN_PERMISSIONS)
                }) { Text(stringResource(R.string.band_scan)) }
                if (scanning) BandText(stringResource(R.string.band_scanning))
                scanMessage?.let { BandText(stringResource(it)) }
                found.forEach { band ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                BandDirect.setDevice(context, band.address, band.name)
                                scanner.stop()
                                scanning = false
                                found.clear()
                                refresh++
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(band.name, style = MaterialTheme.typography.bodyLarge)
                        Text(band.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                BandText(stringResource(R.string.band_address_help))
                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it.trim().uppercase() },
                    label = { Text(stringResource(R.string.band_address_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(enabled = addressText.isNotEmpty(), onClick = {
                    addressInvalid = !BluetoothAdapter.checkBluetoothAddress(addressText)
                    if (!addressInvalid) {
                        BandDirect.setDevice(context, addressText, null)
                        addressText = ""
                        refresh++
                    }
                }) { Text(stringResource(R.string.band_address_save)) }
                if (addressInvalid) BandText(stringResource(R.string.band_address_invalid))
            }
        }
        item {
            BandStep(4, stringResource(R.string.band_step_test)) {
                val ready = status.address != null && status.hasKey
                Button(enabled = ready && !testing, onClick = {
                    if (!BandDirect.granted(context, BandDirect.CONNECT_PERMISSIONS)) {
                        connectPermission.launch(BandDirect.CONNECT_PERMISSIONS)
                        return@Button
                    }
                    testing = true
                    testResult = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            BandDirect.sync(context.applicationContext, ReadSource.App, force = true).also {
                                Widgets.updateAll(context.applicationContext)
                            }
                        }
                        testing = false
                        testResult = result
                        refresh++
                    }
                }) { Text(stringResource(R.string.band_test)) }
                if (testing) BandText(stringResource(R.string.band_testing))
                (testResult ?: status.lastResult)?.let { BandText(stringResource(BandDirect.resultText(it))) }
                if (status.lastSyncMs > 0L) {
                    val time = Instant.ofEpochMilli(status.lastSyncMs).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
                    BandText(stringResource(R.string.band_last_sync, time))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.band_enable), style = MaterialTheme.typography.bodyLarge)
                        BandText(stringResource(R.string.band_enable_hint))
                    }
                    Switch(
                        checked = status.enabled,
                        enabled = ready || status.enabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                actions.onBandDirectChange(enabled)
                                refresh++
                            }
                        },
                    )
                }
            }
        }
    }
}

private sealed interface CloudState {
    data object Idle : CloudState
    data object Starting : CloudState
    class Waiting(val loginUrl: String, val qr: ImageBitmap?) : CloudState
    data object Loading : CloudState
    class Devices(val devices: List<XiaomiCloudDevice>) : CloudState
    class Message(val text: Int, val arg: String? = null) : CloudState
}

@Composable
private fun BandStep(number: Int, title: String, content: @Composable () -> Unit) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_step_title, number, title), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun BandText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
