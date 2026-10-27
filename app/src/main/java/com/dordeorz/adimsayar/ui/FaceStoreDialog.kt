package com.dordeorz.adimsayar.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.BandFaces
import com.dordeorz.adimsayar.data.FaceStore
import com.dordeorz.adimsayar.data.OwnFace
import com.dordeorz.adimsayar.data.OwnFaces
import com.dordeorz.adimsayar.data.StoreFace
import kotlinx.coroutines.launch
import java.util.Locale

/** Mi Band 8 face pictures are 192 x 490. */
private const val FACE_RATIO = 192f / 490f

/** Xiaomi's free watch faces: search, look at one, and send it to the band. Closes once an upload starts. */
@Composable
internal fun FaceStoreDialog(model: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var faces by remember { mutableStateOf<List<StoreFace>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var label by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<StoreFace?>(null) }
    LaunchedEffect(model) {
        FaceStore.load(model) { faces = it }
        loading = false
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.face_store), style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    stringResource(R.string.face_store_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.face_store_search)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                val labels = faces.map { it.label }.filter { it.isNotEmpty() }.distinct().sorted()
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = label == null, onClick = { label = null }, label = { Text(stringResource(R.string.face_store_all)) })
                    labels.forEach { name ->
                        FilterChip(selected = label == name, onClick = { label = if (label == name) null else name }, label = { Text(name) })
                    }
                }
                val needle = query.trim().lowercase(Locale.ROOT)
                val shown = faces.filter { face ->
                    (label == null || face.label == label) &&
                        (needle.isEmpty() || needle in face.name.lowercase(Locale.ROOT) || needle in face.label.lowercase(Locale.ROOT))
                }
                Text(
                    when {
                        loading && faces.isEmpty() -> stringResource(R.string.face_store_loading)
                        !loading && faces.isEmpty() -> stringResource(R.string.face_store_offline)
                        else -> stringResource(R.string.face_store_count, shown.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.id }) { face -> StoreTile(face) { open = face } }
                }
            }
        }
    }
    open?.let { face ->
        StoreFaceDialog(
            face = face,
            onDismiss = { open = null },
            onInstall = { picture ->
                scope.launch {
                    val file = FaceStore.file(face)
                    if (file == null || !BandFaces.install(context, file, picture, face.name)) {
                        Toast.makeText(context, R.string.face_store_failed, Toast.LENGTH_LONG).show()
                        open = null
                    } else {
                        open = null
                        onDismiss()
                    }
                }
            },
        )
    }
}

@Composable
private fun StoreTile(face: StoreFace, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FacePicture(face, Modifier.fillMaxWidth())
        Text(
            face.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun FacePicture(face: StoreFace, modifier: Modifier, onLoaded: (Bitmap?) -> Unit = {}) {
    val context = LocalContext.current
    val picture by produceState<Bitmap?>(null, face.id) {
        value = FaceStore.icon(context, face)
        onLoaded(value)
    }
    Box(
        modifier = modifier.aspectRatio(FACE_RATIO).clip(MaterialTheme.shapes.medium).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val image = picture
        if (image != null) {
            Image(image.asImageBitmap(), contentDescription = face.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun StoreFaceDialog(face: StoreFace, onDismiss: () -> Unit, onInstall: (Bitmap?) -> Unit) {
    var picture by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(face.name) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FacePicture(face, Modifier.heightIn(max = 300.dp)) { picture = it }
                val detail = listOfNotNull(
                    face.label.takeIf { it.isNotEmpty() },
                    stringResource(R.string.face_store_size, String.format(Locale.ROOT, "%.1f", face.size / 1_048_576.0)).takeIf { face.size > 0 },
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (face.intro.isNotEmpty()) Text(face.intro, style = MaterialTheme.typography.bodyMedium)
                if (busy) Text(stringResource(R.string.face_store_downloading), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                onInstall(picture)
            }) { Text(stringResource(R.string.band_faces_upload)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** FitWren's own faces that ship with the app. Closes once an upload starts. */
@Composable
internal fun OwnFacesDialog(faces: List<OwnFace>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<OwnFace?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.own_faces), style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    stringResource(R.string.own_faces_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(faces, key = { it.name }) { face ->
                        Column(
                            modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { open = face }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            OwnFacePicture(face, Modifier.fillMaxWidth())
                            Text(
                                face.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    open?.let { face ->
        var busy by remember(face) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) open = null },
            title = { Text(face.title) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    OwnFacePicture(face, Modifier.heightIn(max = 300.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val file = OwnFaces.file(context, face)
                        val picture = OwnFaces.preview(context, face)
                        open = null
                        if (file == null || !BandFaces.install(context, file, picture, face.title)) {
                            Toast.makeText(context, R.string.face_store_failed, Toast.LENGTH_LONG).show()
                        } else {
                            onDismiss()
                        }
                    }
                }) { Text(stringResource(R.string.band_faces_upload)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { open = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun OwnFacePicture(face: OwnFace, modifier: Modifier) {
    val context = LocalContext.current
    val picture by produceState<Bitmap?>(null, face.name) { value = OwnFaces.preview(context, face) }
    Box(
        modifier = modifier.aspectRatio(FACE_RATIO).clip(MaterialTheme.shapes.medium).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        picture?.let { Image(it.asImageBitmap(), contentDescription = face.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}
