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

    // ---- v2.9: deleting posts (the v2.7 undo) + "Auto-post my logs here" ----
    /** Posts hidden while their 5 s undo window runs; the delete lands when it ends. */
    var pendingDeletes by mutableStateOf<Set<String>>(emptySet()); private set
    /** The post the "Post deleted · Undo" snackbar is about. */
    var deletedSnack by mutableStateOf<String?>(null); private set
    private val deleteJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    /** My group_members.auto_post for the open squad; null while unknown or when the column isn't there yet (switch hidden). */
    var autoPost by mutableStateOf<Boolean?>(null); private set
    /** What Chat / Feed show: everything except posts inside their undo window. */
    // ---- v2.11: reactions, read receipts, unread (schema_v35; all hidden until it's applied) ----
    /** Unread Chat posts per squad id (squad list + the Chat tab's badge). */
    var unread by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    /** The open squad's members with last_read_at; null = unknown / v35 not applied (no ticks). */
    var reads by mutableStateOf<List<com.sohum.bandlog.util.Reactions.ReadRow>?>(null); private set
    /** Optimistic reactions: shown over the server's until the save lands (or fails and reverts). */
    private var reactOverrides by mutableStateOf<Map<String, com.sohum.bandlog.util.Reactions.State>>(emptyMap())
    /** The "who reacted" sheet: its post id and rows (null while loading). */
    var reactorsFor by mutableStateOf<String?>(null); private set
    var reactors by mutableStateOf<List<com.sohum.bandlog.data.PostReactor>?>(null); private set

    fun reactionState(post: GroupPost): com.sohum.bandlog.util.Reactions.State =
        reactOverrides[post.id] ?: com.sohum.bandlog.util.Reactions.State(post.reactions, post.myReaction)

    /**
     * Tap an emoji (bar, or "double_tap" for the Feed's quick ❤️, or "sheet" to remove mine): same
     * one removes it, another replaces it. Shows at once; reverts with an error if the save fails.
     */
    fun react(post: GroupPost, emoji: String, via: String = "bar") {
        val cur = reactionState(post)
        val change = (if (via == "double_tap") com.sohum.bandlog.util.Reactions.quickHeart(cur) else com.sohum.bandlog.util.Reactions.toggle(cur, emoji)) ?: return
        error = null
        reactOverrides = reactOverrides + (post.id to change.state)
        viewModelScope.launch {
            try {
                Api.setReaction(post.id, change.save)
                if (change.save != null) com.sohum.bandlog.data.Analytics.track("reaction_added", "emoji" to change.save, "kind" to post.kind, "replaced" to (cur.mine != null), "via" to via)
                posts = posts.map { if (it.id == post.id) it.copy(reactions = change.state.counts, myReaction = change.state.mine) else it }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                error = if (com.sohum.bandlog.util.Reactions.missingV35(e.message)) "Reactions are coming with the next server update. Try again soon." else friendly(e.message)
            } finally {
                if (reactOverrides[post.id] === change.state) reactOverrides = reactOverrides - post.id
            }
        }
    }

    fun openReactors(postId: String) {
        reactorsFor = postId; reactors = null
        viewModelScope.launch {
            runCatching { Api.postReactors(postId) }
                .onSuccess { if (reactorsFor == postId) reactors = it }
                .onFailure { e -> if (reactorsFor == postId) { reactorsFor = null; error = if (com.sohum.bandlog.util.Reactions.missingV35(e.message)) "Reactions are coming with the next server update." else friendly(e.message) } }
        }
    }

    fun closeReactors() { reactorsFor = null; reactors = null }

    /** Chat is open and visible with new messages: mark the squad read (the caller debounces) and clear its badge. */
    fun markRead() {
        val id = openId ?: return
        if ((unread[id] ?: 0) > 0) unread = unread - id
        viewModelScope.launch { runCatching { Api.markRead(id) } }
    }

    fun loadUnread() { viewModelScope.launch { runCatching { Api.myUnreadCounts() }.onSuccess { unread = it } } }

    val visiblePosts: List<GroupPost> get() = if (pendingDeletes.isEmpty()) posts else posts.filter { it.id !in pendingDeletes }

    // ---- v2.7: Squad Food Battle (docs/food-battle-spec.md) ----
    /** groups.battle_enabled for the open squad; false until [loadBattle] reads it. */
    var battleEnabled by mutableStateOf(false); private set
    /** Today's live board from `battle_board`, in the SQL's own leader-first order. */
    var battleBoard by mutableStateOf<List<com.sohum.bandlog.data.BattleRepo.BattleRow>>(emptyList()); private set
    /** Yesterday's winner (pinned crown card), from `battle_close` — null once there's nothing to show. */
    var battleCrown by mutableStateOf<com.sohum.bandlog.data.BattleRepo.BattleWinner?>(null); private set
    var battleLoading by mutableStateOf(false); private set
    var battleBusy by mutableStateOf(false); private set

    /** v2.3 Discover: public squads, or null when the `public_groups` RPC isn't there (section hidden). */
    var discover by mutableStateOf<List<com.sohum.bandlog.data.PublicSquad>?>(null); private set
    var joiningId by mutableStateOf<String?>(null); private set

    // ---- v2.7: challenges for the open squad ----
    /** Null until group_challenge_list has answered once. */
    var challenges by mutableStateOf<List<com.sohum.bandlog.data.Challenge>?>(null); private set
    /** False once group_challenge_list has failed (the tab explains it isn't live yet). */
    var challengesSupported by mutableStateOf<Boolean?>(null); private set
    var challengeOpenId by mutableStateOf<String?>(null)
    var challengeBoard by mutableStateOf<List<com.sohum.bandlog.data.ChallengeBoardRow>>(emptyList()); private set
    var challengeBoardLoading by mutableStateOf(false); private set
    val openChallenge: com.sohum.bandlog.data.Challenge? get() = challenges?.firstOrNull { it.id == challengeOpenId }

    fun loadDiscover() { viewModelScope.launch { discover = runCatching { Api.publicGroups() }.getOrNull() } }

    fun load() {
        viewModelScope.launch {
            loading = true
            try {
                squads = Api.mySquads()
                loaded = true
                loadUnread()
            } catch (e: Exception) { error = e.message ?: "Couldn't load your squads" } finally { loading = false }
        }
    }

    private suspend fun reloadSquads() { runCatching { squads = Api.mySquads() }; loaded = true }

    fun openSquad(id: String, info: Boolean = false) {
        if (openId != id) { reads = null; reactOverrides = emptyMap(); posts = emptyList(); leaders = emptyList(); members = emptyList(); requests = emptyList(); board = emptyList(); challenges = null; challengeOpenId = null; challengeBoard = emptyList(); battleBoard = emptyList(); battleCrown = null }
        openId = id; infoOpen = info
        loadPage(id)
        loadBattle(id)
    }

    fun close() { openId = null; infoOpen = false; challengeOpenId = null; autoPost = null; closeReactors(); loadUnread() }

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
                val ch = async { runCatching { Api.groupChallenges(id) } }
                val ap = async { Api.myAutoPost(id) }
                val rd = async { runCatching { Api.groupReadStatus(id) }.getOrNull() }
                val sq = squads.firstOrNull { it.id == id }
                val r = async { if (sq != null && sq.ownerId == Session.userId) runCatching { Api.joinRequests(id) }.getOrDefault(emptyList()) else emptyList() }
                f.await().onSuccess { posts = it; feedSupported = true }.onFailure { if (feedSupported != true) feedSupported = false }
                board = b.await(); sent = n.await(); requests = r.await()
                // Degrade to the v2.0 board when the v2.6 RPCs aren't there yet.
                leaders = l.await()?.sortedWith(compareBy<LeaderRow> { if (it.rank > 0) it.rank else Int.MAX_VALUE }.thenByDescending { it.flames }.thenByDescending { it.points })
                    ?: board.map { LeaderRow(it.userId, it.name, null, it.avatarPath, it.weekStreak, 0) }.sortedByDescending { it.flames }
                members = m.await() ?: board.map { MemberDetail(it.userId, it.name, null, it.avatarPath, it.isOwner, it.weekStreak, "") }
                ch.await().onSuccess { if (openId == id) { challenges = it; challengesSupported = true } }.onFailure { if (challengesSupported != true) challengesSupported = false }
                if (openId == id) { autoPost = ap.await(); rd.await()?.let { reads = it } }
            }
            pageLoading = false
        }
    }

    /**
     * v2.7 Squad Food Battle: reads `groups.battle_enabled`, closes yesterday (idempotent — no
     * cron, the spec's "close on page load" design), then loads today's board. Safe to call every
     * time the squad opens; a battle-disabled squad just gets `battleEnabled = false`.
     */
    fun loadBattle(id: String = openId ?: "") {
        if (id.isBlank()) return
        viewModelScope.launch {
            battleLoading = true
            try {
                val enabled = runCatching { com.sohum.bandlog.data.BattleRepo.battleEnabled(id) }.getOrDefault(false)
                battleEnabled = enabled
                if (enabled) {
                    val today = com.sohum.bandlog.util.Dates.today()
                    val yesterday = com.sohum.bandlog.util.Dates.addDays(today, -1)
                    battleCrown = runCatching { com.sohum.bandlog.data.BattleRepo.closeDay(id, yesterday) }.getOrNull()
                    battleBoard = runCatching { com.sohum.bandlog.data.BattleRepo.board(id, today) }.getOrDefault(emptyList())
                } else { battleBoard = emptyList(); battleCrown = null }
            } finally { battleLoading = false }
        }
    }

    /** Re-reads just today's board (after a Snap, or a pull-to-refresh). */
    fun refreshBattleBoard() {
        val id = openId ?: return
        if (!battleEnabled) return
        viewModelScope.launch {
            val today = com.sohum.bandlog.util.Dates.today()
            runCatching { com.sohum.bandlog.data.BattleRepo.board(id, today) }.onSuccess { if (openId == id) battleBoard = it }
        }
    }

    /** Owner-only toggle (Squad info page): flips `groups.battle_enabled`. */
    fun toggleBattle(id: String, enabled: Boolean) {
        viewModelScope.launch {
            battleBusy = true; error = null
            try {
                if (!com.sohum.bandlog.data.BattleRepo.setBattleEnabled(id, enabled)) throw IllegalStateException("Only the squad's owner can change this")
                battleEnabled = enabled
                if (enabled) loadBattle(id) else { battleBoard = emptyList(); battleCrown = null }
            } catch (e: Exception) { error = friendly(e.message) } finally { battleBusy = false }
        }
    }

    /** Chat polls this every 5 s while it's on screen ([withReads]: also the read receipts). */
    fun refreshFeed(withReads: Boolean = false) {
        val id = openId ?: return
        if (feedSupported == false) return
        viewModelScope.launch {
            runCatching { Api.groupFeed(id) }.onSuccess { if (openId == id) { posts = it; feedSupported = true } }
            if (withReads) runCatching { Api.groupReadStatus(id) }.onSuccess { if (openId == id) reads = it }
        }
    }

    /**
     * The Challenges tab: reload the list, then post 🏆 for any active challenge I've just finished
     * (idempotent through ref_id) and refresh the feed when one went up.
     */
    fun loadChallenges() {
        val id = openId ?: return
        viewModelScope.launch {
            runCatching { Api.groupChallenges(id) }
                .onSuccess { list ->
                    if (openId == id) { challenges = list; challengesSupported = true }
                    val posted = runCatching { Api.postChallengeCompletions(id, list) }.getOrDefault(0)
                    if (posted > 0) refreshFeed()
                }
                .onFailure { if (challengesSupported != true) { challengesSupported = false; if (challenges == null) challenges = emptyList() } }
        }
    }

    fun openChallenge(challengeId: String) {
        challengeOpenId = challengeId
        challengeBoard = emptyList()
        viewModelScope.launch {
            challengeBoardLoading = true
            runCatching { Api.challengeBoard(challengeId) }
                .onSuccess { if (challengeOpenId == challengeId) challengeBoard = it }
                .onFailure { error = friendly(it.message) }
            challengeBoardLoading = false
        }
    }

    fun closeChallenge() { challengeOpenId = null; challengeBoard = emptyList() }

    /** create_challenge, then reload the list and the feed (the server posts "🏁 started"). Calls [onDone] on success. */
    fun startChallenge(kind: String, title: String, targetDays: Int, proteinTarget: Int?, startsOn: String, endsOn: String, onDone: () -> Unit) {
        val id = openId ?: return
        viewModelScope.launch {
            busy = true; error = null
            try {
                Api.createChallenge(id, kind, title, targetDays, if (kind == com.sohum.bandlog.util.ChallengeMath.PROTEIN) proteinTarget else null, startsOn, endsOn)
                com.sohum.bandlog.data.Analytics.track("challenge_created", "kind" to kind, "days" to targetDays)
                runCatching { challenges = Api.groupChallenges(id); challengesSupported = true }
                refreshFeed()
                onDone()
            } catch (e: Exception) { error = friendly(e.message) } finally { busy = false }
        }
    }

    fun deleteChallenge(challengeId: String) = act {
        if (!Api.deleteChallenge(challengeId)) throw IllegalStateException("Only whoever started it (or the squad owner) can delete this one")
        closeChallenge()
        openId?.let { g -> runCatching { challenges = Api.groupChallenges(g) } }
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

    /**
     * v2.9: delete a post (mine, or any as the squad owner) with the v2.7 undo. It disappears at
     * once, "Post deleted · Undo" shows for 5 s, then the delete runs on this ViewModel's scope,
     * so it still lands if the squad is closed first. No confirm: Undo covers a slip.
     */
    fun deletePost(post: GroupPost) {
        val id = post.id
        if (id in pendingDeletes) return
        error = null
        pendingDeletes = pendingDeletes + id
        deletedSnack = id
        deleteJobs[id] = viewModelScope.launch {
            kotlinx.coroutines.delay(5_000)
            if (deletedSnack == id) deletedSnack = null
            deleteJobs.remove(id)
            try {
                if (!Api.deleteGroupPost(id)) throw IllegalStateException("Only the author or the squad owner can delete this")
                com.sohum.bandlog.data.Analytics.track("post_deleted")
                posts = posts.filter { it.id != id }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = friendly(e.message) }
            finally { pendingDeletes = pendingDeletes - id }
        }
    }

    fun undoDelete(id: String) {
        deleteJobs.remove(id)?.cancel()
        pendingDeletes = pendingDeletes - id
        if (deletedSnack == id) deletedSnack = null
    }

    /** v2.9: "Auto-post my logs here" for the open squad; reverts the switch if the save fails. */
    fun setAutoPost(on: Boolean) {
        val id = openId ?: return
        val before = autoPost
        autoPost = on
        viewModelScope.launch {
            error = null
            runCatching { Api.setAutoPost(id, on) }.onFailure { e ->
                if (openId == id) autoPost = before
                error = if (com.sohum.bandlog.util.SquadSharing.missingColumn(e.message, "auto_post")) "This switch needs the latest server update. Try again soon." else friendly(e.message)
            }
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
            runCatching { Api.nudge(g, userId) }
                .onSuccess {
                    // v2.13 platform: push it to their iPhone / web app now (the server sends pending rows), and
                    // ask this user once for notification permission so nudges back reach them too.
                    com.sohum.bandlog.ui.platform.PlatformNav.askNotificationsTick++
                    com.sohum.bandlog.data.PlatformApi.dispatchPush(userId)
                }
                .onFailure { e -> sent = sent - userId; error = e.message ?: "Couldn't send the nudge" }
        }
    }

    /** Debug builds only (DebugPreviewActivity): canned data for layout screenshots. */
    internal fun debugSeed(squads: List<Squad>, posts: List<GroupPost>, leaders: List<LeaderRow>, members: List<MemberDetail>) {
        this.squads = squads; this.posts = posts; this.leaders = leaders; this.members = members; feedSupported = true; loaded = true
        openId = squads.firstOrNull()?.id
    }

    /** Debug builds only: canned challenges (and one board) for layout screenshots. */
    internal fun debugSeedChallenges(list: List<com.sohum.bandlog.data.Challenge>, board: List<com.sohum.bandlog.data.ChallengeBoardRow> = emptyList(), openId: String? = null) {
        challenges = list; challengesSupported = true; challengeBoard = board; challengeOpenId = openId
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
                            // v2.11: unread Chat posts (schema_v35); the Private/Public pill otherwise.
                            val badge = com.sohum.bandlog.util.Reactions.unreadLabel(sq.unread[s.id])
                            if (badge != null) UnreadBadge(badge, "${sq.unread[s.id]} unread")
                            else Box(Modifier.background(p.card2, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
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
            "Challenges: anyone can start one (train days, protein days or logging streaks) and the whole squad's in automatically.",
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
