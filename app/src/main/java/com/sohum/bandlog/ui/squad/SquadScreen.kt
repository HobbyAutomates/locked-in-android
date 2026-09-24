package com.sohum.bandlog.ui.squad

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.GroupPost
import com.sohum.bandlog.data.JoinRequest
import com.sohum.bandlog.data.LeaderRow
import com.sohum.bandlog.data.MemberDetail
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.Squad
import com.sohum.bandlog.data.SquadMember
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.PeopleIcon
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.ScreenTitle
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Squads state (activity-scoped, shared by the Squad tab and the shell's full-screen squad pages):
 * my squads, the open squad's chat / feed / leaderboard / members / requests, and the flows.
 */
class SquadViewModel : ViewModel() {
    var squads by mutableStateOf<List<Squad>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    /** A short success line ("Request sent — the owner will let you in"). */
    var notice by mutableStateOf<String?>(null)

    // ---- which full-screen page is up (rendered by the shell over the tabs) ----
    var openId by mutableStateOf<String?>(null); private set
    var infoOpen by mutableStateOf(false)
    var creating by mutableStateOf(false)
    var profileFlow by mutableStateOf(false)
    /** "Back" on the username flow: don't pop it again this session. */
    var profileFlowDismissed by mutableStateOf(false)

    val open: Squad? get() = squads.firstOrNull { it.id == openId }

    // ---- the open squad ----
    var posts by mutableStateOf<List<GroupPost>>(emptyList()); private set
    /** False once group_feed has 404'd (Chat and Feed tabs are hidden); null until the first read. */
    var feedSupported by mutableStateOf<Boolean?>(null); private set
    var leaders by mutableStateOf<List<LeaderRow>>(emptyList()); private set
    var members by mutableStateOf<List<MemberDetail>>(emptyList()); private set
    var requests by mutableStateOf<List<JoinRequest>>(emptyList()); private set
    /** The v2.0 board: who trained today (members list nudges). */
    var board by mutableStateOf<List<SquadMember>>(emptyList()); private set
    var sent by mutableStateOf<Set<String>>(emptySet()); private set
    var pageLoading by mutableStateOf(false); private set
    var posting by mutableStateOf(false); private set

    /** v2.3 Discover: public squads, or null when the `public_groups` RPC isn't there (section hidden). */
    var discover by mutableStateOf<List<com.sohum.bandlog.data.PublicSquad>?>(null); private set
    var joiningId by mutableStateOf<String?>(null); private set

    fun loadDiscover() { viewModelScope.launch { discover = runCatching { Api.publicGroups() }.getOrNull() } }

    fun load() {
        viewModelScope.launch {
            loading = true
            try {
                squads = Api.mySquads()
                loaded = true
            } catch (e: Exception) { error = e.message ?: "Couldn't load your squads" } finally { loading = false }
        }
    }

    private suspend fun reloadSquads() { runCatching { squads = Api.mySquads() }; loaded = true }

    fun openSquad(id: String, info: Boolean = false) {
        if (openId != id) { posts = emptyList(); leaders = emptyList(); members = emptyList(); requests = emptyList(); board = emptyList() }
        openId = id; infoOpen = info
        loadPage(id)
    }

    fun close() { openId = null; infoOpen = false }

    fun loadPage(id: String = openId ?: "") {
        if (id.isBlank()) return
        viewModelScope.launch {
            pageLoading = true
            coroutineScope {
                val f = async { runCatching { Api.groupFeed(id) } }
                val b = async { runCatching { Api.squadBoard(id) }.getOrDefault(emptyList()) }
                val l = async { runCatching { Api.groupLeaderboard(id) }.getOrNull() }
                val m = async { runCatching { Api.groupMembersDetail(id) }.getOrNull() }
                val n = async { runCatching { Api.sentNudges() }.getOrDefault(sent) }
                val sq = squads.firstOrNull { it.id == id }
                val r = async { if (sq != null && sq.ownerId == Session.userId) runCatching { Api.joinRequests(id) }.getOrDefault(emptyList()) else emptyList() }
                f.await().onSuccess { posts = it; feedSupported = true }.onFailure { if (feedSupported != true) feedSupported = false }
                board = b.await(); sent = n.await(); requests = r.await()
                // Degrade to the v2.0 board when the v2.6 RPCs aren't there yet.
                leaders = l.await()?.sortedWith(compareBy<LeaderRow> { if (it.rank > 0) it.rank else Int.MAX_VALUE }.thenByDescending { it.flames }.thenByDescending { it.points })
                    ?: board.map { LeaderRow(it.userId, it.name, null, it.avatarPath, it.weekStreak, 0) }.sortedByDescending { it.flames }
                members = m.await() ?: board.map { MemberDetail(it.userId, it.name, null, it.avatarPath, it.isOwner, it.weekStreak, "") }
            }
            pageLoading = false
        }
    }

    /** Chat polls this every 5 s while it's on screen. */
    fun refreshFeed() {
        val id = openId ?: return
        if (feedSupported == false) return
        viewModelScope.launch { runCatching { Api.groupFeed(id) }.onSuccess { if (openId == id) { posts = it; feedSupported = true } } }
    }

    fun send(text: String) {
        val id = openId ?: return
        val body = text.trim().take(500)
        if (body.isBlank()) return
        viewModelScope.launch {
            posting = true; error = null
            try { Api.postToGroups(listOf(id), "message", body); runCatching { posts = Api.groupFeed(id) } }
            catch (e: Exception) { error = friendly(e.message) } finally { posting = false }
        }
    }

    fun postPhoto(bmp: android.graphics.Bitmap) {
        val id = openId ?: return
        viewModelScope.launch {
            posting = true; error = null
            try {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.sohum.bandlog.util.Images.jpeg(com.sohum.bandlog.util.Images.fitWithin(bmp, 1080), 84) }
                val path = Api.uploadGroupPhoto(bytes)
                Api.postToGroups(listOf(id), "photo", "shared a photo", null, path)
                runCatching { posts = Api.groupFeed(id) }
            } catch (e: Exception) { error = friendly(e.message) } finally { posting = false }
        }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true; error = null
            try { block() } catch (e: Exception) { error = friendly(e.message) } finally { busy = false }
        }
    }

    private fun friendly(msg: String?): String = when {
        msg == null -> "Something went wrong"
        msg.contains("No group", ignoreCase = true) -> "No squad with that code"
        msg.contains("(404)") -> "Squads v2 isn't live on the server yet"
        else -> msg.substringAfter(": ", msg)
    }

    private fun needsRequest(msg: String?): Boolean =
        msg != null && listOf("request", "private", "approval", "approve", "join_policy").any { msg.contains(it, ignoreCase = true) }

    /**
     * v2.6 create: `create_group`, then PATCH description / icon / tags / join_policy (and upload
     * the photo when one was picked instead of a preset); opens the new squad's invite page.
     */
    fun createV2(name: String, description: String, iconKey: String, photo: android.graphics.Bitmap?, tags: List<String>, private: Boolean, display: String) = act {
        val (id, _) = Api.createSquad(name.trim().take(40), display)
        var icon: String? = iconKey
        var coverUrl: String? = null
        if (photo != null) {
            runCatching {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.sohum.bandlog.util.Images.jpeg(com.sohum.bandlog.util.Images.squareCrop(photo, 512), 85) }
                coverUrl = Api.uploadSquadPhoto(bytes, "squad-${System.currentTimeMillis()}")
                icon = null // web format: an uploaded photo lives in cover_url, icon stays null
            }
        }
        val fields = org.json.JSONObject().put("description", description.trim().take(200)).put("icon", icon ?: org.json.JSONObject.NULL)
            .put("cover_url", coverUrl ?: org.json.JSONObject.NULL)
            .put("tags", org.json.JSONArray(tags)).put("join_policy", if (private) "request" else "open")
        if (!Api.patchGroup(id, fields)) {
            // Older server: try the columns one by one so whichever exist still save.
            fields.keys().forEach { k -> Api.patchGroup(id, org.json.JSONObject().put(k, fields.get(k))) }
        }
        reloadSquads()
        creating = false
        openSquad(id, info = true)
    }

    /**
     * Invite code / link: `group_by_code` → `request_join(g)` ('member' | 'joined' | 'requested');
     * on an older server, `join_group` (which files a request itself for request-only squads).
     */
    fun joinByCode(code: String, display: String) = act {
        val found = runCatching { Api.groupByCode(code) }
        if (found.isSuccess) {
            val g = found.getOrNull() ?: throw IllegalStateException("No squad with that code")
            val id = g.getString("id")
            when (Api.requestJoin(id)) {
                "requested" -> notice = "Request sent to ${g.optString("name").ifBlank { "the squad" }} — the owner will let you in"
                else -> { reloadSquads(); if (squads.any { it.id == id }) openSquad(id) else notice = "You're in" }
            }
            return@act
        }
        try {
            val id = Api.joinSquad(code, display)
            reloadSquads()
            if (squads.any { it.id == id }) openSquad(id) else notice = "You're in"
        } catch (e: Exception) {
            if (!needsRequest(e.message)) throw e
            val gid = Api.groupIdForCode(code) ?: throw e
            Api.requestJoin(gid)
            notice = "Request sent — the owner will let you in"
        }
    }

    fun joinPublic(id: String, display: String) {
        viewModelScope.launch {
            joiningId = id; error = null
            try {
                val status = runCatching { Api.requestJoin(id) }.getOrElse { Api.joinPublicSquad(id, display); "joined" }
                if (status == "requested") notice = "Request sent — the owner will let you in"
                else { reloadSquads(); loadDiscover(); openSquad(id) }
            }
            catch (e: Exception) {
                if (needsRequest(e.message)) runCatching { Api.requestJoin(id); notice = "Request sent — the owner will let you in" }.onFailure { error = friendly(it.message) }
                else error = friendly(e.message)
            }
            finally { joiningId = null }
        }
    }

    fun approve(r: JoinRequest) = act { Api.approveJoin(r.id); requests = requests - r; loadPage() }
    fun decline(r: JoinRequest) = act { Api.declineJoin(r.id); requests = requests - r }

    fun rename(id: String, name: String) = act {
        if (!Api.renameSquad(id, name.trim().take(40))) throw IllegalStateException("Only the squad's owner can rename it")
        reloadSquads()
    }

    fun setPrivate(id: String, private: Boolean) = act {
        if (!Api.patchGroup(id, org.json.JSONObject().put("join_policy", if (private) "request" else "open"))) throw IllegalStateException("Couldn't change that — only the owner can")
        reloadSquads()
    }

    fun leave(id: String) = act {
        Api.leaveSquad(id)
        close()
        reloadSquads()
    }

    fun nudge(userId: String) {
        val g = openId ?: return
        sent = sent + userId
        viewModelScope.launch {
            runCatching { Api.nudge(g, userId) }.onFailure { e -> sent = sent - userId; error = e.message ?: "Couldn't send the nudge" }
        }
    }

    /** Debug builds only (DebugPreviewActivity): canned data for layout screenshots. */
    internal fun debugSeed(squads: List<Squad>, posts: List<GroupPost>, leaders: List<LeaderRow>, members: List<MemberDetail>) {
        this.squads = squads; this.posts = posts; this.leaders = leaders; this.members = members; feedSupported = true; loaded = true
        openId = squads.firstOrNull()?.id
    }
}

/**
 * The Squad tab (v2.6): my squads as cards (tap → the squad's Chat · Feed · Leaderboard page),
 * Discover public squads, and create / join. The one-time username + photo flow runs first.
 */
@Composable
fun SquadScreen(vm: AppViewModel, onOpenProfile: () -> Unit) {
    val p = palette
    val sq: SquadViewModel = viewModel()
    var menu by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var howItWorks by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sq.load(); sq.loadDiscover() }
    LaunchedEffect(vm.profile.usernameSupported, vm.profile.username, vm.loadedOnce) {
        if (vm.loadedOnce && vm.profile.usernameSupported && vm.profile.username == null && !sq.profileFlowDismissed) sq.profileFlow = true
    }
    val display = com.sohum.bandlog.util.Names.display(vm.profile.name, Session.email, "Member")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PaddingValues(16.dp, 12.dp, 16.dp, 110.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Rise(0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    ScreenTitle("Squad")
                    vm.profile.username?.let { Text("@$it", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.clickable { sq.profileFlow = true }) }
                }
                if (sq.loading || sq.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.muted)
                else Box {
                    Box(
                        Modifier.size(44.dp).pressable().shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow)
                            .background(p.btn, CircleShape).clickable { menu = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("+", fontSize = 24.sp, fontWeight = FontWeight(600), color = p.btnInk, modifier = Modifier.padding(bottom = 2.dp)) }
                    androidx.compose.material3.DropdownMenu(menu, { menu = false }, Modifier.background(p.card)) {
                        listOf("Create a squad", "Join with code", "How squads work").forEachIndexed { i, label ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label, fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink) },
                                onClick = { menu = false; when (i) { 0 -> sq.creating = true; 1 -> joining = true; else -> howItWorks = true } },
                            )
                        }
                    }
                }
            }
        }
        ErrorNote(sq.error)
        sq.notice?.let { n ->
            Card(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n, fontSize = 14.sp, fontWeight = FontWeight(600), color = p.ink, modifier = Modifier.weight(1f))
                    Text("OK", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted, modifier = Modifier.clickable { sq.notice = null }.padding(8.dp))
                }
            }
        }

        if (sq.loaded && (sq.squads.isEmpty() || joining)) {
            StartCards(sq.busy, showCreate = sq.squads.isEmpty(), onCreate = { sq.creating = true }, onJoin = { sq.joinByCode(it, display); joining = false })
        }

        if (sq.squads.isNotEmpty()) {
            Text("Your squads", fontSize = 17.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = p.ink, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
            sq.squads.forEachIndexed { i, s ->
                Rise(1 + i) {
                    Card(onClick = { sq.openSquad(s.id) }, padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SquadIconView(s.icon, s.name, 52.dp, cover = s.coverUrl)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
                                Text(
                                    s.description.ifBlank { if (s.ownerId == Session.userId) "You own this squad" else "Tap for chat, feed and the leaderboard" },
                                    fontSize = 12.sp, color = p.muted, maxLines = 2,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.background(p.card2, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(if (s.isPrivate) "Private" else "Public", fontSize = 11.sp, fontWeight = FontWeight(700), color = p.muted)
                            }
                        }
                    }
                }
            }
        }

        // v2.3 Discover: public squads I'm not in; hidden when the RPC isn't live yet or there are none.
        val publicSquads = sq.discover?.filter { d -> sq.squads.none { it.id == d.id } }.orEmpty()
        if (publicSquads.isNotEmpty()) {
            Rise(2 + sq.squads.size) { DiscoverSection(publicSquads, sq.joiningId) { sq.joinPublic(it, display) } }
        }

        Rise(3 + sq.squads.size) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenProfile).padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                Text("You share ${if (vm.profile.shareStats) "streaks, meals + protein & calories" else "streaks and workouts only"} · ", fontSize = 12.sp, color = p.muted)
                Text("change", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink)
            }
        }
    }
    if (howItWorks) HowSquadsWorkSheet { howItWorks = false }
}

@Composable
private fun DiscoverSection(squads: List<com.sohum.bandlog.data.PublicSquad>, joiningId: String?, onJoin: (String) -> Unit) {
    val p = palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Discover squads", fontSize = 17.sp, fontWeight = FontWeight(800), letterSpacing = (-0.4).sp, color = p.ink, modifier = Modifier.padding(start = 2.dp))
        squads.take(8).forEach { s ->
            Card(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.icon == null && s.coverUrl != null) com.sohum.bandlog.ui.components.RemoteImage(url = s.coverUrl, size = 52.dp, radius = 26.dp, fallback = PeopleIcon)
                    else SquadIconView(s.icon, s.name, 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
                        Text("${s.memberCount} member${if (s.memberCount == 1) "" else "s"}", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted)
                        if (s.tagline.isNotBlank()) Text(s.tagline, fontSize = 12.sp, color = p.muted, maxLines = 2)
                    }
                    Spacer(Modifier.width(8.dp))
                    val busy = joiningId == s.id
                    Box(Modifier.heightIn(min = 44.dp).pressable().clickable(enabled = joiningId == null) { onJoin(s.id) }, contentAlignment = Alignment.Center) {
                        Box(Modifier.height(34.dp).background(p.btn, CircleShape).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text(if (busy) "Joining…" else if (s.joinPolicy == "request") "Ask to join" else "+ Join", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.btnInk, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** A short explainer behind the + menu. */
@Composable
private fun HowSquadsWorkSheet(onDismiss: () -> Unit) {
    val p = palette
    com.sohum.bandlog.ui.components.BottomSheet(title = "How squads work", onDismiss = onDismiss, primary = "Got it", onPrimary = onDismiss) {
        listOf(
            "Create a squad, pick an icon, and share its invite link — WhatsApp works best.",
            "Public squads show up under Discover. Private ones need the owner to approve a request (invite links still let friends straight in).",
            "Chat with the squad; meals, workouts and PRs you log land in its Feed automatically.",
            "The Leaderboard ranks everyone by their streak flames.",
            "Meals show only when you share stats (Profile → Share with squads).",
        ).forEach { line ->
            Row(Modifier.padding(bottom = 10.dp)) {
                Box(Modifier.padding(top = 6.dp).size(6.dp).background(p.ink, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(line, fontSize = 13.sp, color = p.ink, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun StartCards(busy: Boolean, showCreate: Boolean, onCreate: () -> Unit, onJoin: (String) -> Unit) {
    val p = palette
    var code by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    if (showCreate) Rise(1) {
        Card(padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SquadIconView("biceps", "", 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Create a squad", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text("Chat, a shared feed and a streak leaderboard", fontSize = 12.sp, color = p.muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            PillButton("Create a squad", onCreate, height = 48.dp)
        }
    }
    Rise(2) {
        Card(padding = 18.dp) {
            Text("Join with a code", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
            Text("Ask a friend for their squad code or invite link", fontSize = 12.sp, color = p.muted)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(46.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    BasicTextField(
                        code, { code = it.substringAfterLast("/join/").filter { c -> c.isLetterOrDigit() }.uppercase().take(6) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (code.length == 6) { focus.clearFocus(); onJoin(code) } }),
                        textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight(800), color = p.ink, letterSpacing = 5.sp, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(p.ink), modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (code.isEmpty()) Text("LOCK7Q", fontSize = 20.sp, fontWeight = FontWeight(800), color = p.muted.copy(alpha = 0.5f), letterSpacing = 5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            inner()
                        },
                    )
                }
                PillButton("Join", { focus.clearFocus(); onJoin(code) }, Modifier.width(96.dp), enabled = code.length == 6 && !busy, height = 46.dp)
            }
        }
    }
}
