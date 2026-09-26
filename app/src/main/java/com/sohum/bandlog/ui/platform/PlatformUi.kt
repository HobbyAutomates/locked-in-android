package com.sohum.bandlog.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.progress.accentColor
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Pro

/** v2.13: the small "PRO" chip on Pro features (spec §1). */
@Composable
fun ProChip(modifier: Modifier = Modifier) {
    val accent = accentColor
    Box(
        modifier.border(1.dp, accent, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) { Text("PRO", fontSize = 10.sp, fontWeight = FontWeight(700), letterSpacing = 0.8.sp, color = accent, lineHeight = 12.sp) }
}

/** A page title row with an optional PRO chip. */
@Composable
fun TitleWithChip(text: String, pro: Boolean, size: Int = 17) {
    val p = palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = size.sp, fontWeight = FontWeight(700), color = p.ink)
        if (pro) { Spacer(Modifier.width(8.dp)); ProChip() }
    }
}

/** What a v36 feature shows while the server update isn't applied (spec: "Coming with the next update"). */
@Composable
fun ComingSoonCard(title: String, detail: String = "This needs a server update that's rolling out soon. Nothing you do now is lost.") {
    val p = palette
    Card {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
        Spacer(Modifier.height(4.dp))
        Text("Coming with the next update", fontSize = 14.sp, fontWeight = FontWeight(600), color = accentColor)
        Spacer(Modifier.height(6.dp))
        Text(detail, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
    }
}

/**
 * Paywall wrapper (spec §1): shows [content] when the user can use [feature], else a locked card
 * that opens the Pro screen. With everyone on 'beta' this never locks today.
 */
@Composable
fun ProGate(pvm: PlatformViewModel, feature: Pro.Feature, content: @Composable () -> Unit) {
    if (Pro.canUse(feature, pvm.hasPro)) { content(); return }
    val p = palette
    Card(onClick = { PlatformNav.open(PlatformPage.PRO) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(feature.label, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.weight(1f))
            ProChip()
        }
        Spacer(Modifier.height(6.dp))
        Text("Part of Locked In Pro. Tap to see what's included.", fontSize = 13.sp, color = p.muted)
    }
}

/** A tappable list row for the Progress "Body & training" card. */
@Composable
fun NavRow(icon: ImageVector, label: String, value: String = "", pro: Boolean = false, onClick: () -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClickLabel = label, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight(500), color = p.ink)
        if (pro) { Spacer(Modifier.width(8.dp)); ProChip() }
        Spacer(Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, fontSize = 13.sp, color = p.muted, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Icon(LineIcons.ChevronRight, null, tint = p.muted, modifier = Modifier.size(16.dp))
    }
}

/** A plain confirmation dialog in the app's card style. */
@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, danger: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = palette
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(24.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight(800), color = p.ink)
            Text(body, fontSize = 14.sp, color = p.muted, lineHeight = 20.sp)
            Spacer(Modifier.height(6.dp))
            PillButton(confirm, onConfirm, bg = if (danger) p.red else null, fg = if (danger) Color.White else null)
            Text(
                "Cancel", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onDismiss).padding(vertical = 12.dp),
            )
        }
    }
}

/** Small grey pill (e.g. "Front", "72.4 kg"). */
@Composable
fun SoftPill(text: String, modifier: Modifier = Modifier) {
    val p = palette
    Box(modifier.background(p.card2, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight(600), color = p.ink, maxLines = 1)
    }
}

/**
 * The SubPage header (floating back pill, centred title) over a body the caller lays out itself
 * (a LazyColumn / grid), for pages too long for SubPage's single scrolling Column.
 */
@Composable
fun PageFrame(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val p = palette
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow)
                    .background(p.card, CircleShape).clickable(onClickLabel = "Back", onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(androidx.compose.material.icons.Icons.Outlined.ArrowBack, "Back", tint = p.ink, modifier = Modifier.size(18.dp)) }
            Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { trailing?.invoke() }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
