package com.dordeorz.adimsayar.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.InstallStart
import com.dordeorz.adimsayar.data.UpdateChecker
import com.dordeorz.adimsayar.data.UpdateInstaller
import com.dordeorz.adimsayar.data.UpdateResult
import com.dordeorz.adimsayar.data.UpdateWatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** The check, download and install steps of an update, shared by the Settings row and the Today card. */
@Stable
internal class UpdateFlow(private val context: Context, private val scope: CoroutineScope, known: UpdateResult.Available?) {

    var checking by mutableStateOf(false)
        private set
    var result by mutableStateOf<UpdateResult?>(known)
        private set
    var progress by mutableStateOf<Int?>(null)
        private set
    var apk by mutableStateOf<File?>(null)
        private set
    var downloadFailed by mutableStateOf(false)
        private set
    var needsPermission by mutableStateOf(false)
        private set
    var confirmDownload by mutableStateOf(false)

    val available: UpdateResult.Available? get() = result as? UpdateResult.Available
    val busy: Boolean get() = checking || progress != null

    /** Opens the release page instead of downloading: no APK in the release, or the download failed. */
    val opensPage: Boolean get() = available.let { it != null && apk == null && (downloadFailed || it.apkUrl == null) }

    /** One tap: install a downloaded file, else download (after the consent dialog) or open the page, else check. */
    fun tap(currentVersion: String, openUri: (String) -> Unit) {
        val file = apk
        val release = available
        when {
            busy -> Unit
            file != null -> install(file)
            release != null && opensPage -> runCatching { openUri(release.url) }
            release != null -> confirmDownload = true
            else -> scope.launch {
                checking = true
                val found = UpdateChecker.check(currentVersion)
                UpdateWatch.get(context).record(found)
                result = found
                checking = false
            }
        }
    }

    fun download() {
        confirmDownload = false
        val release = available ?: return
        val url = release.apkUrl ?: return
        scope.launch {
            progress = 0
            val downloaded = UpdateInstaller.download(context, url, release.version) { percent ->
                scope.launch { progress = percent }
            }
            progress = null
            apk = downloaded
            downloadFailed = downloaded == null
            if (downloaded != null) install(downloaded)
        }
    }

    private fun install(file: File) {
        val start = UpdateInstaller.install(context, file)
        needsPermission = start == InstallStart.NeedsPermission
        if (start == InstallStart.Failed) {
            apk = null
            downloadFailed = true
        }
    }

    /** The line for a running check or download, or null when nothing is going on. */
    @Composable
    fun stepText(): String? = when {
        checking -> stringResource(R.string.update_checking)
        progress != null -> stringResource(R.string.update_downloading, progress ?: 0)
        needsPermission -> stringResource(R.string.update_install_permission)
        apk != null -> stringResource(R.string.update_ready)
        downloadFailed -> stringResource(R.string.update_download_failed)
        else -> null
    }

    @Composable
    fun statusText(): String = stepText() ?: when (val found = result) {
        is UpdateResult.Available -> stringResource(R.string.update_available, found.version)
        UpdateResult.UpToDate -> stringResource(R.string.update_up_to_date)
        UpdateResult.NoRelease -> stringResource(R.string.update_no_release)
        UpdateResult.Failed -> stringResource(R.string.update_failed)
        null -> stringResource(R.string.check_updates_hint)
    }
}

@Composable
internal fun rememberUpdateFlow(known: UpdateResult.Available?): UpdateFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(known?.version) { UpdateFlow(context, scope, known) }
}

@Composable
internal fun UpdateConsentDialog(flow: UpdateFlow) {
    if (!flow.confirmDownload || flow.available?.apkUrl == null) return
    AlertDialog(
        onDismissRequest = { flow.confirmDownload = false },
        title = { Text(stringResource(R.string.update_consent_title)) },
        text = { Text(stringResource(R.string.update_consent_text)) },
        confirmButton = {
            TextButton(onClick = flow::download) { Text(stringResource(R.string.update_consent_download)) }
        },
        dismissButton = {
            TextButton(onClick = { flow.confirmDownload = false }) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** What the Today page shows about updates: the one-time question, a new release, or nothing. */
internal sealed interface UpdateNotice {
    data object Ask : UpdateNotice
    data class Release(val release: UpdateResult.Available) : UpdateNotice
}

@Composable
internal fun rememberUpdateNotice(state: MainUiState): UpdateNotice? {
    val watch = UpdateWatch.get(LocalContext.current)
    val latest by watch.latest.collectAsStateWithLifecycle()
    val dismissed by watch.dismissed.collectAsStateWithLifecycle()
    val extras = state.extra.extras
    if (!extras.updateAsked) return UpdateNotice.Ask
    if (!extras.updateWatch) return null
    return UpdateWatch.pending(latest, dismissed, state.version)?.let { UpdateNotice.Release(it) }
}

@Composable
internal fun UpdateNoticeCard(notice: UpdateNotice, state: MainUiState, actions: MainActions) {
    when (notice) {
        UpdateNotice.Ask -> UpdateAskCard(onAnswer = actions.extra::setUpdateWatch)
        is UpdateNotice.Release -> UpdateReleaseCard(notice.release, state.version)
    }
}

@Composable
private fun UpdateAskCard(onAnswer: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UpdateCardTitle(stringResource(R.string.update_ask_title), MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.update_ask_text), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.update_ask_no)) }
                Button(onClick = { onAnswer(true) }) { Text(stringResource(R.string.update_ask_yes)) }
            }
        }
    }
}

@Composable
private fun UpdateReleaseCard(release: UpdateResult.Available, currentVersion: String) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val flow = rememberUpdateFlow(release)
    val onColor = MaterialTheme.colorScheme.onSecondaryContainer
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UpdateCardTitle(stringResource(R.string.update_new_title, release.version), onColor)
            Text(stringResource(R.string.update_card_text, currentVersion), style = MaterialTheme.typography.bodyMedium, color = onColor)
            flow.stepText()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = onColor) }
            Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { UpdateWatch.get(context).dismiss(release.version) }, enabled = !flow.busy) {
                    Text(stringResource(R.string.update_card_later))
                }
                Button(onClick = { flow.tap(currentVersion) { uriHandler.openUri(it) } }, enabled = !flow.busy) {
                    Text(
                        stringResource(
                            when {
                                flow.apk != null -> R.string.update_card_install
                                flow.opensPage -> R.string.github_page
                                else -> R.string.update_card_update
                            },
                        ),
                    )
                }
            }
        }
    }
    UpdateConsentDialog(flow)
}

@Composable
private fun UpdateCardTitle(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_sync), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = color)
    }
}
