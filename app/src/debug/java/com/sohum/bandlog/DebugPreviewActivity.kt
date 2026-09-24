package com.sohum.bandlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sohum.bandlog.data.FoodPreset
import com.sohum.bandlog.data.Lift
import com.sohum.bandlog.data.LiftSet
import com.sohum.bandlog.data.Serving
import com.sohum.bandlog.data.Session
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.ui.AppViewModel
import com.sohum.bandlog.ui.components.QuantitySheet
import com.sohum.bandlog.ui.log.LogScreen
import com.sohum.bandlog.ui.theme.BandLogTheme
import com.sohum.bandlog.ui.theme.palette
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.QuantityFood

/**
 * Debug builds only: renders the v2.5 screens without a session so their layout can be checked
 * (fontScale 1.3, 360 dp) on an emulator.
 *   adb shell am start -n com.sohum.bandlog/.DebugPreviewActivity --es screen meal|workout|roti|whey|loose
 *   v2.6: water | squadcreate | squadicon | squadpolicy | username | userphoto | squadboard | squadinfo
 * Presets are a local copy of data/presets.json (roti left at the old "2 roti" default on purpose).
 */
class DebugPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "meal"
        setContent {
            BandLogTheme(dark = isSystemInDarkTheme()) {
                val vm: AppViewModel = viewModel()
                var seeded by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    vm.debugSeed(PRESETS, seedWorkouts())
                    val now = java.time.OffsetDateTime.now()
                    vm.debugSeedWater(
                        com.sohum.bandlog.data.Profile(name = "Sohum Chhabria", waterGoalMl = 2500, waterGlassMl = 250, usernameSupported = true, weightKg = 70.0, heightCm = 175.0, dob = "2008-05-01"),
                        listOf(500, 250, 500, 250, 250).mapIndexed { i, ml -> com.sohum.bandlog.data.WaterEntry("dbg-w$i", Dates.today(), ml, now.minusMinutes(40L * i).toString(), "glass") },
                    )
                    seeded = true
                }
                Surface(Modifier.fillMaxSize(), color = palette.bg) {
                    if (!seeded) return@Surface
                    val today = Dates.today()
                    when (screen) {
                        "water" -> com.sohum.bandlog.ui.today.WaterScreen(vm) { finish() }
                        "squadcreate", "squadicon", "squadpolicy" -> {
                            val sq: com.sohum.bandlog.ui.squad.SquadViewModel = viewModel()
                            com.sohum.bandlog.ui.squad.CreateSquadFlow(sq, "Sohum", initialStep = when (screen) { "squadicon" -> 1; "squadpolicy" -> 2; else -> 0 }) { finish() }
                        }
                        "username", "userphoto" -> com.sohum.bandlog.ui.squad.UsernameFlow(vm, initialStep = if (screen == "userphoto") 1 else 0) { finish() }
                        "squadboard", "squadinfo", "squadfeed", "squadchat", "squadchallenges", "squadchallenge" -> {
                            val sq: com.sohum.bandlog.ui.squad.SquadViewModel = viewModel()
                            var ready by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) { seedSquad(sq); seedChallenges(sq, detail = screen == "squadchallenge"); ready = true }
                            val s = sq.open
                            if (ready && s != null) {
                                if (screen == "squadinfo") com.sohum.bandlog.ui.squad.SquadInfoPage(sq, s) { finish() }
                                else if (screen == "squadchallenge") com.sohum.bandlog.ui.squad.ChallengeDetailPage(sq, s)
                                else com.sohum.bandlog.ui.squad.SquadPage(sq, s, initialTab = when (screen) { "squadchat" -> 0; "squadchallenges" -> 1; "squadfeed" -> 2; else -> 3 })
                            }
                        }
                        "workout" -> LogScreen(vm, null, today, startOnMeal = false, onClose = { finish() })
                        "roti", "whey", "loose", "idli", "dal" -> {
                            val id = when (screen) { "loose" -> "chicken-breast"; "idli" -> "idli"; "dal" -> "dal-tadka"; else -> screen }
                            val pr = PRESETS.firstOrNull { it.id == id } ?: PRESETS.first()
                            QuantitySheet(QuantityFood.from(pr), onDone = { _, _ -> }, onDismiss = { finish() })
                        }
                        else -> LogScreen(vm, null, today, startOnMeal = true, onClose = { finish() })
                    }
                }
            }
        }
    }

    private fun seedChallenges(sq: com.sohum.bandlog.ui.squad.SquadViewModel, detail: Boolean) {
        val t = Dates.today()
        fun ch(id: String, kind: String, title: String, target: Int, start: Long, len: Int, status: String, mine: Int, leader: String?, lp: Int, done: Int, protein: Int? = null) =
            com.sohum.bandlog.data.Challenge(id, kind, title, target, protein, Dates.addDays(t, start), Dates.addDays(t, start + len - 1), "me", "Sohum", status, mine, leader, lp, 3, done)
        val list = listOf(
            ch("c1", "train_days", "Train 10 of 14 days", 10, -9, 14, "active", 7, "Ayaan Khan", 8, 0),
            ch("c2", "protein_days", "Hit 120 g protein 5 of 7 days", 5, 1, 7, "upcoming", 0, null, 0, 0, 120),
            ch("c3", "log_days", "Log food every day for 7 days", 7, -12, 7, "ended", 7, "Hobby H", 7, 2),
        )
        val board = listOf(
            com.sohum.bandlog.data.ChallengeBoardRow("u2", "Ayaan Khan", "ayaan24", null, 8, false, null, 1),
            com.sohum.bandlog.data.ChallengeBoardRow("me", "Sohum", "sohum", null, 7, false, null, 2),
            com.sohum.bandlog.data.ChallengeBoardRow("u3", "Hobby H", "hobby24", null, 3, false, null, 3),
        )
        sq.debugSeedChallenges(list, board, if (detail) "c1" else null)
    }

    private fun seedSquad(sq: com.sohum.bandlog.ui.squad.SquadViewModel) {
        val squad = com.sohum.bandlog.data.Squad("dbg-sq", "Fuel & Flex", "FLEX42", "me", description = "Protein first, excuses last.", icon = "salad", joinPolicy = "open")
        val t = java.time.OffsetDateTime.now()
        val trophy = String(Character.toChars(0x1F3C6))
        fun post(id: String, kind: String, body: String, name: String, user: String, mins: Long) =
            com.sohum.bandlog.data.GroupPost(id, "dbg-sq", user, kind, body, null, null, t.minusMinutes(mins).toString(), name, name.lowercase().substringBefore(' ') + "24", null)
        sq.debugSeed(
            listOf(squad),
            listOf(
                post("m2", "message", "In. Bringing the bands", "Hobby H", "u3", 3),
                post("m1", "message", "Leg day at 6?", "Ayaan Khan", "u2", 5),
                post("p1", "pr", "$trophy Bench press 45 kg \u00D7 8", "Ayaan Khan", "u2", 12),
                post("p2", "meal", "logged Dal tadka + Roti + Curd \u00B7 420 kcal", "Ayaan Khan", "u2", 50),
                post("p3", "workout", "Gym \u00B7 5 exercises \u00B7 42 min", "Hobby H", "u3", 95),
            ),
            listOf(
                com.sohum.bandlog.data.LeaderRow("u2", "Ayaan Khan", "ayaan24", null, 12, 340),
                com.sohum.bandlog.data.LeaderRow("u3", "Hobby H", "hobby24", null, 7, 210),
                com.sohum.bandlog.data.LeaderRow("u4", "Riya Menon", "riya.lifts", null, 3, 90),
            ),
            listOf(
                com.sohum.bandlog.data.MemberDetail("u3", "Hobby H", "hobby24", null, true, 7, ""),
                com.sohum.bandlog.data.MemberDetail("u2", "Ayaan Khan", "ayaan24", null, false, 12, ""),
                com.sohum.bandlog.data.MemberDetail("u4", "Riya Menon", "riya.lifts", null, false, 3, ""),
            ),
        )
    }

    private fun seedWorkouts(): List<Workout> {
        val d2 = Dates.addDays(Dates.today(), -2)
        val d5 = Dates.addDays(Dates.today(), -5)
        return listOf(
            Workout(
                "dbg-gym", d2, listOf("Chest", "Triceps", "Quads", "Glutes"), "Medium", null, 45, "", "", kind = "gym",
                lifts = listOf(
                    Lift("Bench press", listOf(LiftSet(40.0, 8), LiftSet(40.0, 8), LiftSet(42.5, 6))),
                    Lift("Squat", listOf(LiftSet(60.0, 8), LiftSet(60.0, 8), LiftSet(60.0, 8))),
                ),
            ),
            Workout("dbg-band", d5, listOf("Back", "Biceps"), "Heavy", 12.0, 30, "Rows, curls", ""),
        )
    }

    private companion object {
        fun fp(id: String, label: String, hi: String?, cat: String, servings: List<Serving>, def: String, kcal: Double, p: Double, c: Double, f: Double) =
            FoodPreset(id, id, label, hi, cat, servings, def, 0, null, label, kcal, p, c, f, emptyMap(), null)

        val PRESETS: List<FoodPreset> = listOf(
        fp("poha", "Poha", "पोहा", "breakfast", listOf(Serving("1 katori", 150.0), Serving("1 plate", 200.0), Serving("1 small bowl", 98.0)), "1 katori", 180.5, 4.9, 21.5, 8.1),
        fp("upma", "Upma / khara bath", "उपमा", "breakfast", listOf(Serving("1 katori", 150.0), Serving("1 plate", 200.0), Serving("1 small bowl", 106.0)), "1 katori", 147.9, 3.3, 16.3, 7.5),
        fp("idli", "Idli", "इडली", "breakfast", listOf(Serving("1 idli", 40.0), Serving("2 idli", 80.0), Serving("3 idli", 120.0), Serving("4 idli", 160.0)), "1 idli", 137.5, 4.6, 28.2, 0.3),
        fp("plain-dosa", "Plain dosa", "सादा डोसा", "breakfast", listOf(Serving("1 dosa", 100.0), Serving("2 dosa", 200.0)), "1 dosa", 168.0, 4.0, 28.0, 4.0),
        fp("masala-dosa", "Masala dosa", "मसाला डोसा", "breakfast", listOf(Serving("1 masala dosa", 210.0), Serving("½ masala dosa", 105.0)), "1 masala dosa", 164.6, 3.3, 19.6, 7.8),
        fp("rava-dosa", "Rava dosa", "रवा डोसा", "breakfast", listOf(Serving("1 dosa", 73.0), Serving("2 dosa", 147.0)), "1 dosa", 227.1, 7.3, 32.8, 7.1),
        fp("aloo-paratha", "Aloo paratha", "आलू पराठा", "breakfast", listOf(Serving("1 paratha", 93.0), Serving("2 paratha", 187.0)), "1 paratha", 205.0, 3.7, 23.9, 10.2),
        fp("plain-paratha", "Plain paratha", "सादा पराठा", "breakfast", listOf(Serving("1 paratha", 56.0), Serving("2 paratha", 112.0), Serving("3 paratha", 167.0)), "1 paratha", 298.3, 5.1, 30.7, 16.9),
        fp("bread-white", "Bread / toast (white)", "ब्रेड / टोस्ट", "breakfast", listOf(Serving("1 slice", 30.0), Serving("2 slices", 60.0), Serving("4 slices", 120.0)), "1 slice", 265.0, 9.0, 49.0, 3.2),
        fp("omelette", "Omelette", "ऑमलेट", "breakfast", listOf(Serving("1 egg", 60.0), Serving("2 eggs", 120.0), Serving("3 eggs", 180.0)), "1 egg", 169.7, 16.5, 0.0, 11.6),
        fp("boiled-egg", "Boiled egg", "उबला अंडा", "breakfast", listOf(Serving("1 egg", 50.0), Serving("2 eggs", 100.0), Serving("3 eggs", 150.0)), "1 egg", 147.7, 13.4, 0.0, 10.5),
        fp("oats", "Oats porridge", "ओट्स दलिया", "breakfast", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 250.0), Serving("1 large bowl", 398.0)), "1 katori", 72.9, 2.6, 8.8, 3.2),
        fp("cornflakes", "Cornflakes with milk", "कॉर्नफ्लेक्स दूध के साथ", "breakfast", listOf(Serving("1 bowl", 278.0), Serving("½ bowl", 139.0)), "1 bowl", 117.3, 3.6, 14.9, 5.1),
        fp("kesari-bath", "Kesari bath / sheera", "केसरी भात / शीरा", "breakfast", listOf(Serving("1 katori", 100.0), Serving("1 bowl", 177.0)), "1 katori", 244.6, 2.4, 31.4, 12.6),
        fp("medu-vada", "Medu vada", "मेदू वड़ा", "breakfast", listOf(Serving("1 vada", 60.0), Serving("2 vada", 120.0), Serving("3 vada", 180.0)), "1 vada", 250.0, 7.0, 28.0, 13.0),
        fp("uttapam", "Uttapam", "उत्तपम", "breakfast", listOf(Serving("1 uttapam", 68.0), Serving("2 uttapam", 136.0)), "1 uttapam", 255.9, 6.2, 36.3, 9.0),
        fp("sabudana-khichdi", "Sabudana khichdi", "साबूदाना खिचड़ी", "breakfast", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 271.0)), "1 katori", 187.2, 2.7, 22.4, 10.1),
        fp("thepla", "Methi thepla", "मेथी थेपला", "breakfast", listOf(Serving("1 thepla", 36.0), Serving("2 thepla", 73.0), Serving("3 thepla", 109.0)), "1 thepla", 346.2, 9.0, 41.3, 15.9),
        fp("dhokla", "Dhokla", "ढोकला", "breakfast", listOf(Serving("1 piece", 40.0), Serving("2 pieces", 80.0), Serving("4 pieces", 160.0)), "1 piece", 216.5, 13.4, 30.7, 5.3),
        fp("pesarattu", "Pesarattu (moong dosa)", "पेसरट्टू", "breakfast", listOf(Serving("1 pesarattu", 100.0), Serving("2 pesarattu", 200.0)), "1 pesarattu", 286.0, 12.3, 31.9, 11.7),
        fp("besan-chilla", "Besan chilla", "बेसन चीला", "breakfast", listOf(Serving("1 chilla", 52.0), Serving("2 chilla", 104.0), Serving("3 chilla", 156.0)), "1 chilla", 135.9, 7.8, 21.0, 2.9),
        fp("coconut-chutney", "Coconut chutney", "नारियल चटनी", "breakfast", listOf(Serving("2 tbsp", 50.0), Serving("1 tbsp", 25.0), Serving("¼ katori", 40.0)), "2 tbsp", 265.9, 3.6, 8.3, 25.0),
        fp("roti", "Roti / chapati", "रोटी", "staple", listOf(Serving("1 roti", 40.0), Serving("2 roti", 80.0), Serving("3 roti", 120.0)), "2 roti", 264.0, 8.5, 46.0, 5.5),
        fp("phulka", "Phulka (no ghee)", "फुल्का", "staple", listOf(Serving("1 phulka", 36.0), Serving("2 phulka", 72.0), Serving("3 phulka", 108.0), Serving("4 phulka", 144.0)), "1 phulka", 202.3, 5.9, 35.6, 3.6),
        fp("rice", "Rice (cooked)", "चावल", "staple", listOf(Serving("1 katori", 150.0), Serving("1 plate", 300.0), Serving("1 ladle", 60.0)), "1 katori", 117.2, 2.6, 25.7, 0.2),
        fp("jeera-rice", "Jeera rice", "जीरा चावल", "staple", listOf(Serving("1 katori", 150.0), Serving("1 plate", 271.0)), "1 katori", 135.2, 2.5, 23.6, 3.2),
        fp("brown-rice", "Brown rice (cooked)", "ब्राउन राइस", "staple", listOf(Serving("1 katori", 150.0), Serving("1 plate", 250.0)), "1 katori", 123.0, 2.7, 26.0, 1.0),
        fp("khichdi", "Khichdi", "खिचड़ी", "staple", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 200.0), Serving("2 katori", 300.0)), "1 katori", 143.2, 5.6, 19.6, 4.5),
        fp("veg-pulao", "Veg pulao", "वेज पुलाव", "staple", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 200.0), Serving("1 full plate", 438.0)), "1 katori", 113.0, 2.7, 17.5, 3.3),
        fp("veg-biryani", "Veg biryani", "वेज बिरयानी", "staple", listOf(Serving("1 plate", 302.0), Serving("1 katori", 150.0)), "1 plate", 174.6, 3.2, 18.6, 9.5),
        fp("chicken-biryani", "Chicken biryani", "चिकन बिरयानी", "staple", listOf(Serving("1 plate", 300.0), Serving("1 katori", 150.0)), "1 plate", 180.0, 9.0, 22.0, 6.0),
        fp("mutton-biryani", "Mutton biryani", "मटन बिरयानी", "staple", listOf(Serving("1 plate", 208.0), Serving("1 katori", 150.0), Serving("1 large plate", 300.0)), "1 plate", 190.8, 7.4, 22.5, 7.7),
        fp("naan", "Naan", "नान", "staple", listOf(Serving("1 naan", 53.0), Serving("2 naan", 106.0), Serving("1 large naan", 90.0)), "1 naan", 286.4, 8.1, 51.8, 5.0),
        fp("makki-roti", "Makki ki roti", "मक्की की रोटी", "staple", listOf(Serving("1 roti", 85.0), Serving("2 roti", 170.0)), "1 roti", 264.0, 3.5, 24.2, 16.8),
        fp("curd-rice", "Curd rice", "दही चावल", "staple", listOf(Serving("1 katori", 150.0), Serving("1 plate", 216.0)), "1 katori", 195.7, 5.8, 32.9, 4.3),
        fp("lemon-rice", "Lemon rice", "नींबू चावल", "staple", listOf(Serving("1 katori", 150.0), Serving("1 plate", 319.0)), "1 katori", 176.3, 4.3, 21.6, 7.9),
        fp("dal-tadka", "Dal tadka / dal fry", "दाल तड़का", "dal", listOf(Serving("1 katori", 150.0), Serving("2 katori", 300.0), Serving("1 bowl", 200.0)), "1 katori", 105.0, 6.5, 16.0, 1.5),
        fp("toor-dal", "Toor / arhar dal", "अरहर / तूर दाल", "dal", listOf(Serving("1 katori", 150.0), Serving("2 katori", 300.0), Serving("1 bowl", 200.0)), "1 katori", 105.0, 6.5, 16.0, 1.5),
        fp("moong-dal", "Moong dal", "मूंग दाल", "dal", listOf(Serving("1 katori", 150.0), Serving("2 katori", 300.0), Serving("1 bowl", 200.0)), "1 katori", 105.0, 6.5, 16.0, 1.5),
        fp("masoor-dal", "Masoor dal", "मसूर दाल", "dal", listOf(Serving("1 katori", 150.0), Serving("2 katori", 300.0), Serving("1 bowl", 200.0)), "1 katori", 105.0, 6.5, 16.0, 1.5),
        fp("chana-dal", "Chana dal", "चना दाल", "dal", listOf(Serving("1 katori", 150.0), Serving("2 katori", 300.0), Serving("1 bowl", 292.0)), "1 katori", 99.7, 4.2, 10.0, 4.6),
        fp("rajma", "Rajma", "राजमा", "dal", listOf(Serving("1 katori", 150.0), Serving("2 katori", 300.0), Serving("1 bowl", 200.0)), "1 katori", 143.7, 6.0, 16.4, 5.8),
        fp("chole", "Chole / chana masala", "छोले", "dal", listOf(Serving("1 katori", 150.0), Serving("2 katori", 300.0), Serving("1 bowl", 200.0)), "1 katori", 163.4, 6.1, 20.0, 6.8),
        fp("kala-chana", "Kala chana curry", "काला चना", "dal", listOf(Serving("1 katori", 150.0), Serving("2 katori", 300.0), Serving("1 bowl", 200.0)), "1 katori", 140.7, 5.7, 14.1, 6.6),
        fp("sambar", "Sambar", "सांभर", "dal", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 257.0), Serving("1 ladle", 60.0)), "1 katori", 96.9, 3.4, 10.6, 4.4),
        fp("rasam", "Rasam", "रसम", "dal", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 390.0)), "1 katori", 26.7, 1.1, 3.4, 0.9),
        fp("dal-makhani", "Dal makhani", "दाल मखनी", "dal", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 287.0)), "1 katori", 103.1, 3.9, 8.7, 5.7),
        fp("dal-palak", "Dal palak", "दाल पालक", "dal", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 200.0)), "1 katori", 52.8, 2.1, 4.8, 2.8),
        fp("panchmel-dal", "Panchmel dal", "पंचमेल दाल", "dal", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 301.0)), "1 katori", 111.2, 4.6, 10.7, 5.4),
        fp("lobia", "Lobia curry", "लोबिया", "dal", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 200.0)), "1 katori", 149.0, 6.1, 17.9, 5.6),
        fp("egg-whole", "Egg (whole)", "अंडा", "protein", listOf(Serving("1 egg", 50.0), Serving("2 eggs", 100.0), Serving("3 eggs", 150.0)), "1 egg", 134.8, 13.3, 0.0, 9.2),
        fp("egg-white", "Egg white", "अंडे की सफ़ेदी", "protein", listOf(Serving("1 egg white", 33.0), Serving("2 egg whites", 66.0), Serving("4 egg whites", 132.0)), "1 egg white", 52.6, 12.4, 0.0, 0.3),
        fp("paneer", "Paneer (raw)", "पनीर", "protein", listOf(Serving("100 g", 100.0), Serving("50 g", 50.0), Serving("200 g pack", 200.0)), "100 g", 257.9, 18.9, 12.4, 14.8),
        fp("whey", "Whey protein", "व्हे प्रोटीन", "protein", listOf(Serving("1 scoop", 30.0), Serving("½ scoop", 15.0), Serving("2 scoops", 60.0)), "1 scoop", 400.0, 80.0, 8.0, 6.0),
        fp("chicken-curry", "Chicken curry", "चिकन करी", "protein", listOf(Serving("1 katori (2 pcs)", 150.0), Serving("1 bowl", 248.0)), "1 katori (2 pcs)", 129.2, 11.8, 3.4, 7.6),
        fp("chicken-breast", "Chicken breast (grilled)", "चिकन ब्रेस्ट", "protein", listOf(Serving("150 g", 150.0), Serving("100 g", 100.0), Serving("200 g", 200.0)), "150 g", 165.0, 31.0, 0.0, 3.6),
        fp("tandoori-chicken", "Tandoori chicken / tikka", "तंदूरी चिकन", "protein", listOf(Serving("5-6 pieces", 150.0), Serving("¼ chicken", 360.0), Serving("½ chicken", 720.0)), "5-6 pieces", 145.2, 16.3, 2.3, 7.9),
        fp("butter-chicken", "Butter chicken", "बटर चिकन", "protein", listOf(Serving("1 katori", 150.0), Serving("1 serving", 275.0)), "1 katori", 137.0, 10.9, 3.7, 8.7),
        fp("egg-curry", "Egg curry", "अंडा करी", "protein", listOf(Serving("1 katori", 152.0), Serving("2 katori", 303.0)), "1 katori", 117.5, 5.4, 4.0, 8.8),
        fp("egg-bhurji", "Egg bhurji / scrambled", "अंडा भुर्जी", "protein", listOf(Serving("1 egg", 70.0), Serving("2 eggs", 140.0), Serving("3 eggs", 210.0)), "1 egg", 156.0, 10.3, 1.4, 12.2),
        fp("fish-curry", "Fish curry", "मछली करी", "protein", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 254.0)), "1 katori", 111.1, 8.8, 3.8, 6.7),
        fp("prawn-curry", "Prawn curry", "झींगा करी", "protein", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 286.0)), "1 katori", 109.5, 8.5, 3.1, 6.9),
        fp("mutton-curry", "Mutton curry / rogan josh", "मटन करी", "protein", listOf(Serving("1 katori", 150.0), Serving("1 bowl", 236.0)), "1 katori", 139.6, 9.6, 4.9, 9.0),
        fp("curd", "Curd / dahi", "दही", "protein", listOf(Serving("1 katori", 150.0), Serving("1 cup", 200.0), Serving("2 tbsp", 30.0)), "1 katori", 62.0, 3.5, 4.7, 3.3),
        fp("greek-yogurt", "Greek yogurt", "ग्रीक योगर्ट", "protein", listOf(Serving("1 small tub", 100.0), Serving("1 cup", 170.0)), "1 small tub", 97.0, 9.0, 3.9, 5.0),
        fp("sprouts", "Moong sprouts", "अंकुरित मूंग", "protein", listOf(Serving("1 katori", 100.0), Serving("1 bowl", 150.0)), "1 katori", 30.0, 3.0, 6.0, 0.2),
        fp("soya-chunks", "Soya chunks (cooked)", "सोया चंक्स", "protein", listOf(Serving("1 katori", 100.0), Serving("1 bowl", 150.0)), "1 katori", 140.0, 20.0, 12.0, 1.0),
        fp("tofu", "Tofu", "टोफू", "protein", listOf(Serving("100 g", 100.0), Serving("150 g", 150.0), Serving("200 g pack", 200.0)), "100 g", 76.0, 8.0, 1.9, 4.8),
        fp("chana-boiled", "Kala chana (boiled)", "उबला काला चना", "protein", listOf(Serving("½ katori", 75.0), Serving("1 katori", 150.0)), "½ katori", 160.0, 9.0, 25.0, 3.0),
        fp("peanuts", "Peanuts", "मूंगफली", "protein", listOf(Serving("1 handful", 30.0), Serving("1 tbsp", 10.0), Serving("2 handfuls", 60.0)), "1 handful", 520.1, 23.7, 17.3, 39.6),
        fp("paneer-tikka", "Paneer tikka / shashlik", "पनीर टिक्का", "protein", listOf(Serving("1 plate", 439.0), Serving("½ plate", 220.0)), "1 plate", 93.8, 5.1, 8.0, 4.5),
        )
    }
}
