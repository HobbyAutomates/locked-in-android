package com.sohum.bandlog.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sohum.bandlog.data.SupabaseAuth
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.Ring
import com.sohum.bandlog.ui.components.lucide
import com.sohum.bandlog.ui.theme.DarkPalette
import com.sohum.bandlog.ui.theme.Palette
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Names
import kotlinx.coroutines.launch

// v2.12 login: "Login B" welcome (floating preview cards + bottom sheet), an email sign-in form, and
// "Login C" create-account (one question per screen). The auth calls and their messages are the
// same as v2.11 — SupabaseAuth.signIn / signUp — only the screens around them changed.

private const val WELCOME = 0
private const val SIGN_IN = 1
private const val CREATE = 2
private const val INVITE = 3

private const val CONFIRM_EMAIL = "Account created. Confirm the email we sent, then sign in."

private val MailIcon: ImageVector by lazy { lucide("Mail", listOf("M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M22 6l-10 7L2 6")) }
private val TicketIcon: ImageVector by lazy { lucide("Ticket", listOf("M3 7a2 2 0 0 0 2-2h14a2 2 0 0 0 2 2v3a2 2 0 0 0 0 4v3a2 2 0 0 0-2 2H5a2 2 0 0 0-2-2v-3a2 2 0 0 0 0-4z", "M13 5v2", "M13 17v2", "M13 11v2")) }
private val BackIcon: ImageVector by lazy { lucide("Back", listOf("M15 18l-6-6 6-6")) }
private val EyeIcon: ImageVector by lazy { lucide("Eye", listOf("M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z")) }
private val EyeOffIcon: ImageVector by lazy {
    lucide("EyeOff", listOf("M9.88 9.88a3 3 0 1 0 4.24 4.24", "M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68", "M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61", "M2 2l20 20"))
}
private val FlameLine: ImageVector by lazy {
    lucide("FlameLine", listOf("M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"))
}

/** The code inside an invite link (…/join/<code>) or a typed code — same rules as MainActivity's deep link. */
internal fun parseInviteCode(raw: String): String? {
    val t = raw.trim()
    val fromLink = Regex("""join/([A-Za-z0-9]+)""").find(t)?.groupValues?.get(1)
    return (fromLink ?: t).filter { it.isLetterOrDigit() }.uppercase().takeIf { it.length in 4..12 }
}

@Composable
private fun isDark(p: Palette) = p == DarkPalette

/** Ink on the solid green (white in light mode, black in dark, like the mockups). */
@Composable
private fun onGreen(p: Palette) = if (isDark(p)) Color.Black else Color.White

/**
 * [onInviteCode] hands a squad invite typed before sign-in to the shell, which joins it once the
 * account is ready (the same pending-code path a tapped invite link uses).
 */
@Composable
fun LoginScreen(reason: String? = null, onInviteCode: (String) -> Unit = {}, onSignedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var stage by rememberSaveable { mutableIntStateOf(if (reason != null) SIGN_IN else WELCOME) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var inviteText by rememberSaveable { mutableStateOf("") }
    var pendingInvite by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Why we're here (e.g. the session expired mid-save) until the user acts.
    var error by remember { mutableStateOf(reason) }
    var info by remember { mutableStateOf<String?>(null) }

    fun go(to: Int, toStep: Int = 0) { stage = to; step = toStep; error = null; if (to != CREATE) info = null }

    fun signIn() {
        if (busy) return
        scope.launch {
            busy = true; error = null; info = null
            try { SupabaseAuth.signIn(email, password); onSignedIn() } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    fun signUp() {
        if (busy) return
        scope.launch {
            busy = true; error = null; info = null
            try {
                val active = SupabaseAuth.signUp(email, password, name)
                if (active) onSignedIn() else info = CONFIRM_EMAIL
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    BackHandler(enabled = stage != WELCOME) {
        if (stage == CREATE && step > 0 && info == null) { step--; error = null } else go(WELCOME)
    }

    key(stage) {
        when (stage) {
            WELCOME -> Welcome(error, onEmail = { go(SIGN_IN) }, onInvite = { go(INVITE) })
            SIGN_IN -> SignInForm(
                email, { email = it }, password, { password = it }, busy, error, info,
                onBack = { go(WELCOME) }, onSubmit = { signIn() }, onCreate = { go(CREATE) },
            )
            INVITE -> InviteForm(
                inviteText, { inviteText = it; error = null }, error,
                onBack = { go(WELCOME) },
                onContinue = {
                    val code = parseInviteCode(inviteText)
                    if (code == null) error = "That doesn't look like an invite. Paste the whole link, or type the code."
                    else { pendingInvite = code; onInviteCode(code); go(CREATE) }
                },
                onSignIn = { parseInviteCode(inviteText)?.let { pendingInvite = it; onInviteCode(it) }; go(SIGN_IN) },
            )
            else -> CreateFlow(
                step = step, email = email, onEmail = { email = it }, password = password, onPassword = { password = it },
                name = name, onName = { name = it }, pendingInvite = pendingInvite, busy = busy, error = error, info = info,
                onBack = { if (step > 0 && info == null) { step--; error = null } else go(WELCOME) },
                onNext = { if (step < 3) { step++; error = null } else signUp() },
                onSignIn = { go(SIGN_IN) },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Welcome ("Login B")
// ---------------------------------------------------------------------------------------------

@Composable
private fun Welcome(reason: String?, onEmail: () -> Unit, onInvite: () -> Unit) {
    val p = palette
    val dark = isDark(p)
    val glowA = if (dark) p.green.copy(alpha = 0.16f) else p.greenBg
    val glowB = if (dark) p.blue.copy(alpha = 0.16f) else p.blueBg
    Column(Modifier.fillMaxSize().background(p.bg)) {
        // Soft gradient with the floating preview cards. Decorative sample content: hidden from TalkBack.
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).clipToBounds()
                .drawBehind {
                    drawRect(Brush.radialGradient(listOf(glowA, Color.Transparent), center = Offset(size.width * 0.2f, 0f), radius = size.width * 1.2f))
                    drawRect(Brush.radialGradient(listOf(glowB, Color.Transparent), center = Offset(size.width, size.height * 0.3f), radius = size.width * 0.9f))
                }
                .clearAndSetSemantics { },
        ) {
            val sx = maxWidth / 390f
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                FloatingCard(sx * 24f, 40.dp, -4f, 1) { StreakCard(p) }
                FloatingCard(sx * 196f, 26.dp, 5f, 0) { RingsCard(p) }
                FloatingCard(sx * 36f, 166.dp, 3f, 3) { SquadCard(p) }
                FloatingCard(sx * 236f, 184.dp, -6f, 2) { KcalCard(p) }
            }
        }
        // Bottom sheet.
        Column(
            Modifier.fillMaxWidth().loginEnter(2, rise = 36f)
                .shadow(24.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), ambientColor = p.shadow, spotColor = p.shadow)
                .background(p.card, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.loginEnter(3), verticalAlignment = Alignment.CenterVertically) {
                LockedInLogo(44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Locked In", fontSize = 24.sp, fontWeight = FontWeight(800), letterSpacing = (-0.6).sp, color = p.ink, modifier = Modifier.semantics { heading() })
                    Text("Your food, training and squad in one place", fontSize = 14.sp, color = p.muted, lineHeight = 19.sp)
                }
            }
            if (reason != null) ErrorNote(reason)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.loginEnter(4)) { SolidPill("Continue with email", onEmail, icon = MailIcon) }
            Box(Modifier.loginEnter(5)) { OutlinePill("I have a squad invite", onInvite, icon = TicketIcon) }
        }
    }
}

@Composable
private fun FloatingCard(x: Dp, y: Dp, rotation: Float, order: Int, content: @Composable () -> Unit) {
    Box(
        Modifier.offset(x, y).loginDrift(order).rotate(rotation)
            .loginEnter(order, rise = 44f, baseDelayMs = 120),
    ) { content() }
}

@Composable
private fun PreviewCard(bg: Color, content: @Composable ColumnScope.() -> Unit) {
    val p = palette
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.shadow(18.dp, shape, ambientColor = p.shadow, spotColor = p.shadow).background(bg, shape).padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
private fun StreakCard(p: Palette) = PreviewCard(p.card) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(FlameLine, null, tint = p.orange, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(6.dp))
        Text("19", fontSize = 34.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = p.ink, lineHeight = 36.sp)
        Spacer(Modifier.width(6.dp))
        Text("day\nstreak", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, lineHeight = 15.sp)
    }
}

@Composable
private fun RingsCard(p: Palette) = PreviewCard(p.card) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Ring(0.80f, p.blue, 46.dp, 6.dp)
        Ring(0.95f, p.orange, 46.dp, 6.dp)
        Ring(1f, p.purple, 46.dp, 6.dp)
    }
    Text("Protein · Carbs · Fat", fontSize = 12.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun SquadCard(p: Palette) = PreviewCard(p.card) {
    val onColor = onGreen(p)
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        listOf("A" to p.blue, "R" to p.orange, "K" to p.purple, "S" to p.green).forEach { (l, c) ->
            Box(
                Modifier.size(34.dp).background(p.card, CircleShape).padding(2.dp).background(c, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(l, fontSize = 13.sp, fontWeight = FontWeight(800), color = onColor) }
        }
    }
    Text("Ayaan logged lunch", fontSize = 14.sp, fontWeight = FontWeight(700), color = p.ink, modifier = Modifier.padding(top = 8.dp))
    Text("+32 g protein · 2 min ago", fontSize = 12.sp, color = p.muted)
}

@Composable
private fun KcalCard(p: Palette) = PreviewCard(p.green) {
    val c = onGreen(p)
    Text("Today", fontSize = 12.sp, fontWeight = FontWeight(600), color = c.copy(alpha = 0.8f))
    Text("1,892", fontSize = 30.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, color = c, lineHeight = 32.sp)
    Text("of 2,200 kcal", fontSize = 12.sp, fontWeight = FontWeight(600), color = c.copy(alpha = 0.8f))
}

/** The app mark: green tile, lock, and a flame cut into the lock body. */
@Composable
private fun LockedInLogo(size: Dp) {
    val p = palette
    val fg = onGreen(p)
    val shackle = remember { PathParser().parsePathString("M19 26v-5a9 9 0 0 1 18 0v5").toPath() }
    val flame = remember { PathParser().parsePathString("M28 29c3 3 4 5 4 7a4 4 0 0 1-8 0c0-1.5.7-2.5 1.5-3.2.2 1.2 1 1.9 1.7 1.9-.7-2-.2-4 .8-5.7z").toPath() }
    Canvas(Modifier.size(size).semantics { contentDescription = "Locked In logo" }) {
        val k = this.size.width / 56f
        scale(k, k, pivot = Offset.Zero) {
            drawRoundRect(p.green, Offset.Zero, Size(56f, 56f), CornerRadius(16f, 16f))
            drawPath(shackle, fg, style = Stroke(4f, cap = StrokeCap.Round))
            drawRoundRect(fg, Offset(14f, 25f), Size(28f, 20f), CornerRadius(6f, 6f))
            drawPath(flame, p.green)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Sign in
// ---------------------------------------------------------------------------------------------

@Composable
private fun SignInForm(
    email: String, onEmail: (String) -> Unit, password: String, onPassword: (String) -> Unit,
    busy: Boolean, error: String?, info: String?,
    onBack: () -> Unit, onSubmit: () -> Unit, onCreate: () -> Unit,
) {
    val p = palette
    val focus = LocalFocusManager.current
    var show by rememberSaveable { mutableStateOf(false) }
    val canSubmit = !busy && email.isNotBlank() && password.length >= 6
    FormScaffold(
        top = { BackButton(onBack) },
        bottom = {
            SolidPill("Sign in", { focus.clearFocus(); onSubmit() }, enabled = canSubmit, busy = busy)
            TextLink("New here? ", "Create account", onCreate)
        },
    ) {
        Eyebrow("Welcome back", Modifier.loginEnter(0))
        Headline("Sign in", Modifier.loginEnter(0))
        Text("One sign-in on this phone. You'll stay logged in.", fontSize = 16.sp, color = p.muted, lineHeight = 22.sp, modifier = Modifier.loginEnter(1))
        Spacer(Modifier.height(18.dp))
        Column(Modifier.loginEnter(2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LoginField(
                email, onEmail, "Email", "you@example.com",
                KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
            )
            LoginField(
                password, onPassword, "Password", "At least 6 characters",
                KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                KeyboardActions(onDone = { focus.clearFocus(); if (canSubmit) onSubmit() }),
                password = true, shown = show, onToggleShown = { show = !show },
            )
        }
        Spacer(Modifier.height(12.dp))
        ErrorNote(error)
        if (info != null) Text(info, color = p.green, fontSize = 13.sp, fontWeight = FontWeight(600))
    }
}

// ---------------------------------------------------------------------------------------------
// Squad invite (before sign-in)
// ---------------------------------------------------------------------------------------------

@Composable
private fun InviteForm(text: String, onText: (String) -> Unit, error: String?, onBack: () -> Unit, onContinue: () -> Unit, onSignIn: () -> Unit) {
    val p = palette
    val focus = LocalFocusManager.current
    FormScaffold(
        top = { BackButton(onBack) },
        bottom = {
            SolidPill("Continue", { focus.clearFocus(); onContinue() }, enabled = text.isNotBlank(), bg = p.green, fg = onGreen(p))
            TextLink("Have an account? ", "Sign in", onSignIn)
        },
    ) {
        Eyebrow("Squad invite", Modifier.loginEnter(0))
        Headline("Got an invite?", Modifier.loginEnter(0))
        Text("Paste the link or type the code. You'll join the squad as soon as you're signed in.", fontSize = 16.sp, color = p.muted, lineHeight = 22.sp, modifier = Modifier.loginEnter(1))
        Spacer(Modifier.height(18.dp))
        Box(Modifier.loginEnter(2)) {
            LoginField(
                text, onText, "Invite link or code", "e.g. K7Q2XD",
                KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done, keyboardType = KeyboardType.Uri),
                KeyboardActions(onDone = { focus.clearFocus(); onContinue() }),
            )
        }
        Spacer(Modifier.height(12.dp))
        ErrorNote(error)
    }
}

// ---------------------------------------------------------------------------------------------
// Create account ("Login C"): one question per screen
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateFlow(
    step: Int, email: String, onEmail: (String) -> Unit, password: String, onPassword: (String) -> Unit,
    name: String, onName: (String) -> Unit, pendingInvite: String?, busy: Boolean, error: String?, info: String?,
    onBack: () -> Unit, onNext: () -> Unit, onSignIn: () -> Unit,
) {
    val p = palette
    val focus = LocalFocusManager.current
    var show by rememberSaveable { mutableStateOf(false) }
    val done = info != null
    val canNext = !busy && when (step) {
        0 -> email.isNotBlank() && '@' in email
        1 -> password.length >= 6
        2 -> true
        else -> email.isNotBlank() && password.length >= 6
    }
    val next = { focus.clearFocus(); if (canNext) onNext() }
    FormScaffold(
        top = { StepHeader(step, onBack) },
        bottom = {
            if (done) SolidPill("Go to sign in", onSignIn)
            else SolidPill(if (step == 3) "Create account" else "Continue", { next() }, enabled = canNext, busy = busy, bg = p.green, fg = onGreen(p))
            if (!done) TextLink("Have an account? ", "Sign in", onSignIn)
        },
    ) {
        key(step) {
            Eyebrow("Create account", Modifier.loginEnter(0, step))
            when (step) {
                0 -> {
                    Headline("What's your\nemail?", Modifier.loginEnter(0, step))
                    Text("You'll sign in with it on this phone.", fontSize = 16.sp, color = p.muted, modifier = Modifier.loginEnter(1, step))
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.loginEnter(2, step)) {
                        LoginField(
                            email, onEmail, "Email", "you@example.com",
                            KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            KeyboardActions(onNext = { next() }), big = true,
                        )
                    }
                    if (pendingInvite != null) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.loginEnter(3, step).fillMaxWidth().background(p.greenBg, RoundedCornerShape(14.dp)).padding(12.dp, 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(TicketIcon, null, tint = p.green, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Invite $pendingInvite saved. You'll join the squad after you sign up.", fontSize = 13.sp, color = p.ink, lineHeight = 18.sp)
                        }
                    }
                }
                1 -> {
                    Headline("Pick a\npassword", Modifier.loginEnter(0, step))
                    Text("At least 6 characters.", fontSize = 16.sp, color = p.muted, modifier = Modifier.loginEnter(1, step))
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.loginEnter(2, step)) {
                        LoginField(
                            password, onPassword, "Password", "Password",
                            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            KeyboardActions(onNext = { next() }), big = true,
                            password = true, shown = show, onToggleShown = { show = !show },
                        )
                    }
                }
                2 -> {
                    val fallback = Names.nameFromEmail(email)
                    val shown = name.trim().ifBlank { fallback.ifBlank { "You" } }
                    Headline("What does your\nsquad call you?", Modifier.loginEnter(0, step))
                    Text("This shows on the feed, chats and challenges.", fontSize = 16.sp, color = p.muted, lineHeight = 22.sp, modifier = Modifier.loginEnter(1, step))
                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.loginEnter(2, step)) {
                        LoginField(
                            name, onName, "Your name (optional)", fallback.ifBlank { "e.g. Ayaan" },
                            KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            KeyboardActions(onNext = { next() }), big = true,
                        )
                        val chips = listOf(fallback, fallback.take(3)).filter { it.length >= 2 }.distinct()
                        if (chips.isNotEmpty()) FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chips.forEach { c ->
                                val sel = name.trim() == c
                                Box(
                                    Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable { onName(c) }.semantics { role = Role.Button },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        c, fontSize = 14.sp, fontWeight = FontWeight(700), color = if (sel) onGreen(p) else p.ink,
                                        modifier = Modifier.background(if (sel) p.green else p.card2, CircleShape).padding(horizontal = 14.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    // Live preview of a feed post.
                    Row(
                        Modifier.loginEnter(3, step).fillMaxWidth().background(p.card, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(42.dp).background(p.orange, CircleShape), contentAlignment = Alignment.Center) {
                            Text(shown.take(1).uppercase(), fontWeight = FontWeight(800), color = onGreen(p), fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                buildAnnotatedString {
                                    append(shown)
                                    withStyle(SpanStyle(fontWeight = FontWeight(500), color = p.muted)) { append(" logged breakfast") }
                                },
                                fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1,
                            )
                            Text("How your posts will look", fontSize = 13.sp, color = p.muted)
                        }
                        Icon(FlameLine, null, tint = p.orange, modifier = Modifier.size(20.dp))
                    }
                }
                else -> {
                    Headline(if (done) "Check your\ninbox" else "Ready to\nlock in?", Modifier.loginEnter(0, step))
                    Text(
                        if (done) info!! else "One tap and your account is made.",
                        fontSize = 16.sp, color = if (done) p.green else p.muted, lineHeight = 22.sp,
                        fontWeight = if (done) FontWeight(600) else FontWeight(400), modifier = Modifier.loginEnter(1, step),
                    )
                    Spacer(Modifier.height(24.dp))
                    Column(
                        Modifier.loginEnter(2, step).fillMaxWidth().background(p.card, RoundedCornerShape(18.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SummaryRow("Email", email.trim())
                        SummaryRow("Name", name.trim().ifBlank { Names.nameFromEmail(email).ifBlank { "—" } })
                        if (pendingInvite != null) SummaryRow("Squad invite", pendingInvite)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ErrorNote(error)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val p = palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = p.muted, modifier = Modifier.width(96.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight(700), color = p.ink, maxLines = 1)
    }
}

/** Back + the 4-segment progress bar + "n of 4". */
@Composable
private fun StepHeader(step: Int, onBack: () -> Unit) {
    val p = palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackButton(onBack)
        Spacer(Modifier.width(14.dp))
        Row(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "Step ${step + 1} of 4" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i ->
                val c by animateColorAsState(if (i <= step) p.green else p.track, tween(600, easing = LoginEase), label = "seg$i")
                Box(Modifier.weight(1f).height(5.dp).background(c, RoundedCornerShape(3.dp)))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text("${step + 1} of 4", fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.clearAndSetSemantics { })
    }
}

// ---------------------------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------------------------

/** Header row, a scrolling body, and actions pinned to the bottom (they ride above the keyboard). */
@Composable
private fun FormScaffold(top: @Composable () -> Unit, bottom: @Composable ColumnScope.() -> Unit, body: @Composable ColumnScope.() -> Unit) {
    val p = palette
    Column(Modifier.fillMaxSize().background(p.bg).safeDrawingPadding().imePadding()) {
        Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp).loginEnter(0, rise = 12f)) { top() }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = body,
        )
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 8.dp).loginEnter(3),
            verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally, content = bottom,
        )
    }
}

@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = 14.sp, fontWeight = FontWeight(700), color = palette.green, modifier = modifier)

@Composable
private fun Headline(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = 34.sp, fontWeight = FontWeight(800), letterSpacing = (-1).sp, lineHeight = 38.sp, color = palette.ink, modifier = modifier.semantics { heading() })

@Composable
private fun BackButton(onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = "Back"; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).background(p.card2, CircleShape), contentAlignment = Alignment.Center) {
            Icon(BackIcon, null, tint = p.ink, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TextLink(lead: String, action: String, onClick: () -> Unit) {
    val p = palette
    Box(
        Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .semantics { role = Role.Button }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            buildAnnotatedString {
                append(lead)
                withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight(700))) { append(action) }
            },
            fontSize = 14.sp, color = p.muted, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SolidPill(text: String, onClick: () -> Unit, enabled: Boolean = true, busy: Boolean = false, icon: ImageVector? = null, bg: Color? = null, fg: Color? = null) {
    val p = palette
    val b = bg ?: p.btn
    val f = fg ?: p.btnInk
    Row(
        Modifier.fillMaxWidth().height(54.dp).alpha(if (enabled || busy) 1f else 0.45f).clip(CircleShape).background(b)
            .clickable(enabled = enabled && !busy, onClick = onClick).semantics { role = Role.Button },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = f)
        else {
            if (icon != null) { Icon(icon, null, tint = f, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, color = f, fontSize = 16.sp, fontWeight = FontWeight(700))
        }
    }
}

@Composable
private fun OutlinePill(text: String, onClick: () -> Unit, icon: ImageVector? = null) {
    val p = palette
    val line = if (isDark(p)) Color(0xFF2A2A2D) else Color(0xFFD6D6DB)
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(CircleShape).border(1.5.dp, line, CircleShape)
            .clickable(onClick = onClick).semantics { role = Role.Button },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = p.ink, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight(700))
    }
}

/**
 * Soft filled field with a green focus ring. [big] is the one-question-per-screen size. Passwords
 * get a show/hide eye (a 48 dp target).
 */
@Composable
private fun LoginField(
    value: String, onChange: (String) -> Unit, label: String, placeholder: String,
    keyboardOptions: KeyboardOptions, keyboardActions: KeyboardActions,
    big: Boolean = false, password: Boolean = false, shown: Boolean = false, onToggleShown: () -> Unit = {},
) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val ring by animateColorAsState(if (focused) p.green else Color.Transparent, tween(300), label = "ring")
    val shape = RoundedCornerShape(18.dp)
    Column {
        if (!big) Text(label, fontSize = 13.sp, fontWeight = FontWeight(600), color = p.muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().height(if (big) 64.dp else 56.dp).background(p.card2, shape).border(2.dp, ring, shape).padding(start = 18.dp, end = if (password) 4.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = TextStyle(fontSize = if (big) 22.sp else 16.sp, fontWeight = FontWeight(if (big) 700 else 500), color = p.ink)
            BasicTextField(
                value, onChange,
                Modifier.weight(1f).semantics { contentDescription = label },
                singleLine = true, textStyle = style, cursorBrush = SolidColor(p.green),
                keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
                visualTransformation = if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None,
                interactionSource = source,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(placeholder, style = style.copy(color = p.muted.copy(alpha = 0.7f), fontWeight = FontWeight(500)), maxLines = 1)
                        inner()
                    }
                },
            )
            if (password) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onToggleShown)
                        .semantics { contentDescription = if (shown) "Hide password" else "Show password"; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (shown) EyeOffIcon else EyeIcon, null, tint = p.muted, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}
