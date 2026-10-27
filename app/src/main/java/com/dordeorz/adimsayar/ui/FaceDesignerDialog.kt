package com.dordeorz.adimsayar.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.DesignBird
import com.dordeorz.adimsayar.data.FaceDesign
import com.dordeorz.adimsayar.data.FaceDesigner
import kotlinx.coroutines.launch

private val GRADIENTS = listOf(
    0xFF1E172E to 0xFF07050D,
    0xFF0B102E to 0xFF02030A,
    0xFF000000 to 0xFF000000,
    0xFF12324A to 0xFF0A1420,
    0xFF1B3A2A to 0xFF07130C,
    0xFF4A1A2C to 0xFF12060B,
    0xFFFFF1D6 to 0xFFFFD3C2,
    0xFFB3E5FC to 0xFF4FC3F7,
).map { (a, b) -> a.toInt() to b.toInt() }

private val COLORS = listOf(
    0xFFFFFFFF, 0xFFF2E6D8, 0xFF3B2A20, 0xFFFFC93C, 0xFFFFB06A, 0xFFFF8A3D, 0xFFEF5350, 0xFFFF4FC3,
    0xFFB388FF, 0xFF9CB4FF, 0xFF29E6FF, 0xFF81C784, 0xFF9CE65A, 0xFF9E9E9E,
).map { it.toInt() }

/** Mi Band 8 face pictures are 192 x 490. */
private const val DESIGN_RATIO = 192f / 490f

/** The face designer: background, colours and the bird, then the face goes to the band. Closes once an upload starts. */
@Composable
internal fun FaceDesignerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var design by remember { mutableStateOf(FaceDesigner.saved(context)) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    val failed = stringResource(R.string.face_designer_failed)
    val name = stringResource(R.string.face_designer_name)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { FaceDesigner.photo(context, uri)?.let { photo = it } }
    }
    val picture by produceState<Bitmap?>(null, design, photo) { value = FaceDesigner.picture(context, design, photo) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.face_designer), style = MaterialTheme.typography.titleLarge)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.height(300.dp).aspectRatio(DESIGN_RATIO).clip(MaterialTheme.shapes.large).background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        picture?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
                            ?: CircularProgressIndicator()
                    }
                }
                Section(stringResource(R.string.face_designer_background))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GRADIENTS.forEach { (top, bottom) ->
                        val chosen = photo == null && design.top == top && design.bottom == bottom
                        Box(
                            Modifier
                                .size(width = 30.dp, height = 48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(Brush.verticalGradient(listOf(Color(top), Color(bottom))))
                                .border(
                                    BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                    MaterialTheme.shapes.small,
                                )
                                .clickable {
                                    photo = null
                                    design = design.copy(top = top, bottom = bottom)
                                },
                        )
                    }
                    OutlinedButton(onClick = {
                        runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    }) { Text(stringResource(R.string.face_designer_photo)) }
                }
                Section(stringResource(R.string.face_designer_hour))
                Swatches(design.hour) { design = design.copy(hour = it) }
                Section(stringResource(R.string.face_designer_minute))
                Swatches(design.minute) { design = design.copy(minute = it) }
                Section(stringResource(R.string.face_designer_text))
                Swatches(design.text) { design = design.copy(text = it) }
                Section(stringResource(R.string.face_designer_accent))
                Swatches(design.accent) { design = design.copy(accent = it) }
                Section(stringResource(R.string.face_designer_bird))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        DesignBird.Happy to R.string.face_designer_bird_happy,
                        DesignBird.Sleepy to R.string.face_designer_bird_sleepy,
                        DesignBird.None to R.string.face_designer_bird_none,
                    ).forEach { (bird, label) ->
                        FilterChip(selected = design.bird == bird, onClick = { design = design.copy(bird = bird) }, label = { Text(stringResource(label)) })
                    }
                }
                Button(
                    onClick = {
                        // the faces list shows the upload's progress
                        FaceDesigner.start(context, design, photo, name, failed)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                ) { Text(stringResource(R.string.face_designer_upload)) }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

@Composable
private fun Swatches(selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        COLORS.forEach { color ->
            val chosen = color == selected
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        CircleShape,
                    )
                    .clickable { onPick(color) },
            )
        }
    }
}
