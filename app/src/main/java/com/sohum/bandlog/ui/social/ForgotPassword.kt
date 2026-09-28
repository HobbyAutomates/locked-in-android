package com.sohum.bandlog.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.SocialApi
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.theme.palette
import kotlinx.coroutines.launch

/**
 * v2.18 E5 "Forgot password?" under the sign-in form: Supabase POST /auth/v1/recover sends a reset
 * link that opens the web's /reset page. (Google sign-in: not built, no OAuth client exists; see
 * util/Safety.GOOGLE_SIGN_IN_ENABLED.)
 */
@Composable
fun ForgotPasswordLink(prefill: String) {
    val p = palette
    var open by remember { mutableStateOf(false) }
    Text(
        "Forgot password?", fontSize = 14.sp, fontWeight = FontWeight(600), color = p.muted,
        modifier = Modifier.heightIn(min = 44.dp).clickable { open = true }.padding(vertical = 12.dp),
    )
    if (open) ForgotDialog(prefill) { open = false }
}

@Composable
private fun ForgotDialog(prefill: String, onDismiss: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(prefill) }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(24.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Reset your password", fontSize = 19.sp, fontWeight = FontWeight(800), color = p.ink)
            if (sent) {
                Text("Check $email for a link to set a new password. It can take a minute; look in spam too.", fontSize = 14.sp, color = p.muted, lineHeight = 20.sp)
                PillButton("OK", onDismiss)
                return@Column
            }
            Text("We'll email you a link to set a new one.", fontSize = 14.sp, color = p.muted)
            SocialField(email, { email = it.trim() }, "you@example.com", keyboard = KeyboardType.Email, label = "Email")
            ErrorNote(err)
            PillButton(if (busy) "Sending…" else "Send the link", {
                if (busy) return@PillButton
                if (!email.contains('@')) { err = "Enter your email"; return@PillButton }
                scope.launch {
                    busy = true; err = null
                    try { SocialApi.recover(email); sent = true } catch (e: Exception) { err = e.message ?: "Couldn't send the email" }
                    busy = false
                }
            }, enabled = email.isNotBlank(), bg = Ember, fg = BoneBrand)
            Text(tr("common.cancel"), fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 12.dp))
        }
    }
}
