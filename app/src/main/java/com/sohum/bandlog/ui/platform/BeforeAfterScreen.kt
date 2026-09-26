package com.sohum.bandlog.ui.platform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.PhotoV2
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * v2.13 before/after slider (spec §11, Pro): the two picked photos stacked, a vertical divider you
 * drag (or tap) to compare, with both dates, the weight change and the days between.
 */
@Composable
fun BeforeAfterScreen(vm: AppViewModel, pvm: PlatformViewModel, onBack: () -> Unit) {
    val p = palette
    val pair = PlatformNav.compare
    SubPage("Before / after", onBack) {
        if (pair == null) { Text("Pick two photos first.", color = p.muted); return@SubPage }
        ProGate(pvm, com.sohum.bandlog.util.Pro.Feature.BEFORE_AFTER) {
            val (before, after) = pair
            var a by remember { mutableStateOf<ImageBitmap?>(null) }
            var b by remember { mutableStateOf<ImageBitmap?>(null) }
            var loading by remember { mutableStateOf(true) }
            LaunchedEffect(before.id, after.id) {
                a = Api.fetchStorageBitmap("progress-photos", before.path)?.asImageBitmap()
                b = Api.fetchStorageBitmap("progress-photos", after.path)?.asImageBitmap()
                loading = false
            }
            var split by remember { mutableFloatStateOf(0.5f) }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(20.dp)).background(p.card2)
                        .semantics { contentDescription = "Before and after comparison. Drag to move the divider." }
                        .pointerInput(Unit) { detectTapGestures { o -> split = (o.x / size.width).coerceIn(0f, 1f) } }
                        .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> split = (change.position.x / size.width).coerceIn(0f, 1f) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) CircularProgressIndicator(color = p.ink)
                    Canvas(Modifier.fillMaxSize()) {
                        b?.let { drawCover(it) }
                        val x = size.width * split
                        a?.let { img -> clipRect(right = x) { drawCover(img) } }
                        drawRect(Color.White, Offset(x - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height))
                        drawCircle(Color.White, 16.dp.toPx(), Offset(x, size.height / 2))
                        drawCircle(Color.Black.copy(alpha = 0.55f), 5.dp.toPx(), Offset(x - 5.dp.toPx(), size.height / 2))
                        drawCircle(Color.Black.copy(alpha = 0.55f), 5.dp.toPx(), Offset(x + 5.dp.toPx(), size.height / 2))
                    }
                    Tag("Before · ${Dates.short(before.date)}", Modifier.align(Alignment.TopStart))
                    Tag("After · ${Dates.short(after.date)}", Modifier.align(Alignment.TopEnd))
                }
                val kgA = before.weightKg ?: nearestWeight(vm, before.date)
                val kgB = after.weightKg ?: nearestWeight(vm, after.date)
                val days = abs(Dates.daysBetween(before.date, after.date))
                Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Days between", "$days")
                        Stat(
                            "Weight change",
                            if (kgA != null && kgB != null) (kgB - kgA).let { d -> (if (d > 0) "+" else if (d < 0) "−" else "") + num1((abs(d) * 10).roundToInt() / 10.0) + " kg" } else "—",
                        )
                        Stat("Poses", "${PhotoV2.poseLabel(before.pose)} / ${PhotoV2.poseLabel(after.pose)}")
                    }
                    if (before.weightKg == null || after.weightKg == null) Text(
                        "Weight is from your nearest weigh-in when the photo has none.", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** The weigh-in closest in time to [date]. */
private fun nearestWeight(vm: AppViewModel, date: String): Double? =
    vm.weights.minByOrNull { abs(Dates.daysBetween(it.date, date)) }?.takeIf { abs(Dates.daysBetween(it.date, date)) <= 14 }?.weightKg

/** Center-crops [img] to fill the canvas, like ContentScale.Crop. */
private fun DrawScope.drawCover(img: ImageBitmap) {
    val scale = maxOf(size.width / img.width, size.height / img.height)
    val w = (size.width / scale).roundToInt().coerceAtMost(img.width)
    val h = (size.height / scale).roundToInt().coerceAtMost(img.height)
    val sx = (img.width - w) / 2
    val sy = (img.height - h) / 2
    drawImage(img, IntOffset(sx, sy), IntSize(w, h), dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
}

@Composable
private fun Tag(text: String, modifier: Modifier) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight(700), color = Color.White,
        modifier = modifier.padding(10.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Stat(label: String, value: String) {
    val p = palette
    Column {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight(700), color = p.ink)
        Text(label, fontSize = 12.sp, color = p.muted)
    }
}
