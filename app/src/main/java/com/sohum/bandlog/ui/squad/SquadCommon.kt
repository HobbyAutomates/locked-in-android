package com.sohum.bandlog.ui.squad

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.components.PeopleIcon
import com.sohum.bandlog.ui.components.RemoteImage
import com.sohum.bandlog.ui.components.SQUAD_ICONS
import com.sohum.bandlog.ui.theme.palette

const val WEB_URL = "https://web-production-ff1cf.up.railway.app"
const val APK_URL = "https://evizkfvltacrfngsgbuu.supabase.co/storage/v1/object/public/app/LockedIn-18.apk"

/** v2.6 invite link; the web app serves /join/<code> and the Android app opens it directly. */
fun inviteLink(code: String) = "$WEB_URL/join/${code.uppercase()}"

fun squadInviteText(name: String, code: String) =
    "Join my squad “$name” on Locked In: ${inviteLink(code)} (code $code). New here? Android: $APK_URL"

/** Share an invite: WhatsApp directly when [whatsapp] and it's installed, else the system share sheet. */
fun shareInvite(ctx: Context, text: String, whatsapp: Boolean = false) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    if (whatsapp) {
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            if (runCatching { ctx.startActivity(Intent(send).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }
    runCatching { ctx.startActivity(Intent.createChooser(send, "Invite to your squad").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** "now", "5m", "3h", "2d", else the date. */
fun timeAgo(iso: String): String = runCatching {
    val t = java.time.OffsetDateTime.parse(iso).toInstant()
    val s = java.time.Duration.between(t, java.time.Instant.now()).seconds
    when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3600}h"
        s < 7 * 86_400 -> "${s / 86_400}d"
        else -> t.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH))
    }
}.getOrDefault("")

/**
 * A squad's picture: its uploaded photo, else the icon (or a people mark) on the squad's mono
 * texture (v2.14 brand: squads get a texture from [textureId]'s hash, never a colour).
 */
@Composable
fun SquadIconView(icon: String?, seed: String, size: Dp, modifier: Modifier = Modifier, ring: Boolean = false, cover: String? = null, textureId: String? = null) {
    val p = palette
    val ringMod = if (ring) Modifier.border(4.dp, p.ink, CircleShape).padding(6.dp) else Modifier
    Box(modifier.then(ringMod), contentAlignment = Alignment.Center) {
        if (icon != null && icon.startsWith("photo:")) {
            RemoteImage(privatePath = icon.removePrefix("photo:"), bucket = "group-photos", size = size, radius = size / 2, fallback = PeopleIcon)
        } else if (SQUAD_ICONS.none { it.key == icon } && !cover.isNullOrBlank()) {
            RemoteImage(url = cover, size = size, radius = size / 2, fallback = PeopleIcon)
        } else {
            val preset = SQUAD_ICONS.firstOrNull { it.key == icon }
            Box(
                Modifier.size(size).clip(CircleShape).background(p.card2).squadTexture(textureId ?: seed, alpha = 0.2f, cell = (size.value / 6f).coerceIn(6f, 14f).dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(size * 0.62f).background(p.card, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(preset?.icon ?: PeopleIcon, null, tint = p.ink, modifier = Modifier.size(size * 0.36f))
                }
            }
        }
    }
}

/** The eight gradient-initials profile photo presets (Cal AI's swipe row). */
val AVATAR_GRADIENTS: List<Pair<Color, Color>> = listOf(
    Color(0xFFFF8A00) to Color(0xFFE94E3C),
    Color(0xFFE91E63) to Color(0xFF9C27B0),
    Color(0xFF3F51B5) to Color(0xFF2196F3),
    Color(0xFF00A86B) to Color(0xFF7ED957),
    Color(0xFF6A11CB) to Color(0xFF2575FC),
    Color(0xFFF7971E) to Color(0xFFFFD200),
    Color(0xFF11998E) to Color(0xFF38EF7D),
    Color(0xFF232526) to Color(0xFF6B6F76),
)

@Composable
fun PresetAvatar(index: Int, initials: String, size: Dp, modifier: Modifier = Modifier) {
    val (a, b) = AVATAR_GRADIENTS[index.mod(AVATAR_GRADIENTS.size)]
    Box(modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
        Text(initials, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight(700), color = Color.White, maxLines = 1)
    }
}

/** The preset drawn to a [px] square bitmap for avatars/<uid>/avatar.jpg (a JPEG has no alpha, so the corners are filled). */
fun renderPresetAvatar(index: Int, initials: String, px: Int = 512): android.graphics.Bitmap {
    val (a, b) = AVATAR_GRADIENTS[index.mod(AVATAR_GRADIENTS.size)]
    val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.shader = android.graphics.LinearGradient(0f, 0f, px.toFloat(), px.toFloat(), a.toArgb(), b.toArgb(), android.graphics.Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), paint)
    val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = px * 0.38f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val y = px / 2f - (text.descent() + text.ascent()) / 2f
    c.drawText(initials, px / 2f, y, text)
    return bmp
}

/** The flows' top bar: back arrow + optional title (Cal AI "← Create Group"). */
@Composable
fun FlowTopBar(title: String?, onBack: () -> Unit) {
    val p = palette
    Row(Modifier.fillMaxWidth().padding(8.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ArrowBack, "Back", tint = p.ink, modifier = Modifier.size(24.dp))
        }
        if (title != null) Text(title, fontSize = 22.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Cal AI's outlined text field with the label notched into the top border. */
@Composable
fun OutlinedLabelField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    prefix: String? = null, trailing: (@Composable () -> Unit)? = null, capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences, onDone: () -> Unit = {},
) {
    val p = palette
    Box(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).border(2.dp, p.ink, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prefix != null) Text(prefix, fontSize = 18.sp, color = p.muted)
            BasicTextField(
                value, onChange, singleLine = true, modifier = Modifier.weight(1f).padding(vertical = 18.dp),
                keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done, autoCorrect = false),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                textStyle = TextStyle(fontSize = 18.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
            )
            if (trailing != null) trailing()
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.offset(x = 16.dp, y = (-9).dp).background(p.bg).padding(horizontal = 6.dp))
    }
}

/** A filled secondary field ("Description (optional)"). */
@Composable
fun FilledField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, maxLines: Int = 3, onDone: () -> Unit = {}) {
    val p = palette
    Box(modifier.fillMaxWidth().heightIn(min = 60.dp).background(p.card2, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 18.dp), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value, onChange, maxLines = maxLines, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (maxLines == 1) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            textStyle = TextStyle(fontSize = 17.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
            decorationBox = { inner -> if (value.isEmpty()) Text(hint, fontSize = 17.sp, color = p.muted); inner() },
        )
    }
}

/** Big centred heading + subline used on every step of the flows. */
@Composable
fun FlowHeading(title: String, sub: String) {
    val p = palette
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = p.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 32.sp)
        Text(sub, fontSize = 16.sp, color = p.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
