package com.sohum.bandlog.ui.squad

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.BattleLines
import com.sohum.bandlog.data.BattleRepo
import com.sohum.bandlog.data.PlateEstimate
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Avatar
import com.sohum.bandlog.ui.components.CameraIcon
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.scan.PhotoReview
import com.sohum.bandlog.ui.scan.decodeScaled
import com.sohum.bandlog.ui.scan.toJpegBase64
import com.sohum.bandlog.ui.scan.toJpegBytes
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Names
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Squad Food Battle (v2.7, docs/food-battle-spec.md): the live board + pinned crown card + the
 * Snap button, in its own tab on the squad page. Reuses the existing plate-photo estimate
 * ([Api.photoMeal]) and its review UI ([PhotoReview]) — nothing here re-derives vision/nutrition
 * logic, it only saves the confirmed meal and posts the enriched line to *this* squad.
 */
@Composable
fun BattleBoardTab(sq: SquadViewModel, vm: AppViewModel, groupId: String) {
    val p = palette
    val me = Session.userId
    var snapOpen by remember(groupId) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (sq.battleLoading && sq.battleBoard.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = p.muted) }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sq.battleCrown?.let { crown -> item { GraffitiCrownCard(crown) } }
                if (sq.battleBoard.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No one's logged today yet", fontSize = 17.sp, fontWeight = FontWeight(800), color = p.ink)
                            Text("Snap your first meal to open the board.", fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                } else {
                    items(sq.battleBoard, key = { it.userId }) { row ->
                        BattleRow(row, mine = row.userId == me, pointsToLead = BattleRepo.pointsToLead(row, sq.battleBoard))
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp).height(52.dp).pressable()
                .shadow(10.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow).background(p.btn, CircleShape)
                .clickable { snapOpen = true }.padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(CameraIcon, null, tint = p.btnInk, modifier = Modifier.size(18.dp))
            Text("  Snap", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.btnInk)
        }
    }

    if (snapOpen) SnapSheet(vm, groupId, onDismiss = { snapOpen = false; sq.refreshBattleBoard() })
}

@Composable
private fun BattleRow(row: BattleRepo.BattleRow, mine: Boolean, pointsToLead: Int?) {
    val p = palette
    val leader = pointsToLead == null && row.canWin
    Row(
        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = p.shadow, spotColor = p.shadow).background(p.card, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(Api.avatarUrl(row.avatarPath), Names.initials(row.name), 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name + if (mine) " (you)" else "", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (leader) Text(" 👑", fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.background(goalTint(row.goalType).copy(alpha = 0.14f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(row.goalLabel, fontSize = 10.sp, fontWeight = FontWeight(800), color = goalTint(row.goalType))
                }
            }
            if (row.private) {
                Text("Private", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(top = 4.dp))
            } else {
                Spacer(Modifier.height(6.dp))
                BandBar(row)
                Text(
                    "${row.eaten.toInt()} / ${row.target.toInt()} kcal · ${row.meals} meal${if (row.meals == 1) "" else "s"}" +
                        when { row.underFuelled -> "  ·  Under-fuelled" else -> "" },
                    fontSize = 11.sp, fontWeight = FontWeight(600),
                    color = if (row.underFuelled) p.orange else p.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(if (row.eligible) row.score.toInt().toString() else "–", fontSize = 18.sp, fontWeight = FontWeight(800), color = p.ink)
            if (!row.private) {
                Text(
                    when { !row.eligible -> "log 2+ meals"; leader -> "leading"; pointsToLead != null -> "$pointsToLead pts to lead"; else -> "" },
                    fontSize = 10.sp, color = p.muted, maxLines = 1,
                )
            }
        }
    }
}

/** The goal band shaded on eaten/target, like the web's board. */
@Composable
private fun BandBar(row: BattleRepo.BattleRow) {
    val p = palette
    val hi = when (row.goalType) { "gain" -> 1.10; "lose" -> 1.00; else -> 1.05 }
    val scaleMax = hi.coerceAtLeast(1.15)
    val bandEnd = (hi / scaleMax).coerceIn(0.0, 1.0).toFloat()
    val frac = (row.r / scaleMax).coerceIn(0.0, 1.0).toFloat()
    val tint = goalTint(row.goalType)
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.track)) {
        Box(Modifier.fillMaxWidth(bandEnd).height(8.dp).clip(RoundedCornerShape(4.dp)).background(tint.copy(alpha = 0.18f)))
        Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (row.underFuelled) p.orange else tint))
    }
}

/** goal_type → a palette color that reads in both light and dark theme. */
@Composable
private fun goalTint(goal: String): Color {
    val p = palette
    return when (goal) { "gain" -> p.blue; "lose" -> p.green; else -> p.purple }
}

/** Bold spray-paint styled winner card, pinned above the board until the next day closes. */
@Composable
private fun GraffitiCrownCard(crown: BattleRepo.BattleWinner) {
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFF5C8A), Color(0xFFFFC53D), Color(0xFF7C5CFF)),
        start = Offset(0f, 0f), end = Offset(400f, 120f),
    )
    Box(
        Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(24.dp)).background(gradient, RoundedCornerShape(24.dp)).padding(18.dp),
    ) {
        Column {
            Text("👑 GRAFFITI CROWN", fontSize = 12.sp, fontWeight = FontWeight(800), color = Color.White.copy(alpha = 0.85f), letterSpacing = 1.5.sp)
            Text(
                crown.name, fontSize = 26.sp, fontWeight = FontWeight(900), color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "${crown.score.toInt()} pts · ${crown.goalLabel} · yesterday's winner", fontSize = 13.sp, fontWeight = FontWeight(700),
                color = Color.White.copy(alpha = 0.92f), modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Owner-only row for the squad info page: turns Squad Food Battle on/off for the squad
 * ([SquadViewModel.toggleBattle]).
 */
@Composable
fun BattleToggleRow(sq: SquadViewModel, groupId: String) {
    val p = palette
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Squad Food Battle", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
            Text("Daily calorie game — closest to your own goal wins the crown", fontSize = 12.sp, color = p.muted)
        }
        androidx.compose.material3.Switch(
            checked = sq.battleEnabled, onCheckedChange = { sq.toggleBattle(groupId, it) }, enabled = !sq.battleBusy,
        )
    }
}

/** Camera → plate estimate (existing Api.photoMeal) → review (existing PhotoReview) → save + post to this squad. */
@Composable
private fun SnapSheet(vm: AppViewModel, groupId: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var est by remember { mutableStateOf<PlateEstimate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<android.net.Uri?>(null) }

    fun runEstimate(bmp: Bitmap) {
        photo = bmp; error = null
        scope.launch {
            busy = true
            try { est = Api.photoMeal(withContext(Dispatchers.Default) { toJpegBase64(bmp, 85) }, "") }
            catch (e: Exception) { error = e.message ?: "Couldn't read that plate" }
            finally { busy = false }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) target?.let { uri -> scope.launch { withContext(Dispatchers.IO) { decodeScaled(ctx, uri, 2200) }?.let { runEstimate(it) } ?: run { error = "Couldn't open that photo" } } }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { decodeScaled(ctx, uri, 2200) }?.let { runEstimate(it) } }
    }
    fun openCamera() {
        val dir = File(ctx.cacheDir, "scans").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(dir, "battle-snap.jpg"))
        target = uri
        runCatching { camera.launch(uri) }.onFailure { error = "No camera app found — pick from gallery instead." }
    }

    fun saveAndPost(items: List<com.sohum.bandlog.data.PlateItem>, storedPhotoPath: String?) {
        scope.launch {
            busy = true; error = null
            try {
                val date = vm.today
                val mealItems = items.map { it.toMealItem() }
                val photoPath = storedPhotoPath ?: photo?.let { runCatching { Api.uploadMealPhoto(withContext(Dispatchers.Default) { toJpegBytes(it, 85) }) }.getOrNull() }
                val names = items.map { it.name.trim() }.filter { it.isNotBlank() }
                val what = (names.take(3).joinToString(" + ") + if (names.size > 3) " + ${names.size - 3} more" else "").ifBlank { "Snap" }
                val mealId = Api.saveMeal(date, "Snap: $what", mealItems, photoPath)
                vm.refresh()

                val kcal = mealItems.sumOf { it.calories }
                val eatenToday = vm.meals.filter { it.date == date }.sumOf { it.calories }
                val targetKcal = vm.profile.calorieTarget + (if (vm.profile.addBurnedToGoal == true) vm.burnedOn(date) else 0.0)
                val line = BattleLines.line(vm.profile.goalType, eatenToday, targetKcal, seed = mealId.hashCode().toLong())
                val body = "$what · ${BattleRepo.kcalRangeText(kcal)} — $line"
                Api.postToGroups(listOf(groupId), "meal", body, mealId, photoPath)

                onDismiss()
            } catch (e: Exception) { error = e.message ?: "Couldn't save that meal" } finally { busy = false }
        }
    }

    com.sohum.bandlog.ui.components.BottomSheet(title = "Snap your plate", onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            ErrorNote(error, Modifier.padding(bottom = 8.dp))
            val e = est
            if (e == null) {
                Card {
                    Text("Take a photo of your plate — the same estimate as Scan.", fontSize = 13.sp, color = palette.muted)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(if (busy) "Working…" else "Camera", { openCamera() }, Modifier.weight(1f), enabled = !busy, height = 48.dp)
                        PillButton("Gallery", { gallery.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(0.6f), enabled = !busy, height = 48.dp, bg = palette.card2, fg = palette.ink)
                    }
                    if (busy) { Spacer(Modifier.height(10.dp)); CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = palette.muted) }
                }
            } else {
                PhotoReview(e, photo, readOnly = false, onSave = { items, storedPath -> saveAndPost(items, storedPath) })
                if (busy) { Spacer(Modifier.height(10.dp)); CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = palette.muted) }
            }
        }
    }
}
