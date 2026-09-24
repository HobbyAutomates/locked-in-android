package com.sohum.bandlog.ui.squad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.sohum.bandlog.ui.components.CrossIcon
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.heightIn
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
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
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.Squad
import com.sohum.bandlog.data.SquadMember
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.CheckIcon
import com.sohum.bandlog.ui.components.CopyIcon
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.FistIcon
import com.sohum.bandlog.ui.components.Flame
import com.sohum.bandlog.ui.components.PeopleIcon
import com.sohum.bandlog.ui.components.PencilIcon
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.Rise
import com.sohum.bandlog.ui.components.ScreenTitle
import com.sohum.bandlog.ui.components.ShareIcon
import com.sohum.bandlog.ui.components.SmallChip
import com.sohum.bandlog.ui.components.pressable
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val APK_URL = "https://evizkfvltacrfngsgbuu.supabase.co/storage/v1/object/public/app/LockedIn-14.apk"
private const val WEB_URL = "https://web-production-ff1cf.up.railway.app"

fun squadInviteText(code: String) = "Join my Locked In squad: code $code — Android $APK_URL · iPhone $WEB_URL"

/** Squads state: my squads, the selected board, who I've nudged today. */
class SquadViewModel : ViewModel() {
    var squads by mutableStateOf<List<Squad>>(emptyList()); private set
    var selectedId by mutableStateOf<String?>(null); private set
    var board by mutableStateOf<List<SquadMember>>(emptyList()); private set
    var sent by mutableStateOf<Set<String>>(emptySet()); private set
    var loading by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    /** Set right after a create: (name, code) for the big shareable code card. */
    var created by mutableStateOf<Pair<String, String>?>(null)

    val selected: Squad? get() = squads.firstOrNull { it.id == selectedId }

    fun load(prefer: String? = selectedId) {
        viewModelScope.launch {
            loading = true
            try {
                coroutineScope {
                    val s = async { Api.mySquads() }
                    val n = async { runCatching { Api.sentNudges() }.getOrDefault(sent) }
                    squads = s.await(); sent = n.await()
                }
                selectedId = squads.firstOrNull { it.id == prefer }?.id ?: squads.firstOrNull()?.id
                board = selectedId?.let { runCatching { Api.squadBoard(it) }.getOrElse { e -> error = e.message; emptyList() } } ?: emptyList()
                loaded = true
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load your squads"
            } finally { loading = false }
        }
    }

    fun select(id: String) {
        if (id == selectedId) return
        selectedId = id; board = emptyList()
        viewModelScope.launch { board = runCatching { Api.squadBoard(id) }.getOrElse { e -> error = e.message; emptyList() } }
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
        else -> msg.substringAfter(": ", msg)
    }

    fun create(name: String, display: String) = act {
        val (id, code) = Api.createSquad(name.trim().take(40), display)
        created = name.trim() to code
        load(id)
    }

    fun join(code: String, display: String) = act {
        val id = Api.joinSquad(code, display)
        load(id)
    }

    fun rename(id: String, name: String) = act {
        if (!Api.renameSquad(id, name.trim().take(40))) throw IllegalStateException("Only the squad's owner can rename it")
        load(id)
    }

    fun leave(id: String) = act {
        Api.leaveSquad(id)
        load(null)
    }

    fun nudge(member: SquadMember) {
        val g = selectedId ?: return
        sent = sent + member.userId
        viewModelScope.launch {
            runCatching { Api.nudge(g, member.userId) }.onFailure { e -> sent = sent - member.userId; error = e.message ?: "Couldn't send the nudge" }
        }
    }
}

/**
 * The Squad tab: create a squad or join with a 6-letter code; once in, the board — who's locked in
 * today, this week's dots, streaks, protein & calories for members who share them, and a nudge for
 * anyone who hasn't trained yet.
 */
@Composable
fun SquadScreen(vm: AppViewModel, onOpenProfile: () -> Unit) {
    val p = palette
    val sq: SquadViewModel = viewModel()
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sq.load() }
    val display = com.sohum.bandlog.util.Names.display(vm.profile.name, Session.email, "Member")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PaddingValues(16.dp, 12.dp, 16.dp, 110.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Rise(0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Squad")
                if (sq.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = p.muted)
                else if (sq.squads.isNotEmpty()) Box(
                    Modifier.size(44.dp).pressable().shadow(8.dp, CircleShape, ambientColor = p.shadow, spotColor = p.shadow)
                        .background(if (adding) p.card else p.btn, CircleShape).clickable { adding = !adding },
                    contentAlignment = Alignment.Center,
                ) {
                    if (adding) Icon(CrossIcon, "Close", tint = p.ink, modifier = Modifier.size(14.dp))
                    else Text("+", fontSize = 24.sp, fontWeight = FontWeight(600), color = p.btnInk, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
        ErrorNote(sq.error)

        val created = sq.created
        if (created != null) {
            Rise(1) { CreatedCard(created.first, created.second, onDone = { sq.created = null; adding = false }) }
        } else if (sq.loaded && (sq.squads.isEmpty() || adding)) {
            StartCards(sq.busy, onCreate = { sq.create(it, display) }, onJoin = { sq.join(it, display); adding = false })
        }

        if (sq.squads.size > 1) {
            Rise(1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sq.squads.forEach { s -> SmallChip(s.name, { sq.select(s.id) }, filled = s.id == sq.selectedId) }
                }
            }
        }

        sq.selected?.let { squad -> Board(sq, squad, Session.userId.orEmpty(), vm.profile.shareStats, onOpenProfile) }
    }
}

@Composable
private fun StartCards(busy: Boolean, onCreate: (String) -> Unit, onJoin: (String) -> Unit) {
    val p = palette
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Rise(1) {
        Card(padding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(p.btn, CircleShape), contentAlignment = Alignment.Center) { Icon(PeopleIcon, null, tint = p.btnInk, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Create a squad", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
                    Text("Get a 6-letter code to share with friends", fontSize = 12.sp, color = p.muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it.take(40) }, "Squad name", Modifier.weight(1f)) { if (name.isNotBlank()) { focus.clearFocus(); onCreate(name) } }
                PillButton("Create", { focus.clearFocus(); onCreate(name) }, Modifier.width(96.dp), enabled = name.isNotBlank() && !busy, height = 46.dp)
            }
        }
    }
    Rise(2) {
        Card(padding = 18.dp) {
            Text("Join with a code", fontSize = 16.sp, fontWeight = FontWeight(700), color = p.ink)
            Text("Ask a friend for their squad code", fontSize = 12.sp, color = p.muted)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(46.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    BasicTextField(
                        code, { code = it.filter { c -> c.isLetterOrDigit() }.uppercase().take(6) }, singleLine = true,
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

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val p = palette
    Box(modifier.height(46.dp).background(p.card2, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value, onChange, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            textStyle = TextStyle(fontSize = 15.sp, color = p.ink), cursorBrush = SolidColor(p.ink), modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner -> if (value.isEmpty()) Text(hint, fontSize = 15.sp, color = p.muted, maxLines = 1); inner() },
        )
    }
}

private fun shareInvite(ctx: android.content.Context, code: String) {
    runCatching {
        ctx.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, squadInviteText(code)) }, "Invite to your squad"),
        )
    }
}

@Composable
private fun CreatedCard(name: String, code: String, onDone: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    Card(padding = 20.dp) {
        Text("$name is live", fontSize = 13.sp, fontWeight = FontWeight(700), color = p.muted)
        Text("Send your friends this code", fontSize = 15.sp, color = p.ink, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        CodePill(code, big = true) { shareInvite(ctx, code) }
        Spacer(Modifier.height(12.dp))
        PillButton("See the board", onDone, height = 48.dp, bg = p.card2, fg = p.ink)
    }
}

/** The squad code as one big pill: tap it to copy; the round button beside it shares an invite. */
@Composable
private fun CodePill(code: String, big: Boolean = false, onShare: () -> Unit) {
    val p = palette
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val h = if (big) 64.dp else 56.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.weight(1f).height(h).pressable().background(p.card2, CircleShape)
                .clickable { clip.setText(AnnotatedString(code)); copied = true; scope.launch { delay(1500); copied = false } }
                .padding(start = 22.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(code, fontSize = if (big) 30.sp else 24.sp, fontWeight = FontWeight(800), letterSpacing = if (big) 6.sp else 5.sp, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(if (copied) CheckIcon else CopyIcon, null, tint = if (copied) p.green else p.muted, modifier = Modifier.size(16.dp))
            Text(if (copied) " Copied" else " Copy", fontSize = 12.sp, fontWeight = FontWeight(700), color = if (copied) p.green else p.muted)
        }
        Box(Modifier.size(h).pressable().background(p.btn, CircleShape).clickable(onClick = onShare), contentAlignment = Alignment.Center) {
            Icon(ShareIcon, "Invite friends", tint = p.btnInk, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Board(sq: SquadViewModel, squad: Squad, me: String, shareStats: Boolean, onOpenProfile: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val today = Dates.today()
    val weekStart = Dates.weekStart(today)
    val days = (0..6).map { Dates.addDays(weekStart, it.toLong()) }
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH) }
    var renaming by remember(squad.id) { mutableStateOf(false) }
    var newName by remember(squad.id) { mutableStateOf(squad.name) }
    var confirmLeave by remember(squad.id) { mutableStateOf(false) }
    val isOwner = squad.ownerId == me
    val members = sq.board.sortedWith(compareByDescending<SquadMember> { it.weekStreak }.thenBy { if (it.userId == me) 0 else 1 })

    Rise(2) {
        Card(padding = 18.dp) {
            if (renaming) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(newName, { newName = it.take(40) }, "Squad name", Modifier.weight(1f)) { focus.clearFocus(); sq.rename(squad.id, newName); renaming = false }
                    PillButton("Save", { focus.clearFocus(); sq.rename(squad.id, newName); renaming = false }, Modifier.width(86.dp), enabled = newName.isNotBlank() && !sq.busy, height = 46.dp)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(squad.name, fontSize = 22.sp, fontWeight = FontWeight(800), letterSpacing = (-0.5).sp, color = p.ink, maxLines = 1, modifier = Modifier.weight(1f))
                    if (isOwner) Box(Modifier.size(32.dp).background(p.card2, CircleShape).clickable { renaming = true }, contentAlignment = Alignment.Center) {
                        Icon(PencilIcon, "Rename squad", tint = p.ink, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Text("${sq.board.size} member${if (sq.board.size == 1) "" else "s"} · tap the code to copy it", fontSize = 12.sp, color = p.muted, modifier = Modifier.padding(bottom = 10.dp))
            CodePill(squad.code) { shareInvite(ctx, squad.code) }
        }
    }

    members.forEachIndexed { i, m ->
        val todayRow = m.day(today)
        val trainedToday = todayRow?.trained == true
        val trainedDays = m.days.filter { it.trained }.map { it.date }.toSet()
        val isMe = m.userId == me
        val already = m.userId in sq.sent
        Rise(3 + i) {
            Card(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp)) {
                        com.sohum.bandlog.ui.components.Avatar(com.sohum.bandlog.data.Api.avatarUrl(m.avatarPath), com.sohum.bandlog.util.Names.initials(m.name), 44.dp)
                        Box(Modifier.align(Alignment.BottomEnd).size(13.dp).background(p.card, CircleShape).padding(2.dp).background(if (trainedToday) p.green else p.hair, CircleShape))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name + (if (isMe) " · you" else "") + (if (m.isOwner) " · owner" else ""), fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
                        Text(if (trainedToday) "Locked in today" else "Not yet today", fontSize = 12.sp, fontWeight = if (trainedToday) FontWeight(700) else FontWeight(500), color = if (trainedToday) p.green else p.muted)
                    }
                    Flame(p.flame, 18.dp)
                    Text(" ${m.weekStreak}", fontSize = 15.sp, fontWeight = FontWeight(800), color = p.ink)
                    if (!isMe && !trainedToday) {
                        Spacer(Modifier.width(6.dp))
                        // One tap, no confirm: the nudge goes out and the pill greys.
                        Box(Modifier.heightIn(min = 44.dp).pressable().clickable(enabled = !already) { sq.nudge(m) }, contentAlignment = Alignment.Center) {
                            Row(
                                Modifier.height(32.dp).background(if (already) p.card2 else p.btn, CircleShape).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(FistIcon, null, tint = if (already) p.muted else p.btnInk, modifier = Modifier.size(13.dp))
                                Text(if (already) " Nudged" else " Nudge", fontSize = 12.sp, fontWeight = FontWeight(700), color = if (already) p.muted else p.btnInk)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        days.forEach { d ->
                            val did = d in trainedDays
                            val future = d > today
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(15.dp)
                                        .then(if (d == today) Modifier.border(1.5.dp, p.ink, CircleShape).padding(2.5.dp) else Modifier)
                                        .then(if (did) Modifier.background(p.green, CircleShape) else Modifier.border(1.5.dp, if (future) p.track else p.hair, CircleShape)),
                                )
                                Text(Dates.parse(d).format(dayFmt), fontSize = 9.sp, fontWeight = FontWeight(600), color = p.muted)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (m.shareStats) "${(todayRow?.proteinG ?: 0.0).toInt()} g · ${String.format(Locale.US, "%,d", (todayRow?.calories ?: 0.0).toInt())} kcal" else "streaks only",
                        fontSize = 13.sp, fontWeight = FontWeight(600), color = if (m.shareStats) p.ink else p.muted,
                    )
                }
            }
        }
    }

    Rise(4 + members.size) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpenProfile).padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
            Text("You share ${if (shareStats) "streaks + protein & calories" else "streaks only"} · ", fontSize = 12.sp, color = p.muted)
            Text("change", fontSize = 12.sp, fontWeight = FontWeight(700), color = p.ink)
        }
    }

    Rise(5 + members.size) {
        if (confirmLeave) {
            Card(padding = 16.dp) {
                Text("Leave ${squad.name}?", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                Text(
                    when { sq.board.size <= 1 -> "You're the last one in, so the squad will be deleted."; isOwner -> "The longest-standing member becomes the owner."; else -> "You can rejoin any time with the code." },
                    fontSize = 12.sp, color = p.muted,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Stay", { confirmLeave = false }, Modifier.weight(1f), height = 44.dp, bg = p.card2, fg = p.ink)
                    PillButton("Leave", { confirmLeave = false; sq.leave(squad.id) }, Modifier.weight(1f), enabled = !sq.busy, height = 44.dp, bg = p.red, fg = androidx.compose.ui.graphics.Color.White)
                }
            }
        } else {
            Text("Leave squad", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.red, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { confirmLeave = true }.padding(vertical = 8.dp))
        }
    }
}
