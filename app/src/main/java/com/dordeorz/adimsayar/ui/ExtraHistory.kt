package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.DayNotes
import com.dordeorz.adimsayar.data.DayOverrides
import java.time.LocalDate

/** The note and the hand correction of the selected day. */
@Composable
internal fun DayExtraRows(date: LocalDate, steps: Long, state: MainUiState, actions: MainActions) {
    var editNote by rememberSaveable(date.toString()) { mutableStateOf(false) }
    var editSteps by rememberSaveable(date.toString()) { mutableStateOf(false) }
    val note = state.extra.notes[date]
    val corrected = date in state.extra.overrides
    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_note),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            note ?: stringResource(R.string.note_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = if (note == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        TextButton(onClick = { editNote = true }) {
            Text(stringResource(if (note == null) R.string.note_add else R.string.note_edit))
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_edit),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            stringResource(if (corrected) R.string.correct_done else R.string.correct_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        TextButton(onClick = { editSteps = true }) { Text(stringResource(R.string.correct_button)) }
    }
    if (editNote) NoteDialog(note.orEmpty(), onSave = { actions.extra.setNote(date, it) }, onDismiss = { editNote = false })
    if (editSteps) {
        CorrectDialog(
            steps = steps,
            corrected = corrected,
            onSave = { actions.extra.setOverride(date, it) },
            onDismiss = { editSteps = false },
        )
    }
}

@Composable
private fun NoteDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(DayNotes.MAX_LENGTH) },
                placeholder = { Text(stringResource(R.string.note_placeholder)) },
                supportingText = { Text("${text.length}/${DayNotes.MAX_LENGTH}") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(text)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (current.isNotEmpty()) {
                    TextButton(onClick = {
                        onSave("")
                        onDismiss()
                    }) { Text(stringResource(R.string.delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun CorrectDialog(steps: Long, corrected: Boolean, onSave: (Long?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(steps.toString()) }
    val value = text.toLongOrNull()?.takeIf { it in 0L..DayOverrides.MAX_STEPS }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.correct_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.correct_text), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter(Char::isDigit).take(DayOverrides.MAX_STEPS.toString().length) },
                    singleLine = true,
                    isError = value == null,
                    suffix = { Text(stringResource(R.string.steps_unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(value)
                    onDismiss()
                },
                enabled = value != null,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (corrected) {
                    TextButton(onClick = {
                        onSave(null)
                        onDismiss()
                    }) { Text(stringResource(R.string.correct_undo)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
