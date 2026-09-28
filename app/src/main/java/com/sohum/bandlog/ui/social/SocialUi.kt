package com.sohum.bandlog.ui.social

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.SocialStore
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.theme.Display
import com.sohum.bandlog.ui.theme.palette

/** Brand v1 "dark gold for premium". */
internal val Gold = Color(0xFFD9B872)
internal val GoldPale = Color(0xFFFBE7A8)
internal val GoldDeep = Color(0xFF5E4518)
internal val Ember = Color(0xFFFF5B1F)
internal val InkBrand = Color(0xFF0B0B0C)
internal val BoneBrand = Color(0xFFF4F1EA)

/** Shorthand for the v2.18 strings in the chosen language. */
internal fun tr(key: String, vars: Map<String, Any> = emptyMap()) = SocialStore.t(key, vars)

/** A screen's data load: [missing] = the v44 / v45 schema isn't applied yet ("Coming with the next update"). */
internal class Load<T>(val loading: Boolean = true, val data: T? = null, val missing: Boolean = false, val error: String? = null)

/** Loads [block] once per [key]; NotYetAvailable → missing, other errors → error. */
@Composable
internal fun <T> rememberLoad(key: Any?, tick: Int = 0, block: suspend () -> T): Load<T> {
    var state by remember(key) { mutableStateOf(Load<T>()) }
    LaunchedEffect(key, tick) {
        state = try { Load(false, block()) } catch (e: NotYetAvailable) { Load(false, missing = true) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Load(false, error = e.message ?: "Couldn't load") }
    }
    return state
}

/** The "Coming with the next update" card for a feature whose server part isn't applied yet. */
@Composable
internal fun SoonCard(title: String, detail: String = "This needs a server update that's rolling out soon. Nothing you do now is lost.") {
    val p = palette
    Card {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight(700), color = p.ink)
        Spacer(Modifier.height(4.dp))
        Text(tr("common.soon"), fontSize = 14.sp, fontWeight = FontWeight(600), color = Ember)
        Spacer(Modifier.height(6.dp))
        Text(detail, fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
    }
}

@Composable
internal fun Spinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = palette.muted)
    }
}

/** A one-line (or multi-line) text field on the grey fill. */
@Composable
internal fun SocialField(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, label: String = placeholder,
) {
    val p = palette
    val focus = LocalFocusManager.current
    Box(modifier.fillMaxWidth().heightIn(min = 48.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(14.dp, 12.dp), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth().semantics { contentDescription = label }, singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink),
            decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = p.muted, maxLines = if (singleLine) 1 else 3); inner() },
        )
    }
}

/** Small outlined pill (e.g. "₹149 · free in the beta"). */
@Composable
internal fun OutlinePill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, color, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight(700), color = color, maxLines = 1)
    }
}

/** A tappable choice pill (filled when selected). */
@Composable
internal fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = palette
    Box(
        modifier.heightIn(min = 36.dp).background(if (selected) p.ink else p.card2, CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight(600), color = if (selected) p.bg else p.ink, maxLines = 1) }
}

/** The ink hero at the top of a v2.18 screen: an eyebrow, a big Bricolage line and a sub-line. */
@Composable
internal fun Hero(eyebrow: String, big: String, sub: String? = null, gold: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().background(
            if (gold) Brush.linearGradient(listOf(Color(0xFF15110A), Color(0xFF2A2012))) else Brush.linearGradient(listOf(Color(0xFF141416), Color(0xFF2A1A10))),
            RoundedCornerShape(24.dp),
        ).padding(20.dp),
    ) {
        Text(eyebrow.uppercase(), fontSize = 11.sp, fontWeight = FontWeight(700), letterSpacing = 1.6.sp, color = if (gold) Gold else Color(0xFFFF8B5E))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(big, fontFamily = Display, fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-0.8).sp, color = if (gold) GoldPale else BoneBrand, modifier = Modifier.weight(1f), lineHeight = 34.sp)
            trailing?.let { Spacer(Modifier.width(10.dp)); it() }
        }
        sub?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 13.sp, color = Color(0xFFB5B0A8), lineHeight = 18.sp) }
    }
}

/** Plain row of a label and a trailing value, used in lists inside cards. */
@Composable
internal fun LineRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    val p = palette
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = p.ink, modifier = Modifier.weight(1f), maxLines = 2)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight(600), color = valueColor ?: p.muted, maxLines = 1)
    }
}
