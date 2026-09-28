package com.sohum.bandlog.ui.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.resolveAsTypeface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.sohum.bandlog.R
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.SocialApi
import com.sohum.bandlog.data.SocialStore
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.GroupLabel
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.profile.CoverView
import com.sohum.bandlog.ui.progress.Jewel
import com.sohum.bandlog.ui.theme.Display
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Jewels
import com.sohum.bandlog.util.Leagues
import com.sohum.bandlog.util.Packs
import com.sohum.bandlog.util.Story
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------------------------------ D10

/**
 * D10 transformation story (opt-in): progress photos and weights as a before → after timeline,
 * exported as a frame sequence (an intro, one frame per photo, an outro) through
 * ACTION_SEND_MULTIPLE. Video encoding (MediaCodec) was left out on purpose: frames share to every
 * story / reel editor and keep the APK small.
 */
@Composable
fun StoryScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var optIn by remember { mutableStateOf(SocialStore.flag(Story.STORY_OPTIN_KEY)) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.loadProgressPhotos() }
    val frames = remember(vm.progressPhotos, vm.weights) {
        Story.frames(vm.progressPhotos.map { Story.Photo(it.date, it.path, null) }, vm.weights.map { Story.Weight(it.date, it.weightKg) })
    }
    val display by LocalFontFamilyResolver.current.resolveAsTypeface(Display, FontWeight(800))

    SubPage(tr("story.title"), onBack) {
        Hero(tr("story.title"), tr("story.sub"), "Private until you share it. Only your progress photos and weights are used.")
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Make my story", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                    Text("Off by default. Nothing is posted anywhere.", fontSize = 12.sp, color = p.muted)
                }
                Switch(optIn, { optIn = it; SocialStore.setFlag(Story.STORY_OPTIN_KEY, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Ember))
            }
        }
        if (!optIn) return@SubPage
        GroupLabel("Timeline")
        Card {
            if (frames.isEmpty()) Text("Add progress photos (Progress → Body & training → Progress photos) to build the story.", fontSize = 14.sp, color = p.muted, lineHeight = 19.sp)
            frames.forEachIndexed { i, f ->
                val (day, change) = Story.caption(f)
                LineRow("${Dates.short(f.date)} · $day", listOfNotNull(f.kg?.let { "${com.sohum.bandlog.util.Wrapped.num(it)} kg" }, change).joinToString(" · "), valueColor = if (i == frames.lastIndex) Ember else null)
            }
        }
        if (frames.isNotEmpty()) {
            Text("${frames.size} frames · about ${Story.reelLength(frames.size) / 1000} s as a reel", fontSize = 12.sp, color = p.muted)
            ErrorNote(err)
            PillButton(if (busy) "Making the frames…" else "Export and share", {
                if (busy) return@PillButton
                scope.launch {
                    busy = true; err = null
                    try {
                        val uris = withContext(Dispatchers.IO) {
                            val out = mutableListOf<android.net.Uri>()
                            out.add(saveFrame(ctx, StoryCards.intro(ctx, frames, vm.profile.name, display)))
                            frames.forEach { f ->
                                val photo = f.url?.let { Api.fetchStorageBitmap("progress-photos", it) }
                                out.add(saveFrame(ctx, StoryCards.frame(ctx, f, photo, display)))
                                photo?.recycle()
                            }
                            out.add(saveFrame(ctx, StoryCards.outro(ctx, frames, display)))
                            out
                        }
                        WrappedCards.shareUris(ctx, uris, "Share your story")
                    } catch (e: Exception) { err = e.message ?: "Couldn't make the story" }
                    busy = false
                }
            }, bg = Ember, fg = BoneBrand)
        }
    }
}

private fun saveFrame(ctx: Context, b: Bitmap): android.net.Uri = WrappedCards.save(ctx, b, "story-${System.nanoTime()}.png").also { b.recycle() }

/** Story frames, 1080 × 1920: the photo full-bleed with an ink fade, "Day N" and the change. */
object StoryCards {
    private const val W = 1080
    private const val H = 1920

    private fun geist(ctx: Context, weight: Int, mono: Boolean = false): Typeface {
        val base = runCatching { ResourcesCompat.getFont(ctx, if (mono) R.font.geistmono else R.font.geist) }.getOrNull() ?: Typeface.DEFAULT
        return Typeface.create(base, weight, false)
    }

    private fun base(): Pair<Bitmap, Canvas> {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFF0B0B0C.toInt())
        return b to c
    }

    private fun paint(size: Float, color: Int, tf: Typeface, align: Paint.Align = Paint.Align.LEFT, spacing: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = tf; textAlign = align; letterSpacing = spacing }

    fun intro(ctx: Context, frames: List<Story.Frame>, name: String, display: Typeface?): Bitmap {
        val (b, c) = base()
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), 0xFFFF7A3D.toInt(), 0xFFC2410C.toInt(), Shader.TileMode.CLAMP) })
        c.drawText("LOCKED IN", 96f, 190f, paint(40f, 0xFFF4F1EA.toInt(), geist(ctx, 600, true), spacing = 0.28f))
        val first = name.trim().split(Regex("\\s+")).firstOrNull()?.ifBlank { null }
        c.drawText(if (first != null) "$first's" else "My", 96f, 900f, paint(120f, 0xFFF4F1EA.toInt(), display ?: geist(ctx, 800)))
        c.drawText("transformation", 96f, 1030f, paint(120f, 0xFFF4F1EA.toInt(), display ?: geist(ctx, 800)))
        val days = frames.lastOrNull()?.dayIndex?.plus(1) ?: 1
        c.drawText("$days days · ${frames.size} photos", 96f, 1140f, paint(54f, 0xCCF4F1EA.toInt(), geist(ctx, 500)))
        return b
    }

    fun frame(ctx: Context, f: Story.Frame, photo: Bitmap?, display: Typeface?): Bitmap {
        val (b, c) = base()
        if (photo != null) {
            // Center-crop to 9:16.
            val scale = maxOf(W.toFloat() / photo.width, H.toFloat() / photo.height)
            val sw = (W / scale).toInt(); val sh = (H / scale).toInt()
            val sx = ((photo.width - sw) / 2).coerceAtLeast(0); val sy = ((photo.height - sh) / 2).coerceAtLeast(0)
            c.drawBitmap(photo, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, W, H), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        c.drawRect(0f, H * 0.55f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, H * 0.55f, 0f, H.toFloat(), 0x000B0B0C, 0xF00B0B0C.toInt(), Shader.TileMode.CLAMP) })
        val (day, change) = Story.caption(f)
        c.drawText("LOCKED IN", 96f, 190f, paint(40f, 0xFFF4F1EA.toInt(), geist(ctx, 600, true), spacing = 0.28f))
        c.drawText(day, 96f, 1560f, paint(150f, 0xFFF4F1EA.toInt(), display ?: geist(ctx, 800)))
        val line = listOfNotNull(Dates.long(f.date), f.kg?.let { "${com.sohum.bandlog.util.Wrapped.num(it)} kg" }).joinToString(" · ")
        c.drawText(line, 96f, 1650f, paint(46f, 0xFFB5B0A8.toInt(), geist(ctx, 500)))
        change?.let { c.drawText(it, W - 96f, 1560f, paint(72f, 0xFFFF5B1F.toInt(), geist(ctx, 700), Paint.Align.RIGHT)) }
        return b
    }

    fun outro(ctx: Context, frames: List<Story.Frame>, display: Typeface?): Bitmap {
        val (b, c) = base()
        val last = frames.lastOrNull()
        val change = last?.let { Story.caption(it).second }
        c.drawText("LOCKED IN", 96f, 190f, paint(40f, 0xFFF4F1EA.toInt(), geist(ctx, 600, true), spacing = 0.28f))
        c.drawText(change ?: "Still going.", 96f, 950f, paint(if (change != null) 200f else 120f, 0xFFFF5B1F.toInt(), display ?: geist(ctx, 800)))
        c.drawText("Day ${(last?.dayIndex ?: 0) + 1} and counting.", 96f, 1060f, paint(60f, 0xFFF4F1EA.toInt(), geist(ctx, 500)))
        c.drawText("Locked In", 96f, H - 150f, paint(38f, 0xFFFF5B1F.toInt(), geist(ctx, 700)))
        return b
    }
}

// ------------------------------------------------------------------------------------------ D11

/** D11 covers and badge packs: gold covers + badge skins, free in the beta with the price shown. */
@Composable
fun PacksScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val remote = rememberLoad(tick) { SocialApi.myUnlocks().also { SocialStore.updateUnlocked(SocialStore.unlocked + it) } }
    var err by remember { mutableStateOf<String?>(null) }
    val unlocked = SocialStore.unlocked

    SubPage(tr("packs.title"), onBack) {
        Hero("Premium", tr("packs.title"), "Payments aren't switched on yet, so every pack is free during the beta.", gold = true)
        if (remote.missing) Text("Unlocks are kept on this phone until the next server update.", fontSize = 12.sp, color = p.muted)
        ErrorNote(err)
        Packs.PACKS.forEach { pack ->
            val have = pack.id in unlocked
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pack.name, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
                        Text(pack.blurb, fontSize = 13.sp, color = p.muted)
                        Spacer(Modifier.height(6.dp))
                        OutlinePill(Packs.priceLabel(pack, Packs.PACKS_BETA_FREE_DEFAULT), Gold)
                    }
                    if (pack.kind == "badge_skin") {
                        val skin = pack.id.removePrefix("skin-")
                        Jewel(Jewels.Category.STREAK, Jewels.Tier.GOLD, 56.dp, metal = Packs.SKIN_METAL[skin], label = pack.name)
                    }
                }
                if (pack.kind == "covers") {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Packs.GOLD_COVERS.take(3).forEach { g ->
                            CoverView(g.id, Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp)))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (!have) PillButton("Unlock free", {
                    SocialStore.updateUnlocked(SocialStore.unlocked + pack.id)
                    scope.launch { runCatching { SocialApi.unlockPack(pack.id, pack.priceInr) }.onFailure { e -> if (e !is NotYetAvailable) err = e.message }; tick++ }
                }, bg = Gold, fg = com.sohum.bandlog.ui.theme.Brand.Ink, height = 46.dp)
                else if (pack.kind == "covers") Text("Unlocked · pick one from your cover's “Cover” button (Gold row).", fontSize = 13.sp, color = Gold)
                else {
                    val skin = pack.id.removePrefix("skin-")
                    val on = SocialStore.badgeSkin == skin
                    PillButton(if (on) "In use · switch back to classic" else "Use on my badges", {
                        val next = if (on) "classic" else skin
                        SocialStore.setSkin(next)
                        scope.launch { SocialApi.patchProfile(org.json.JSONObject().put("badge_skin", if (next == "classic") org.json.JSONObject.NULL else next)) }
                    }, height = 46.dp)
                }
            }
        }
    }
}

/** The Gold row in the cover picker (only once the pack is unlocked). */
@Composable
fun GoldCoverRow(current: String, onPick: (String) -> Unit) {
    val p = palette
    if (Packs.COVERS_PACK !in SocialStore.unlocked) return
    Text("Gold edition", fontSize = 13.sp, fontWeight = FontWeight(700), color = Gold, modifier = Modifier.padding(bottom = 8.dp))
    Packs.GOLD_COVERS.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { g ->
                val sel = g.id == current
                Column(Modifier.weight(1f).clickable(onClickLabel = "Use ${g.name} gold") { onPick(g.id) }) {
                    CoverView(g.id, Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(14.dp)).border(if (sel) 2.5.dp else 1.dp, if (sel) Gold else p.hair, RoundedCornerShape(14.dp)))
                    Text("${g.name} · gold" + if (sel) " · current" else "", fontSize = 10.5.sp, color = if (sel) p.ink else p.muted, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// ------------------------------------------------------------------------------------------ D12

/** D12 squad leagues: behind [Leagues.LEAGUES_ENABLED] (off), so nothing links here in v2.18. */
@Composable
fun LeaguesScreen(onBack: () -> Unit) {
    val p = palette
    SubPage(tr("leagues.title"), onBack) {
        if (!Leagues.leaguesOn(null)) { SoonCard(tr("leagues.title"), "Squad vs squad leagues aren't switched on."); return@SubPage }
        Card { Leagues.TIERS.forEach { Text(it, fontSize = 15.sp, color = p.ink, modifier = Modifier.padding(vertical = 6.dp)) } }
        Box(Modifier.background(p.card2))
    }
}
