package com.dordeorz.adimsayar.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.DesignBird
import com.dordeorz.adimsayar.data.DesignPlace
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

private val STYLE_NAMES = mapOf(
    "bold" to R.string.face_style_bold,
    "light" to R.string.face_style_light,
    "italic" to R.string.face_style_italic,
    "serif" to R.string.face_style_serif,
    "mono" to R.string.face_style_mono,
    "outline" to R.string.face_style_outline,
    "lcd" to R.string.face_style_lcd,
)

/** Mi Band 8 face pictures are 192 x 490. */
private const val DESIGN_RATIO = 192f / 490f

/**
 * The face designer: themes, background (colours or a photo moved and zoomed with the fingers),
 * digit style and colours, where the time sits, which parts show, and the bird. Closes once an
 * upload starts; the faces list shows its progress.
 */
@Composable
internal fun FaceDesignerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var design by remember { mutableStateOf(FaceDesigner.saved(context)) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var previewHeight by remember { mutableIntStateOf(1) }
    val failed = stringResource(R.string.face_designer_failed)
    val name = stringResource(R.string.face_designer_name)
    val styles = remember { FaceDesigner.styles(context) }
    LaunchedEffect(Unit) {
        if (design.photo) photo = FaceDesigner.savedPhoto(context) ?: run {
            design = design.copy(photo = false)
            null
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                FaceDesigner.takePhoto(context, uri)?.let {
                    photo = it
                    design = design.copy(photo = true, zoom = 1f, panX = 0f, panY = 0f)
                }
            }
        }
    }
    val picture by produceState<Bitmap?>(null, design, photo) { value = FaceDesigner.picture(context, design, photo) }
    val current by rememberUpdatedState(design)
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
                        Modifier
                            .height(320.dp)
                            .aspectRatio(DESIGN_RATIO)
                            .clip(MaterialTheme.shapes.large)
                            .background(Color.Black)
                            .onSizeChanged { previewHeight = it.height.coerceAtLeast(1) }
                            .pointerInput(photo) {
                                val image = photo ?: return@pointerInput
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val now = current
                                    if (!now.photo) return@detectTransformGestures
                                    val perPixel = FaceDesigner.H.toFloat() / previewHeight
                                    val z = (now.zoom * zoom).coerceIn(1f, FaceDesigner.ZOOM_MAX)
                                    val (_, x, y) = FaceDesigner.photoPlace(image, z, now.panX + pan.x * perPixel, now.panY + pan.y * perPixel)
                                    design = now.copy(zoom = z, panX = x, panY = y)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        picture?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
                            ?: CircularProgressIndicator()
                    }
                }

                Section(stringResource(R.string.face_designer_theme))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FaceDesigner.THEMES.forEach { theme ->
                        ThemeTile(theme) {
                            design = design.copy(
                                top = theme.top, bottom = theme.bottom, hour = theme.hour, minute = theme.minute,
                                text = theme.text, accent = theme.accent, photo = false,
                            )
                        }
                    }
                }

                Section(stringResource(R.string.face_designer_style))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    styles.forEach { style ->
                        StyleTile(style, chosen = design.style == style, hour = design.hour) { design = design.copy(style = style) }
                    }
                }

                Section(stringResource(R.string.face_designer_place))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        DesignPlace.Top to R.string.face_designer_place_top,
                        DesignPlace.Middle to R.string.face_designer_place_middle,
                        DesignPlace.Bottom to R.string.face_designer_place_bottom,
                    ).forEach { (place, label) ->
                        FilterChip(selected = design.place == place, onClick = { design = design.copy(place = place) }, label = { Text(stringResource(label)) })
                    }
                }

                Section(stringResource(R.string.face_designer_background))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GRADIENTS.forEach { (top, bottom) ->
                        val chosen = !design.photo && design.top == top && design.bottom == bottom
                        Box(
                            Modifier
                                .size(width = 30.dp, height = 48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(Brush.verticalGradient(listOf(Color(top), Color(bottom))))
                                .border(outline(chosen), MaterialTheme.shapes.small)
                                .clickable { design = design.copy(top = top, bottom = bottom, photo = false) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = {
                        runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    }) { Text(stringResource(R.string.face_designer_photo)) }
                    if (design.photo) {
                        TextButton(onClick = { design = design.copy(photo = false) }) { Text(stringResource(R.string.face_designer_photo_remove)) }
                    }
                }
                if (design.photo) {
                    Hint(stringResource(R.string.face_designer_photo_hint))
                    Text(stringResource(R.string.face_designer_zoom), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = design.zoom,
                        onValueChange = { z ->
                            val image = photo ?: return@Slider
                            val (_, x, y) = FaceDesigner.photoPlace(image, z, design.panX, design.panY)
                            design = design.copy(zoom = z, panX = x, panY = y)
                        },
                        valueRange = 1f..FaceDesigner.ZOOM_MAX,
                    )
                }
                Text(stringResource(R.string.face_designer_dim), style = MaterialTheme.typography.bodyMedium)
                Slider(value = design.dim.toFloat(), onValueChange = { design = design.copy(dim = it.toInt()) }, valueRange = 0f..80f)

                Section(stringResource(R.string.face_designer_hour))
                Swatches(design.hour) { design = design.copy(hour = it) }
                Section(stringResource(R.string.face_designer_minute))
                Swatches(design.minute) { design = design.copy(minute = it) }
                Section(stringResource(R.string.face_designer_text))
                Swatches(design.text) { design = design.copy(text = it) }
                Section(stringResource(R.string.face_designer_accent))
                Swatches(design.accent) { design = design.copy(accent = it) }

                Section(stringResource(R.string.face_designer_show))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(design.showDate, { design = design.copy(showDate = !design.showDate) }, { Text(stringResource(R.string.face_designer_show_date)) })
                    FilterChip(design.showSteps, { design = design.copy(showSteps = !design.showSteps) }, { Text(stringResource(R.string.face_designer_show_steps)) })
                    FilterChip(design.showBar, { design = design.copy(showBar = !design.showBar) }, { Text(stringResource(R.string.face_designer_show_bar)) })
                    FilterChip(design.showHealth, { design = design.copy(showHealth = !design.showHealth) }, { Text(stringResource(R.string.face_designer_show_health)) })
                }

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
private fun outline(chosen: Boolean) =
    BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** A small swatch of a theme: its background with the hour and minute colours as dots. */
@Composable
private fun ThemeTile(theme: FaceDesign, onPick: () -> Unit) {
    Box(
        Modifier
            .size(width = 40.dp, height = 64.dp)
            .clip(MaterialTheme.shapes.small)
            .background(Brush.verticalGradient(listOf(Color(theme.top), Color(theme.bottom))))
            .border(outline(false), MaterialTheme.shapes.small)
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(theme.hour, theme.minute, theme.accent).forEach { color ->
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(color)))
            }
        }
    }
}

/** A digit style shown as "12" in that style. */
@Composable
private fun StyleTile(style: String, chosen: Boolean, hour: Int, onPick: () -> Unit) {
    val context = LocalContext.current
    val sample by produceState<Bitmap?>(null, style) {
        value = runCatching {
            context.assets.open("designer/digits_$style.png").use(BitmapFactory::decodeStream)?.let { strip ->
                val w = strip.width / 10
                Bitmap.createBitmap(w * 2, strip.height, Bitmap.Config.ARGB_8888).also { out ->
                    val canvas = android.graphics.Canvas(out)
                    canvas.drawBitmap(Bitmap.createBitmap(strip, w, 0, w, strip.height), 0f, 0f, null)
                    canvas.drawBitmap(Bitmap.createBitmap(strip, 2 * w, 0, w, strip.height), w.toFloat(), 0f, null)
                }
            }
        }.getOrNull()
    }
    Column(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color.Black)
            .border(outline(chosen), MaterialTheme.shapes.small)
            .clickable(onClick = onPick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        sample?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(hour)),
                modifier = Modifier.height(32.dp),
            )
        }
        Text(
            stringResource(STYLE_NAMES[style] ?: R.string.face_style_bold),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun Swatches(selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        COLORS.forEach { color ->
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(outline(color == selected), CircleShape)
                    .clickable { onPick(color) },
            )
        }
    }
}
