package com.sohum.bandlog.ui.social

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.MemberDetail
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.SocialApi
import com.sohum.bandlog.data.SocialStore
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Avatar
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.GroupLabel
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Freezes
import com.sohum.bandlog.util.Names
import kotlinx.coroutines.launch

private val Ice = Color(0xFF9FD0FF)
private val IceDeep = Color(0xFF2A6FD8)

/** D5 streak freezes: the tokens (3 slots), how to earn the next one, the days they saved, and gifting. */
@Composable
fun FreezesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    val today = Dates.today()
    var tick by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val events = rememberLoad(tick) { SocialApi.freezeEvents() }
    val tokens = rememberLoad(tick) { SocialApi.myFreezeTokens().also { SocialStore.setFreezes(it, SocialStore.frozenDays) } }
    val mates = rememberLoad(Unit) { squadmates() }
    val active = remember(vm.meals, vm.workouts, vm.exercises) { (vm.meals.map { it.date } + vm.workouts.map { it.date } + vm.exercises.map { it.date }).toSet() }
    val have = tokens.data ?: SocialStore.freezeTokens
    val parsed = events.data?.let { Freezes.parseFreezeEvents(it) }

    SubPage(tr("streak.title"), onBack) {
        if (tokens.missing || events.missing) { SoonCard(tr("streak.title"), "Streak freezes switch on with the next server update. Your streak still counts every logged day."); return@SubPage }
        Hero(tr("streak.title"), Freezes.freezeCountText(have), tr("streak.sub"))
        Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(Freezes.MAX_FREEZES) { i -> FreezeToken(filled = i < have, size = 64.dp) }
            }
            Spacer(Modifier.padding(6.dp))
            Text(Freezes.earnHint(have, active, today), fontSize = 14.sp, color = p.ink, lineHeight = 20.sp)
            Text("One is used automatically on a missed day, so the streak keeps going.", fontSize = 12.sp, color = p.muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        }
        ErrorNote(err)
        info?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.green) }
        PillButton("Check now", {
            scope.launch {
                err = null
                try {
                    val r = SocialApi.freezeSync()
                    SocialStore.setFreezes(r.tokens, r.usedDays); SocialStore.lastFreezeSync = today
                    info = when {
                        r.earnedNow > 0 -> "You earned ${r.earnedNow} ${if (r.earnedNow == 1) "freeze" else "freezes"}."
                        r.usedNow > 0 -> "A freeze saved your streak."
                        else -> "All up to date."
                    }
                    tick++
                } catch (e: NotYetAvailable) { err = tr("common.soon") } catch (e: Exception) { err = e.message }
            }
        }, height = 46.dp)

        GroupLabel("Days a freeze saved")
        Card {
            val used = parsed?.usedDays ?: SocialStore.frozenDays
            if (events.loading) Spinner()
            else if (used.isEmpty()) Text("None yet. Every day so far was logged.", fontSize = 14.sp, color = p.muted)
            else used.take(12).forEachIndexed { i, d -> if (i > 0) Hair(); LineRow(Dates.long(d), "Frozen", valueColor = IceDeep) }
        }

        GroupLabel("Gift one to a squadmate")
        Card {
            Text("Once per person a week. They need room (under 3).", fontSize = 12.sp, color = p.muted)
            when {
                mates.loading -> Spinner()
                mates.data.isNullOrEmpty() -> Text("Join a squad to gift freezes.", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(top = 8.dp))
                else -> mates.data.forEachIndexed { i, m ->
                    if (i > 0) Hair()
                    val block = Freezes.canGift(have, null, parsed?.lastGiftTo?.get(m.userId), self = m.userId == Session.userId)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(Api.avatarUrl(m.avatarPath), Names.initials(m.name), 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(m.name, fontSize = 15.sp, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                        ChoicePill("Gift", false, {
                            if (block != null) err = block.text
                            else scope.launch {
                                err = null
                                try { val left = SocialApi.freezeGift(m.userId); SocialStore.setFreezes(left, SocialStore.frozenDays); info = "Sent ${m.name.substringBefore(' ')} a freeze."; tick++ }
                                catch (e: NotYetAvailable) { err = tr("common.soon") } catch (e: Exception) { err = e.message }
                            }
                        })
                    }
                }
            }
        }
    }
}

/** Everyone in my squads except me, once each. */
internal suspend fun squadmates(): List<MemberDetail> {
    val me = Session.userId
    return Api.mySquads().flatMap { runCatching { Api.groupMembersDetail(it.id) }.getOrDefault(emptyList()) }
        .filter { it.userId != me }.distinctBy { it.userId }.sortedBy { it.name.lowercase() }
}

/** One freeze token: an ice-blue hexagonal gem (hollow when the slot is empty). */
@Composable
internal fun FreezeToken(filled: Boolean, size: Dp) {
    val p = palette
    Canvas(Modifier.size(size).semantics { contentDescription = if (filled) "Freeze ready" else "Empty slot" }) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.04f); lineTo(w * 0.92f, h * 0.28f); lineTo(w * 0.92f, h * 0.72f)
            lineTo(w * 0.5f, h * 0.96f); lineTo(w * 0.08f, h * 0.72f); lineTo(w * 0.08f, h * 0.28f); close()
        }
        if (filled) {
            drawPath(path, Brush.linearGradient(listOf(Color.White, Ice, IceDeep, Color(0xFF0E2D66)), Offset(0f, 0f), Offset(w, h)))
            // A frost facet: the snowflake's three strokes.
            val c = Offset(w / 2, h / 2)
            for (k in 0 until 3) {
                val a = Math.toRadians(90.0 + k * 60.0)
                val dx = (kotlin.math.cos(a) * w * 0.26).toFloat()
                val dy = (kotlin.math.sin(a) * h * 0.26).toFloat()
                drawLine(Color.White.copy(alpha = 0.85f), Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), strokeWidth = w * 0.035f)
            }
        } else drawPath(path, p.muted.copy(alpha = 0.5f), style = Stroke(width = w * 0.03f))
    }
}

/** Home: a small ice pill beside the day streak while freezes are banked ("2"), opening Streak freezes. */
@Composable
fun FreezeChip() {
    val n = SocialStore.freezeTokens
    if (n <= 0) return
    val p = palette
    Row(
        Modifier.height(34.dp).background(Ice.copy(alpha = 0.16f), androidx.compose.foundation.shape.CircleShape)
            .clickable(onClickLabel = Freezes.freezeCountText(n)) { SocialNav.open(SocialPage.FREEZES) }.padding(start = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FreezeToken(filled = true, size = 18.dp)
        Spacer(Modifier.width(5.dp))
        Text("$n", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.ink)
    }
}
