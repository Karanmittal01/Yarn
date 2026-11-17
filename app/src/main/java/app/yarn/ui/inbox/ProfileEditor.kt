package app.yarn.ui.inbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.yarn.R
import app.yarn.ui.common.Avatar
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where Yarn keeps your profile picture: one small square JPEG in its private files. */
object ProfilePhoto {
    private const val SIZE = 512

    /** Saves [bitmap] as the new picture and returns its URI; older pictures are removed. */
    fun save(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "profile").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        // A new name each time, so cached copies of the old picture are never shown.
        val file = File(dir, "me_${System.currentTimeMillis()}.jpg")
        val square = if (bitmap.width == SIZE && bitmap.height == SIZE) bitmap else Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
        file.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file).toString()
    }

    fun clear(context: Context) {
        File(context.filesDir, "profile").listFiles()?.forEach { it.delete() }
    }

    /** Reads a picked photo the right way up, scaled down so huge camera photos stay light. */
    fun load(context: Context, uri: Uri): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val largest = max(info.size.width, info.size.height)
            if (largest > 2048) decoder.setTargetSampleSize((largest / 2048f).roundToInt().coerceAtLeast(1))
        }
    }.getOrNull()

    /** Cuts [source]'s square [crop] (in the bitmap's pixels) out as the profile picture. */
    fun crop(source: Bitmap, crop: Rect): Bitmap {
        val x = crop.left.roundToInt().coerceIn(0, source.width - 1)
        val y = crop.top.roundToInt().coerceIn(0, source.height - 1)
        val side = crop.width.roundToInt().coerceAtMost(min(source.width - x, source.height - y)).coerceAtLeast(1)
        return Bitmap.createBitmap(source, x, y, side, side)
    }
}

/**
 * Your name and picture, as Yarn shows them. The picture is chosen with the system photo picker
 * (no storage permission) and cropped to a circle in [PhotoCropper].
 */
@Composable
fun ProfileEditor(
    name: String,
    photo: String?,
    onSave: (name: String, photo: Bitmap?, removePhoto: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draftName by remember { mutableStateOf(name) }
    var newPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var removed by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch(app.yarn.CrashLog.handler) {
            cropping = withContext(Dispatchers.IO) { ProfilePhoto.load(context, uri) }
        }
    }
    val source = cropping
    if (source != null) {
        PhotoCropper(source, onCancel = { cropping = null }, onDone = { newPhoto = it; removed = false; cropping = null })
        return
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.edit_profile), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
            TextButton(onClick = { onSave(draftName.trim(), newPhoto, removed) }) { Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(28.dp))
        val changeLabel = stringResource(R.string.change_photo)
        Box(Modifier.size(132.dp)) {
            Box(
                Modifier.matchParentSize().clip(CircleShape).clickable(onClickLabel = changeLabel) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                contentAlignment = Alignment.Center,
            ) {
                val preview = newPhoto
                when {
                    preview != null -> androidx.compose.foundation.Image(preview.asImageBitmap(), null, Modifier.size(132.dp), contentScale = ContentScale.Crop)
                    !removed && photo != null -> AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(132.dp))
                    else -> MyAvatar(draftName, null, 132.dp)
                }
            }
            // A small camera badge says the picture can be changed; it sits on the circle's edge, not inside it.
            Box(
                Modifier.align(Alignment.BottomEnd)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(app.yarn.ui.settings.settingsPageColor())
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary) }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(changeLabel) }
            if (newPhoto != null || (!removed && photo != null)) {
                TextButton(onClick = { newPhoto = null; removed = true }) { Text(stringResource(R.string.remove_photo), color = MaterialTheme.colorScheme.error) }
            }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            draftName, { draftName = it.take(60) },
            label = { Text(stringResource(R.string.your_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.profile_private_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp),
        )
    }
}

/**
 * Pinch to zoom and drag to place the photo inside the circle; everything outside is dimmed.
 * The picture always covers the circle, so the result never has empty edges.
 */
@Composable
fun PhotoCropper(source: Bitmap, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val image: ImageBitmap = remember(source) { source.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val w = source.width.toFloat()
    val h = source.height.toFloat()

    fun radius() = min(area.width, area.height) * 0.42f
    fun baseScale() = (radius() * 2) / min(w, h)
    /** Keeps the photo covering the circle wherever it is dragged. */
    fun clamp(o: Offset, z: Float): Offset {
        val s = baseScale() * z
        val maxX = max(0f, (w * s / 2) - radius())
        val maxY = max(0f, (h * s / 2) - radius())
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Text(
            stringResource(R.string.crop_hint), style = MaterialTheme.typography.titleMedium, color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { area = it }
                .pointerInput(source) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                        offset = clamp(offset + pan, zoom)
                    }
                },
        ) {
            val s = baseScale() * zoom
            val drawn = Size(w * s, h * s)
            val topLeft = center - Offset(drawn.width / 2, drawn.height / 2) + offset
            drawImage(
                image,
                dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                dstSize = IntSize(drawn.width.roundToInt(), drawn.height.roundToInt()),
            )
            val r = radius()
            val hole = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addOval(Rect(center, r))
            }
            drawPath(hole, Color.Black.copy(alpha = 0.62f))
            drawCircle(Color.White.copy(alpha = 0.9f), r, center, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel), color = Color.White) }
            Button(onClick = {
                if (area.width == 0) return@Button
                val s = baseScale() * zoom
                val center = Offset(area.width / 2f, area.height / 2f)
                val topLeft = center - Offset(w * s / 2, h * s / 2) + offset
                val r = radius()
                // The circle's square, mapped from the screen back into the photo's own pixels.
                val crop = Rect(
                    left = (center.x - r - topLeft.x) / s, top = (center.y - r - topLeft.y) / s,
                    right = (center.x + r - topLeft.x) / s, bottom = (center.y + r - topLeft.y) / s,
                )
                onDone(ProfilePhoto.crop(source, crop))
            }) { Text(stringResource(R.string.use_photo)) }
        }
    }
}
