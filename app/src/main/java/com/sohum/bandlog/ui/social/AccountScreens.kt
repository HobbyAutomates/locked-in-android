package com.sohum.bandlog.ui.social

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.NotYetAvailable
import com.sohum.bandlog.data.SocialApi
import com.sohum.bandlog.data.SocialStore
import com.sohum.bandlog.data.totalsFor
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.Card
import com.sohum.bandlog.ui.components.ErrorNote
import com.sohum.bandlog.ui.components.GroupLabel
import com.sohum.bandlog.ui.components.Hair
import com.sohum.bandlog.ui.components.LineIcons
import com.sohum.bandlog.ui.components.PillButton
import com.sohum.bandlog.ui.components.SubPage
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.ExportData
import com.sohum.bandlog.util.HealthExtras
import com.sohum.bandlog.util.I18n
import com.sohum.bandlog.util.Safety
import com.sohum.bandlog.util.WeightImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ------------------------------------------------------------------------------------------ E2

/** Preferences → Language: English / Hinglish / हिन्दी (the bottom bar, the sync chip, the v2.18 screens). */
@Composable
fun LanguageScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    SubPage(tr("lang.title"), onBack) {
        Card(padding = 0.dp) {
            I18n.LANGS.forEachIndexed { i, l ->
                if (i > 0) Hair()
                val sel = l.key == SocialStore.lang
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                        SocialStore.setLanguage(l.key)
                        // Mirrored to profiles.ui_lang (schema_v45); a missing column is fine.
                        scope.launch { SocialApi.patchProfile(org.json.JSONObject().put("ui_lang", l.key)) }
                    }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(l.native, fontSize = 16.sp, fontWeight = FontWeight(600), color = p.ink)
                        if (l.native != l.label) Text(l.label, fontSize = 12.sp, color = p.muted)
                    }
                    if (sel) Text("✓", fontSize = 18.sp, fontWeight = FontWeight(700), color = Ember)
                }
            }
        }
        Text(tr("lang.sub"), fontSize = 13.sp, color = p.muted, lineHeight = 18.sp)
    }
}

// ------------------------------------------------------------------------------------------ E5 export

/** Preferences → Account → Export: every log as CSV (same columns as the web), or a PDF summary. */
@Composable
fun ExportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    SubPage(tr("export.title"), onBack) {
        Hero(tr("export.title"), tr("export.sub"), "Meals with every item, workouts, activities, weights and water. Nothing leaves the phone until you share it.")
        ErrorNote(err)
        PillButton(if (busy == "csv") "Preparing…" else "Export CSV", {
            if (busy != null) return@PillButton
            scope.launch {
                busy = "csv"; err = null
                runCatching { shareFile(ctx, buildCsvFile(ctx), "text/csv") }.onFailure { err = it.message ?: "Couldn't export" }
                busy = null
            }
        }, bg = Ember, fg = BoneBrand)
        PillButton(if (busy == "pdf") "Preparing…" else "Export PDF summary", {
            if (busy != null) return@PillButton
            scope.launch {
                busy = "pdf"; err = null
                runCatching { shareFile(ctx, buildPdf(ctx, vm), "application/pdf") }.onFailure { err = it.message ?: "Couldn't export" }
                busy = null
            }
        }, height = 46.dp)
        Text("The CSV opens in Excel or Google Sheets. The PDF is a printable summary of the last 30 days.", fontSize = 12.sp, color = p.muted, lineHeight = 17.sp)
    }
}

private fun exportDir(ctx: Context) = File(ctx.cacheDir, "share").apply { mkdirs() }

private suspend fun buildCsvFile(ctx: Context): File {
    val today = Dates.today()
    val from = "2000-01-01"
    val meals = Api.meals(from, today)
    val workouts = Api.workouts(from, today)
    val acts = Api.exercises(from, today)
    val weights = runCatching { Api.weights() }.getOrDefault(emptyList())
    val water = runCatching { Api.water(from, today) }.getOrDefault(emptyList())
    val parts = listOf(
        ExportData.Csv("meals", ExportData.HEADERS.getValue("meals"), ExportData.mealRows(meals.sortedBy { it.date }.map { m ->
            ExportData.MealIn(m.date, m.rawText, m.mealType, m.items.map { ExportData.Item(it.name, it.grams, it.calories, it.proteinG, it.carbsG, it.fatG) })
        })),
        ExportData.Csv("workouts", ExportData.HEADERS.getValue("workouts"), workouts.sortedBy { it.date }.map { w ->
            listOf(w.date, w.kind, w.minutes, w.muscles.joinToString(" "), if (w.lifts.isNotEmpty()) com.sohum.bandlog.data.Lift.summaryText(w.lifts) else w.exercises, w.notes)
        }),
        ExportData.Csv("activities", ExportData.HEADERS.getValue("activities"), acts.sortedBy { it.date }.map { a ->
            listOf(a.date, a.name, a.minutes, a.intensity, a.kcal, a.steps, a.distanceKm, a.source)
        }),
        ExportData.Csv("weights", ExportData.HEADERS.getValue("weights"), weights.sortedBy { it.date }.map { listOf(it.date, it.weightKg, it.note) }),
        ExportData.Csv("water", ExportData.HEADERS.getValue("water"), water.sortedBy { it.date }.map { listOf(it.date, it.ml, it.vessel) }),
    )
    val f = File(exportDir(ctx), ExportData.filename("all", today))
    withContext(Dispatchers.IO) { f.writeText(ExportData.combinedCsv(parts), Charsets.UTF_8) }
    return f
}

/** A printable A4 summary: the header, the totals, then the last 30 days and the weights. */
private suspend fun buildPdf(ctx: Context, vm: AppViewModel): File = withContext(Dispatchers.IO) {
    val today = Dates.today()
    val doc = PdfDocument()
    val w = 595; val h = 842
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; isFakeBoldText = true; color = 0xFF0B0B0C.toInt() }
    val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; isFakeBoldText = true; color = 0xFF0B0B0C.toInt() }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF333333.toInt() }
    val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFFC2410C.toInt() }
    var pageNo = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
    var c = page.canvas
    var y = 56f
    fun line(text: String, p: Paint, gap: Float = 15f) {
        if (y > h - 48f) { doc.finishPage(page); pageNo++; page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create()); c = page.canvas; y = 56f }
        c.drawText(text, 40f, y, p); y += gap
    }
    line("Locked In · my summary", title, 26f)
    line("${vm.profile.name.ifBlank { "Me" }} · exported ${Dates.long(today)}", accent, 22f)
    line("Totals (last 120 days)", head, 18f)
    line("${vm.meals.size} meals · ${vm.workouts.size} workouts · ${vm.exercises.count { it.source != "workout" }} activities · ${vm.weights.size} weigh-ins", body, 14f)
    line("Day streak: ${vm.dayStreak} · targets ${vm.profile.calorieTarget} kcal, ${vm.profile.proteinTargetG} g protein", body, 24f)
    line("Last 30 days", head, 18f)
    line("Date                     kcal     protein    meals   trained", body, 14f)
    (0 until 30).map { Dates.addDays(today, -it.toLong()) }.forEach { d ->
        val t = totalsFor(vm.meals, d)
        val meals = vm.meals.count { it.date == d }
        val trained = vm.workouts.any { it.date == d }
        if (meals == 0 && !trained) return@forEach
        line(String.format(java.util.Locale.US, "%-22s %6d %8d g %7d   %s", Dates.short(d), t.calories.toInt(), t.protein.toInt(), meals, if (trained) "yes" else ""), body, 13f)
    }
    y += 10f
    if (vm.weights.isNotEmpty()) {
        line("Weights", head, 18f)
        vm.weights.sortedByDescending { it.date }.take(30).forEach { line("${Dates.short(it.date)}   ${com.sohum.bandlog.util.Wrapped.num(it.weightKg)} kg${if (it.note.isNotBlank()) "   ${it.note.take(40)}" else ""}", body, 13f) }
    }
    doc.finishPage(page)
    val f = File(exportDir(ctx), ExportData.filename("summary", today, "pdf"))
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    f
}

private fun shareFile(ctx: Context, f: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, f.name)
        clipData = ClipData.newRawUri(f.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ------------------------------------------------------------------------------------------ E5 delete

/** Preferences → Account → Delete account: type DELETE, the web route wipes everything, then sign out. */
@Composable
fun DeleteAccountScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var soon by remember { mutableStateOf(false) }
    SubPage(tr("delete.title"), onBack) {
        Card {
            Text(tr("delete.sub"), fontSize = 17.sp, fontWeight = FontWeight(700), color = p.red)
            Spacer(Modifier.height(8.dp))
            Text("Your meals, workouts, weights, photos, squads' posts and coach history are deleted, then the account itself. Squads you own pass to another member. This can't be undone.", fontSize = 14.sp, color = p.ink, lineHeight = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text("Want a copy first? Export my data.", fontSize = 13.sp, fontWeight = FontWeight(600), color = Ember, modifier = Modifier.clickable { SocialNav.open(SocialPage.EXPORT) }.padding(vertical = 6.dp))
        }
        Text("Type ${Safety.DELETE_WORD} to confirm", fontSize = 13.sp, color = p.muted)
        SocialField(typed, { typed = it.take(12) }, Safety.DELETE_WORD)
        ErrorNote(err)
        if (soon) Text("In-app deletion is ${tr("common.soon").lowercase()}. Until then, email us and we'll erase it for you.", fontSize = 13.sp, color = p.muted)
        PillButton(if (busy) "Deleting…" else tr("delete.title"), {
            if (busy) return@PillButton
            scope.launch {
                busy = true; err = null
                try {
                    SocialApi.deleteAccount()
                    SocialNav.closeAll()
                    vm.signOut()
                } catch (e: NotYetAvailable) { soon = true } catch (e: Exception) { err = e.message }
                busy = false
            }
        }, enabled = Safety.deleteConfirmed(typed) && !busy, bg = p.red, fg = androidx.compose.ui.graphics.Color.White)
        if (soon) PillButton("Email us instead", {
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:sohumai.team@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, "Locked In — delete my account")
                    putExtra(Intent.EXTRA_TEXT, "Please delete my Locked In account and all my logs.\n\nAccount: ${com.sohum.bandlog.data.Session.email.orEmpty()}")
                })
            }
        }, height = 46.dp)
    }
}

// ------------------------------------------------------------------------------------------ E5 blocks

@Composable
fun BlockedScreen(onBack: () -> Unit) {
    val p = palette
    val scope = rememberCoroutineScope()
    SubPage("Blocked people", onBack) {
        Card(padding = 0.dp) {
            val list = SocialStore.blocked.toList()
            if (list.isEmpty()) Text("Nobody. Block someone from a squad post's menu (long-press).", fontSize = 14.sp, color = p.muted, modifier = Modifier.padding(16.dp))
            list.forEachIndexed { i, id ->
                if (i > 0) Hair()
                Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(SocialStore.string("blocked-name-$id") ?: "Squadmate", fontSize = 15.sp, color = p.ink, modifier = Modifier.weight(1f))
                    ChoicePill(tr("squad.unblock"), false, {
                        SocialStore.updateBlocked(SocialStore.blocked - id)
                        scope.launch { runCatching { SocialApi.unblock(id) } }
                    })
                }
            }
        }
        Text("Blocked people's posts and messages are hidden from you in every squad.", fontSize = 12.sp, color = p.muted)
    }
}

// ------------------------------------------------------------------------------------------ E4

/** Scales and watches (Health Connect): weight, steps and resting heart rate; weight import is opt-in. */
@Composable
fun HealthSyncScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p = palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val available = remember { com.sohum.bandlog.util.Health.available(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf<HealthExtras.Reading?>(null) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importOn by remember { mutableStateOf(SocialStore.flag(HC_WEIGHT)) }
    var note by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { tick++ }
    LaunchedEffect(tick) {
        if (!available) return@LaunchedEffect
        granted = HealthExtras.granted(ctx)
        if (granted.isNotEmpty()) reading = runCatching { HealthExtras.read(ctx) }.getOrNull()
    }
    SubPage("Scales and watches", onBack) {
        Hero("Health Connect", "Weight, steps, heart rate", "Mi Fit, Zepp (Amazfit), Samsung Health and Google Fit all write to Health Connect. We only read.")
        if (!available) { Card { Text("Health Connect isn't available on this phone. Install it from the Play Store to sync a scale or watch.", fontSize = 14.sp, color = p.muted, lineHeight = 19.sp) }; return@SubPage }
        val missing = HealthExtras.PERMISSIONS - granted
        if (missing.isNotEmpty()) PillButton("Connect Health Connect", { runCatching { launcher.launch(HealthExtras.PERMISSIONS) } }, bg = Ember, fg = BoneBrand)
        reading?.let { r ->
            Card {
                LineRow("Latest weight", r.weights.lastOrNull()?.let { "${com.sohum.bandlog.util.Wrapped.num(Math.round(it.second * 10) / 10.0)} kg · ${Dates.relative(it.first)}" } ?: "—")
                Hair()
                val today = Dates.today()
                LineRow("Steps today", r.steps[today]?.let { com.sohum.bandlog.util.Money.group(it) } ?: "—")
                Hair()
                val week = (0 until 7).mapNotNull { r.steps[Dates.addDays(today, -it.toLong())] }
                LineRow("Steps, 7-day average", if (week.isEmpty()) "—" else com.sohum.bandlog.util.Money.group(week.sum() / 7))
                Hair()
                LineRow("Resting heart rate", r.restingHr?.let { "${it.second} bpm · ${Dates.relative(it.first)}" } ?: "—")
            }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Copy new weigh-ins to my weight log", fontSize = 15.sp, fontWeight = FontWeight(600), color = p.ink)
                    Text("Only days you haven't logged, newer than your last entry.", fontSize = 12.sp, color = p.muted)
                }
                Switch(importOn, { importOn = it; SocialStore.setFlag(HC_WEIGHT, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Ember))
            }
            if (importOn) {
                Spacer(Modifier.height(10.dp))
                PillButton("Sync now", {
                    scope.launch { note = importWeights(ctx, vm)?.let { n -> if (n == 0) "Nothing new to copy." else "Copied $n ${if (n == 1) "weigh-in" else "weigh-ins"}." } ?: "Connect Health Connect first." }
                }, height = 44.dp)
            }
            note?.let { Text(it, fontSize = 13.sp, color = p.green, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

internal const val HC_WEIGHT = "hc-weight"

/** Copies new Health Connect weigh-ins into weight_log (opt-in). Null when not connected. */
internal suspend fun importWeights(ctx: Context, vm: AppViewModel): Int? {
    if (!SocialStore.flag(HC_WEIGHT) || !com.sohum.bandlog.util.Health.available(ctx)) return null
    val r = runCatching { HealthExtras.read(ctx, 14) }.getOrNull() ?: return null
    if (r.weights.isEmpty() && HealthExtras.granted(ctx).isEmpty()) return null
    val todo = WeightImport.weightsToImport(r.weights, vm.weights.map { it.date })
    var n = 0
    todo.forEach { (d, kg) -> if (runCatching { Api.logWeight(d, kg, "Health Connect") }.isSuccess) n++ }
    if (n > 0) vm.refresh()
    return n
}
