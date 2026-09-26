package com.sohum.bandlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.ui.theme.palette

/**
 * The one bottom-sheet style in the app: grab handle, title (+ optional one-line subtitle and a
 * trailing slot), the content, then one black primary button. Used by the Quantity sheet,
 * Cooked in…, the scan lens picker, Ring colours and the plate's Fix / Save-as-repeat sheets.
 * Swipe down, back, or a tap on the scrim dismisses it — there is no Cancel button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    primary: String? = null,
    primaryEnabled: Boolean = true,
    onPrimary: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    // Previews / layout screenshots can't open a window: draw the sheet in place, pinned to the bottom.
    if (androidx.compose.ui.platform.LocalInspectionMode.current) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().background(p.card, shape)) {
                SheetBody(title, subtitle, primary, primaryEnabled, onPrimary, trailing, content)
            }
        }
        return
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = p.card, dragHandle = null, tonalElevation = 0.dp,
        shape = shape,
    ) {
        SheetBody(title, subtitle, primary, primaryEnabled, onPrimary, trailing, content)
    }
}

@Composable
private fun SheetBody(
    title: String, subtitle: String?, primary: String?, primaryEnabled: Boolean, onPrimary: () -> Unit,
    trailing: (@Composable () -> Unit)?, content: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    Column(Modifier.fillMaxWidth().padding(20.dp, 12.dp, 20.dp, 8.dp).navigationBarsPadding().imePadding()) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).background(p.hair, CircleShape))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = p.ink, lineHeight = 23.sp)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = p.muted, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(14.dp))
        // v2.13: the body scrolls when it's taller than the sheet ("What's new" and friends were cut
        // off). The title stays put and the primary button stays pinned under it. Content must not
        // nest its own unbounded vertical scroll (bounded ones, heightIn(max = …), are fine).
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
        if (primary != null) {
            Spacer(Modifier.height(14.dp))
            PillButton(primary, onPrimary, enabled = primaryEnabled)
        }
        Spacer(Modifier.height(8.dp))
    }
}
