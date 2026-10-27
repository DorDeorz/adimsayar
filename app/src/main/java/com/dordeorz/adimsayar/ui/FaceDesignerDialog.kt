package com.dordeorz.adimsayar.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.DesignBird
import com.dordeorz.adimsayar.data.DesignPlace
import com.dordeorz.adimsayar.data.FaceDesign
import com.dordeorz.adimsayar.data.FaceDesigner
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

/** The designer's pages, one tab each. */
private enum class DesignTab(val label: Int) {
    Theme(R.string.face_designer_tab_theme),
    Time(R.string.face_designer_tab_time),
    Background(R.string.face_designer_background),
    Colors(R.string.face_designer_tab_colors),
    Parts(R.string.face_designer_tab_parts),
}

/** Mi Band 8 face pictures are 192 x 490. */
private const val DESIGN_RATIO = 192f / 490f

/** Changes closer together than this, like one finger drag, are undone in one step. */
private const val UNDO_GAP_MS = 700L

/**
 * The face designer: the preview stays on top, the options sit in tabs below it and the upload
 * button stays at the bottom. Changes can be undone one by one or all reset. Closes once an upload
 * starts; the faces list shows its progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FaceDesignerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var design by remember { mutableStateOf(FaceDesigner.saved(context)) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var previewHeight by remember { mutableIntStateOf(1) }
    var tab by remember { mutableStateOf(DesignTab.Theme) }
    val history = remember { mutableStateListOf<FaceDesign>() }
    var lastChange by remember { mutableLongStateOf(0L) }
    val failed = stringResource(R.string.face_designer_failed)
    val name = stringResource(R.string.face_designer_name)
    val styles = remember { FaceDesigner.styles(context) }

    fun change(next: FaceDesign) {
        if (next == design) return
        val now = SystemClock.uptimeMillis()
        if (now - lastChange > UNDO_GAP_MS || history.isEmpty()) history.add(design)
        lastChange = now
        design = next
    }

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
                    change(design.copy(photo = true, zoom = 1f, panX = 0f, panY = 0f))
                }
            }
        }
    }
    val picture by produceState<Bitmap?>(null, design, photo) { value = FaceDesigner.picture(context, design, photo) }
    val current by rememberUpdatedState(design)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.face_designer), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { history.removeLastOrNull()?.let { design = it } }, enabled = history.isNotEmpty()) {
                        Text(stringResource(R.string.face_designer_undo))
                    }
                    TextButton(onClick = { change(FaceDesigner.DEFAULT) }, enabled = design != FaceDesigner.DEFAULT) {
                        Text(stringResource(R.string.face_designer_reset))
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .height(250.dp)
                            .aspectRatio(DESIGN_RATIO)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.Black)
                            .border(BorderStroke(3.dp, Color(0xFF2A2A2A)), RoundedCornerShape(28.dp))
                            .onSizeChanged { previewHeight = it.height.coerceAtLeast(1) }
                            .pointerInput(photo) {
                                val image = photo ?: return@pointerInput
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val now = current
                                    if (!now.photo) return@detectTransformGestures
                                    val perPixel = FaceDesigner.H.toFloat() / previewHeight
                                    val z = (now.zoom * zoom).coerceIn(1f, FaceDesigner.ZOOM_MAX)
                                    val (_, x, y) = FaceDesigner.photoPlace(image, z, now.panX + pan.x * perPixel, now.panY + pan.y * perPixel)
                                    change(now.copy(zoom = z, panX = x, panY = y))
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        picture?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
                            ?: CircularProgressIndicator()
                    }
                }

                PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp) {
                    DesignTab.entries.forEach { t ->
                        Tab(selected = tab == t, onClick = { tab = t }, text = { Text(stringResource(t.label)) })
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (tab) {
                        DesignTab.Theme -> ThemePage(design) { theme ->
                            change(
                                design.copy(
                                    top = theme.top, bottom = theme.bottom, hour = theme.hour, minute = theme.minute,
                                    text = theme.text, accent = theme.accent, photo = false,
                                ),
                            )
                        }
                        DesignTab.Time -> TimePage(design, styles) { change(it) }
                        DesignTab.Background -> BackgroundPage(
                            design,
                            photo,
                            onChange = { change(it) },
                            onPickPhoto = { runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
                        )
                        DesignTab.Colors -> ColorsPage(design) { change(it) }
                        DesignTab.Parts -> PartsPage(design) { change(it) }
                    }
                }

                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Button(
                        onClick = {
                            FaceDesigner.start(context, design, photo, name, failed)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_watch), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.face_designer_upload))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePage(design: FaceDesign, onPick: (FaceDesign) -> Unit) {
    Label(stringResource(R.string.face_designer_theme))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FaceDesigner.THEMES.forEach { theme ->
            val chosen = !design.photo && design.top == theme.top && design.bottom == theme.bottom &&
                design.hour == theme.hour && design.minute == theme.minute && design.accent == theme.accent
            ThemeTile(theme, chosen) { onPick(theme) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TimePage(design: FaceDesign, styles: List<String>, onChange: (FaceDesign) -> Unit) {
    Label(stringResource(R.string.face_designer_style))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        styles.forEach { style ->
            StyleTile(style, chosen = design.style == style, hour = design.hour) { onChange(design.copy(style = style)) }
        }
    }
    Label(stringResource(R.string.face_designer_place))
    val places = listOf(
        DesignPlace.Top to R.string.face_designer_place_top,
        DesignPlace.Middle to R.string.face_designer_place_middle,
        DesignPlace.Bottom to R.string.face_designer_place_bottom,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        places.forEachIndexed { i, (place, label) ->
            SegmentedButton(
                selected = design.place == place,
                onClick = { onChange(design.copy(place = place)) },
                shape = SegmentedButtonDefaults.itemShape(i, places.size),
            ) { Text(stringResource(label)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundPage(design: FaceDesign, photo: Bitmap?, onChange: (FaceDesign) -> Unit, onPickPhoto: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GRADIENTS.forEach { (top, bottom) ->
            val chosen = !design.photo && design.top == top && design.bottom == bottom
            Box(
                Modifier
                    .size(width = 44.dp, height = 72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.verticalGradient(listOf(Color(top), Color(bottom))))
                    .border(outline(chosen), RoundedCornerShape(14.dp))
                    .clickable { onChange(design.copy(top = top, bottom = bottom, photo = false)) },
                contentAlignment = Alignment.Center,
            ) {
                if (chosen) CheckMark(Color(top))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = onPickPhoto) { Text(stringResource(R.string.face_designer_photo)) }
        if (design.photo) {
            TextButton(onClick = { onChange(design.copy(photo = false)) }) { Text(stringResource(R.string.face_designer_photo_remove)) }
        }
    }
    if (design.photo) {
        Hint(stringResource(R.string.face_designer_photo_hint))
        ValueSlider(
            stringResource(R.string.face_designer_zoom),
            "%.1f×".format(design.zoom),
            design.zoom,
            1f..FaceDesigner.ZOOM_MAX,
        ) { z ->
            val image = photo ?: return@ValueSlider
            val (_, x, y) = FaceDesigner.photoPlace(image, z, design.panX, design.panY)
            onChange(design.copy(zoom = z, panX = x, panY = y))
        }
    }
    ValueSlider(stringResource(R.string.face_designer_dim), "%${design.dim}", design.dim.toFloat(), 0f..80f) {
        onChange(design.copy(dim = it.roundToInt()))
    }
}

@Composable
private fun ColorsPage(design: FaceDesign, onChange: (FaceDesign) -> Unit) {
    Label(stringResource(R.string.face_designer_hour))
    Swatches(design.hour) { onChange(design.copy(hour = it)) }
    Label(stringResource(R.string.face_designer_minute))
    Swatches(design.minute) { onChange(design.copy(minute = it)) }
    Label(stringResource(R.string.face_designer_text))
    Swatches(design.text) { onChange(design.copy(text = it)) }
    Label(stringResource(R.string.face_designer_accent))
    Swatches(design.accent) { onChange(design.copy(accent = it)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartsPage(design: FaceDesign, onChange: (FaceDesign) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 4.dp)) {
            SwitchRow(R.drawable.ic_calendar, R.string.face_designer_show_date, design.showDate) { onChange(design.copy(showDate = it)) }
            SwitchRow(R.drawable.ic_walk, R.string.face_designer_show_steps, design.showSteps) { onChange(design.copy(showSteps = it)) }
            SwitchRow(R.drawable.ic_trophy, R.string.face_designer_show_bar, design.showBar) { onChange(design.copy(showBar = it)) }
            SwitchRow(R.drawable.ic_heart, R.string.face_designer_show_health, design.showHealth) { onChange(design.copy(showHealth = it)) }
        }
    }
    Label(stringResource(R.string.face_designer_bird))
    val birds = listOf(
        DesignBird.Happy to R.string.face_designer_bird_happy,
        DesignBird.Sleepy to R.string.face_designer_bird_sleepy,
        DesignBird.None to R.string.face_designer_bird_none,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        birds.forEachIndexed { i, (bird, label) ->
            SegmentedButton(
                selected = design.bird == bird,
                onClick = { onChange(design.copy(bird = bird)) },
                shape = SegmentedButtonDefaults.itemShape(i, birds.size),
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun SwitchRow(icon: Int, label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = current, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun outline(chosen: Boolean) =
    BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun Label(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A check mark that reads on [behind]. */
@Composable
private fun CheckMark(behind: Color) {
    Icon(
        painterResource(R.drawable.ic_check),
        contentDescription = null,
        tint = if (behind.luminance() > 0.5f) Color.Black else Color.White,
        modifier = Modifier.size(20.dp),
    )
}

/** A small face of a theme: its background, a sample time in its colours and its goal bar. */
@Composable
private fun ThemeTile(theme: FaceDesign, chosen: Boolean, onPick: () -> Unit) {
    Column(
        Modifier
            .size(width = 64.dp, height = 104.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(theme.top), Color(theme.bottom))))
            .border(outline(chosen), RoundedCornerShape(16.dp))
            .clickable(onClick = onPick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("09", color = Color(theme.hour), fontSize = 20.sp, fontWeight = FontWeight.Black, lineHeight = 20.sp)
        Text("41", color = Color(theme.minute), fontSize = 20.sp, fontWeight = FontWeight.Black, lineHeight = 20.sp)
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color(theme.accent)),
        )
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
            .width(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111111))
            .border(outline(chosen), RoundedCornerShape(16.dp))
            .clickable(onClick = onPick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            sample?.let {
                Image(it.asImageBitmap(), contentDescription = null, colorFilter = ColorFilter.tint(Color(hour)), modifier = Modifier.height(36.dp))
            }
        }
        Text(
            stringResource(STYLE_NAMES[style] ?: R.string.face_style_bold),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        COLORS.forEach { color ->
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(outline(color == selected), CircleShape)
                    .clickable { onPick(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (color == selected) CheckMark(Color(color))
            }
        }
    }
}
