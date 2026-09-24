package com.sohum.bandlog.ui.squad

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SQUAD_ICONS
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

val NAME_SUGGESTIONS = listOf("Plate Checkers", "Fuel & Flex", "Snack Team", "Locked In Bengaluru", "Cutting Season", "Bulk Szn", "Protein Pact", "5am Club")
val SQUAD_TAGS = listOf("Just for fun", "Friends", "Gym crew", "College", "Office", "Running", "Cutting", "Bulking")

/**
 * v2.6 Create squad (Cal AI group + Strava club): name + description + suggestion chips →
 * choose an icon (12 presets or upload) → public or private (+ up to 3 tags) → Create.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreateSquadFlow(sq: SquadViewModel, display: String, initialStep: Int = 0, onClose: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(initialStep) }
    var name by remember { mutableStateOf(if (initialStep > 0) "Fuel & Flex" else "") }
    var description by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var private by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    val pager = rememberPagerState { SQUAD_ICONS.size }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            photo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { com.sohum.bandlog.ui.scan.decodeScaled(ctx, uri, 1200) }.getOrNull() }
        }
    }
    val back: () -> Unit = { if (step == 0) onClose() else step -= 1 }
    androidx.activity.compose.BackHandler(enabled = step > 0) { step -= 1 }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().imePadding()) {
        FlowTopBar("Create Squad", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            when (step) {
                0 -> {
                    FlowHeading("Create squad name", "Pick something fun or goal-focused. Your squad name helps set the vibe.")
                    OutlinedLabelField(name, { name = it.take(40) }, "Squad Name")
                    Spacer(Modifier.height(16.dp))
                    FilledField(description, { description = it.take(200) }, "Description (optional)")
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NAME_SUGGESTIONS.forEach { s ->
                            val sel = name == s
                            Box(
                                Modifier.heightIn(min = 48.dp).pressable().then(if (sel) Modifier.background(p.card2, CircleShape) else Modifier)
                                    .border(1.5.dp, p.hair, CircleShape).clickable { name = s }.padding(horizontal = 20.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(s, fontSize = 16.sp, color = if (sel) p.ink else p.muted, maxLines = 1) }
                        }
                    }
                }
                1 -> {
                    FlowHeading("Choose a squad icon", "Select one of our pre-made icons or upload your own.")
                    val chosen = photo
                    if (chosen != null) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(170.dp).border(4.dp, p.ink, CircleShape).padding(6.dp).clip(CircleShape)) {
                                Image(chosen.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            Text("Your photo", fontSize = 20.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.padding(top = 18.dp))
                            Text("Use an icon instead", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.clickable { photo = null }.padding(12.dp))
                        }
                    } else {
                        HorizontalPager(pager, Modifier.fillMaxWidth().height(190.dp), contentPadding = PaddingValues(horizontal = 72.dp)) { page ->
                            val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                            Box(Modifier.fillMaxSize().graphicsLayer { val s = 1f - 0.3f * off; scaleX = s; scaleY = s; alpha = 1f - 0.35f * off }, contentAlignment = Alignment.Center) {
                                val preset = SQUAD_ICONS[page]
                                Box(Modifier.clickable { scope.launch { pager.animateScrollToPage(page) } }) {
                                    SquadIconView(preset.key, preset.key, 128.dp, ring = page == pager.currentPage)
                                }
                            }
                        }
                        Text(SQUAD_ICONS[pager.currentPage].label, fontSize = 22.sp, fontWeight = FontWeight(700), color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
                        Text("Swipe to select", fontSize = 15.sp, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    }
                    Text("OR", fontSize = 15.sp, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
                    Box(
                        Modifier.fillMaxWidth().height(58.dp).pressable().border(1.5.dp, p.hair, CircleShape)
                            .clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) { Text("Upload a photo", fontSize = 17.sp, fontWeight = FontWeight(600), color = p.ink) }
                }
                else -> {
                    FlowHeading("Public or private?", "You can change this later from the squad's page.")
                    PolicyCard("Public", "Anyone can find it under Discover and join straight away.", !private) { private = false }
                    Spacer(Modifier.height(12.dp))
                    PolicyCard("Private", "People ask to join and you approve them. Your invite link still lets friends straight in.", private) { private = true }
                    Spacer(Modifier.height(22.dp))
                    Text("What kind of squad? (up to 3)", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                    Spacer(Modifier.height(10.dp))
                    SQUAD_TAGS.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { t ->
                                val sel = t in tags
                                Box(
                                    Modifier.heightIn(min = 40.dp).pressable().background(if (sel) p.btn else p.card2, CircleShape)
                                        .clickable { tags = if (sel) tags - t else if (tags.size < 3) tags + t else tags }.padding(horizontal = 14.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(t, fontSize = 14.sp, fontWeight = FontWeight(600), color = if (sel) p.btnInk else p.ink, maxLines = 1) }
                            }
                        }
                    }
                }
            }
            ErrorNote(sq.error, Modifier.padding(top = 12.dp))
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp, 12.dp, 24.dp, 16.dp)) {
            when (step) {
                0 -> PillButton("Next", { step = 1 }, enabled = name.isNotBlank(), height = 60.dp)
                1 -> PillButton("Next", { step = 2 }, height = 60.dp)
                else -> PillButton(
                    if (sq.busy) "Creating…" else "Create Squad",
                    { sq.createV2(name, description, SQUAD_ICONS[pager.currentPage].key, photo, tags, private, display) },
                    enabled = !sq.busy && name.isNotBlank(), height = 60.dp,
                )
            }
        }
    }
}

@Composable
private fun PolicyCard(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().pressable().background(p.card, RoundedCornerShape(20.dp)).border(if (selected) 2.dp else 1.dp, if (selected) p.ink else p.hair, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight(800), color = p.ink)
            Text(sub, fontSize = 14.sp, color = p.muted, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(24.dp).border(2.dp, if (selected) p.ink else p.hair, CircleShape).padding(5.dp).then(if (selected) Modifier.background(p.ink, CircleShape) else Modifier))
    }
}
