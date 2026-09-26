package com.sohum.bandlog.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.CoachNote
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.onboarding.OnbIcons
import com.sohum.bandlog.ui.theme.Brand
import com.sohum.bandlog.ui.theme.Bricolage
import com.sohum.bandlog.ui.theme.EyebrowStyle
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.MealTypes
import com.sohum.bandlog.util.OnbStore
import com.sohum.bandlog.util.OnboardingV2

/*
 * v2.14 Home cards: "Tune your plan" for people who joined before v2.14, the coach's "Today's
 * note" (an ink card with the iris mark), and buddy streaks. Each hides itself while its server
 * side isn't there (schema_v37 / the coach routes).
 */

/** Whether Home should offer "Tune your plan": onboarded_v2 is false (not missing), the profile is complete, and it wasn't dismissed. */
fun tuneVisible(vm: AppViewModel): Boolean {
    val pr = vm.profile
    return pr.onboardedV2 == false && pr.weightKg != null && pr.heightCm != null && pr.dob != null && !OnbStore.tuneDismissed && !OnbStore.doneLocally
}

@Composable
fun TuneCard(onDismiss: () -> Unit) {
    val p = palette
    Box(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(20.dp))) {
        Row(
            Modifier.fillMaxWidth().pressable().clickable { CoachNav.open(CoachPage.TUNE) }.padding(start = 14.dp, end = 44.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoachAvatar(40.dp)
            Column(Modifier.weight(1f)) {
                Text("Tune your plan", fontFamily = Bricolage, fontSize = 16.sp, fontWeight = FontWeight(800), color = p.ink)
                Text("Three quick questions and your new AI coach knows how to talk to you.", fontSize = 13.sp, lineHeight = 18.sp, color = p.muted)
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(36.dp).clickable(onClickLabel = "Hide", onClick = onDismiss).semantics { contentDescription = "Hide" }, contentAlignment = Alignment.Center) {
            Icon(OnbIcons.X, null, tint = p.muted, modifier = Modifier.size(16.dp))
        }
    }
}

private fun noteTime(iso: String?): String = runCatching {
    val t = java.time.OffsetDateTime.parse(iso!!).atZoneSameInstant(com.sohum.bandlog.util.Dates.ZONE)
    clock("%02d:%02d".format(t.hour, t.minute))
}.getOrDefault("")

/** Today's note (and the Sunday roast / evening nudge), or the one-line explainer before the first note. */
@Composable
fun TodayNoteCards(cvm: CoachViewModel, onLogMeal: (String) -> Unit) {
    val p = palette
    if (cvm.noteAvailable != true) return
    val shown = cvm.notes?.shown.orEmpty()
    if (shown.isEmpty()) {
        val at = cvm.notes?.noteTime?.let { " (${clock(it)})" }.orEmpty()
        Row(
            Modifier.fillMaxWidth().pressable().background(p.card, RoundedCornerShape(20.dp)).clickable { CoachNav.open(CoachPage.CHAT) }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CoachAvatar(28.dp)
            Text("Your coach writes one note a morning$at, plus a nudge if you go quiet by 8 pm. Tap to chat now.", fontSize = 13.5.sp, lineHeight = 19.sp, color = p.muted)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { shown.forEach { NoteCard(it, onLogMeal) } }
}

@Composable
private fun NoteCard(n: CoachNote, onLogMeal: (String) -> Unit) {
    val p = palette
    val meal = MealTypes.default()
    val label = OnboardingV2.STYLES.firstOrNull { it.key == n.style }?.label ?: "Coach"
    val kind = when (n.kind) { "roast" -> "WEEKLY ROAST"; "evening" -> "EVENING NUDGE"; else -> "TODAY'S NOTE" }
    // Always an ink card (bone text) in both themes, like the design; iris marks the coach.
    Column(Modifier.fillMaxWidth().background(Brand.Ink, RoundedCornerShape(24.dp)).clickable { CoachNav.open(CoachPage.CHAT) }.padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoachAvatar(28.dp)
            Spacer(Modifier.width(8.dp))
            Text("$kind · ${label.uppercase()}", style = EyebrowStyle.copy(fontSize = 11.5.sp), color = Color(0xFFB9AEFF), modifier = Modifier.weight(1f), maxLines = 1)
            Text(noteTime(n.createdAt), fontSize = 12.sp, color = Brand.Mute)
        }
        Text(n.text, fontSize = 17.sp, lineHeight = 24.sp, color = Brand.Bone, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NoteChip("Reply") { CoachNav.open(CoachPage.CHAT) }
            NoteChip("Log ${MealTypes.label(meal).removeSuffix("s").lowercase()}") { onLogMeal(meal) }
        }
    }
}

@Composable
private fun NoteChip(label: String, onClick: () -> Unit) {
    Box(Modifier.pressable().background(Color.White.copy(alpha = 0.1f), CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp)) {
        Text(label, fontSize = 13.5.sp, fontWeight = FontWeight(600), color = Brand.Bone)
    }
}

/** Buddy streaks on Home: the shared flame, both-logged dots, Nudge. Nothing while my_buddies() is missing. */
@Composable
fun BuddyCard(cvm: CoachViewModel) {
    val p = palette
    if (cvm.buddyAvailable != true) return
    val list = cvm.buddies.orEmpty()
    if (list.isEmpty()) {
        Row(
            Modifier.fillMaxWidth().pressable().background(p.card, RoundedCornerShape(20.dp)).clickable { CoachNav.open(CoachPage.BUDDY) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(36.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) { Icon(OnbIcons.Flame, null, tint = p.muted, modifier = Modifier.size(18.dp)) }
            Column(Modifier.weight(1f)) {
                Text("Bring a buddy", fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink)
                Text("A shared streak makes you 2× more likely to stick", fontSize = 12.sp, color = p.muted)
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { list.take(2).forEach { b -> BuddyRow(b, cvm) { CoachNav.open(CoachPage.BUDDY) } } }
}

/** Whether any v2.14 Home card has something to show (so Home doesn't reserve an empty row). */
fun v214CardsVisible(vm: AppViewModel, cvm: CoachViewModel): Boolean = cvm.noteAvailable == true || cvm.buddyAvailable == true || tuneVisible(vm)

/** Everything v2.14 adds to Home, in order. The caller loads [cvm] once Home has a profile. */
@Composable
fun V214HomeCards(vm: AppViewModel, cvm: CoachViewModel, onLogMeal: (String) -> Unit) {
    var tuneHidden by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!tuneHidden && tuneVisible(vm)) TuneCard { OnbStore.tuneDismissed = true; tuneHidden = true }
        TodayNoteCards(cvm, onLogMeal)
        BuddyCard(cvm)
    }
}
