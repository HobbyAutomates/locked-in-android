package com.sohum.bandlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = p.card, dragHandle = null, tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
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
            content()
            if (primary != null) {
                Spacer(Modifier.height(14.dp))
                PillButton(primary, onPrimary, enabled = primaryEnabled)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
