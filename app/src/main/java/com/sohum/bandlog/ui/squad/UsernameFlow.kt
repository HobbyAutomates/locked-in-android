package com.sohum.bandlog.ui.squad

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Avatar
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.components.primeImageCache
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Images
import com.sohum.bandlog.util.Names
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

private val USERNAME_RE = Regex("^[a-z0-9_.]{3,20}$")

/**
 * v2.6 one-time squad profile (Cal AI): "Create a username" with a live availability check, then
 * "Add a profile photo" — swipe through 8 gradient-initials presets (or keep the current photo),
 * or choose / take a photo — then Create Profile. Presets are rendered to 512 px and uploaded
 * to avatars/<uid>/avatar.jpg like any other photo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UsernameFlow(vm: AppViewModel, initialStep: Int = 0, onClose: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val displayName = Names.display(vm.profile.name, Session.email, "Member")
    val initials = Names.initials(displayName)
    var step by remember { mutableIntStateOf(initialStep) }
    var username by remember { mutableStateOf(vm.profile.username ?: "") }
    /** null = unknown / checking / the RPC isn't there; true / false = the answer for [checkedFor]. */
    var available by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var photo by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val hasCurrent = vm.profile.avatarPath != null
    val presetCount = AVATAR_GRADIENTS.size + if (hasCurrent) 1 else 0
    val pager = rememberPagerState(initialPage = if (hasCurrent) 0 else 3) { presetCount }
    val valid = USERNAME_RE.matches(username)
    val unchanged = username == vm.profile.username

    LaunchedEffect(username) {
        available = null
        if (!valid || unchanged) return@LaunchedEffect
        checking = true
        delay(400)
        available = Api.usernameAvailable(username)
        checking = false
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { photo = withContext(Dispatchers.IO) { runCatching { com.sohum.bandlog.ui.scan.decodeScaled(ctx, uri, 1400) }.getOrNull() } }
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> if (bmp != null) photo = bmp }

    fun finish() {
        scope.launch {
            busy = true; error = null
            try {
                if (!unchanged && !vm.setUsername(username)) { error = "That username was just taken — try another"; step = 0; return@launch }
                val keepCurrent = photo == null && hasCurrent && pager.currentPage == 0
                if (!keepCurrent) {
                    val src = photo ?: renderPresetAvatar(pager.currentPage - if (hasCurrent) 1 else 0, initials)
                    val square = withContext(Dispatchers.Default) { Images.squareCrop(src, 512) }
                    val bytes = withContext(Dispatchers.Default) { Images.jpeg(square, 88) }
                    val path = Api.uploadAvatar(bytes)
                    Api.avatarUrl(path)?.let { primeImageCache(it, square) }
                    if (Api.patchProfile(org.json.JSONObject().put("avatar_path", path))) vm.setAvatarPathLocal(path)
                }
                onClose()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Couldn't save your profile" }
            finally { busy = false }
        }
    }

    androidx.activity.compose.BackHandler(enabled = step > 0) { step = 0 }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().imePadding()) {
        FlowTopBar(null) { if (step == 0) onClose() else step = 0 }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            if (step == 0) {
                Spacer(Modifier.height(40.dp))
                FlowHeading("Create a username", "This helps others find you in squads")
                OutlinedLabelField(
                    username, { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(20) }, "Username",
                    prefix = "@", capitalization = KeyboardCapitalization.None,
                    trailing = {
                        when {
                            checking -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.muted)
                            available == true || (unchanged && valid) -> Text("✓", fontSize = 20.sp, fontWeight = FontWeight(800), color = p.green)
                            available == false -> Text("✕", fontSize = 18.sp, fontWeight = FontWeight(800), color = p.red)
                        }
                    },
                )
                val hint = when {
                    username.isEmpty() -> "3–20 characters: letters, numbers, _ and ."
                    !valid -> "Use 3–20 letters, numbers, _ or ."
                    unchanged -> "That's your username"
                    checking -> "Checking…"
                    available == true -> "@$username is available"
                    available == false -> "@$username is taken"
                    else -> "We'll confirm it when you continue"
                }
                Text(hint, fontSize = 13.sp, color = if (available == false) p.red else if (available == true) p.green else p.muted, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
            } else {
                Spacer(Modifier.height(24.dp))
                FlowHeading("Add a profile photo", "Your profile photo helps others recognize you in squads.")
                val chosen = photo
                if (chosen != null) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(190.dp).border(4.dp, p.ink, CircleShape).padding(6.dp).clip(CircleShape)) {
                            Image(chosen.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Text("Use a preset instead", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.clickable { photo = null }.padding(14.dp))
                    }
                } else {
                    HorizontalPager(pager, Modifier.fillMaxWidth().height(200.dp), contentPadding = PaddingValues(horizontal = 72.dp)) { page ->
                        val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                        Box(Modifier.fillMaxSize().graphicsLayer { val s = 1f - 0.2f * off; scaleX = s; scaleY = s }, contentAlignment = Alignment.Center) {
                            val sel = page == pager.currentPage
                            Box(Modifier.then(if (sel) Modifier.border(4.dp, p.ink, CircleShape).padding(6.dp) else Modifier).clickable { scope.launch { pager.animateScrollToPage(page) } }) {
                                if (hasCurrent && page == 0) Avatar(Api.avatarUrl(vm.profile.avatarPath), initials, 140.dp)
                                else PresetAvatar(page - if (hasCurrent) 1 else 0, initials, 140.dp)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center) {
                        repeat(presetCount) { i ->
                            Box(Modifier.padding(horizontal = 4.dp).size(9.dp).background(if (i == pager.currentPage) p.ink else p.muted.copy(alpha = 0.4f), CircleShape))
                        }
                    }
                    Text(if (hasCurrent && pager.currentPage == 0) "Your current photo" else "Swipe to select", fontSize = 15.sp, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(p.hair))
                    Text("OR", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(horizontal = 16.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(p.hair))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Choose photo", "Take photo").forEachIndexed { i, label ->
                        Box(
                            Modifier.weight(1f).height(56.dp).pressable().border(1.5.dp, p.hair, CircleShape).clickable {
                                if (i == 0) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                else runCatching { take.launch(null) }.onFailure { error = "No camera app found — choose a photo instead." }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight(600), color = p.ink) }
                    }
                }
            }
            ErrorNote(error, Modifier.padding(top = 12.dp))
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp, 12.dp, 24.dp, 16.dp)) {
            if (step == 0) PillButton("Next", { step = 1 }, enabled = valid && available != false && !checking, height = 60.dp)
            else PillButton(if (busy) "Saving…" else "Create Profile", { finish() }, enabled = !busy, height = 60.dp)
        }
    }
}
