package com.sohum.bandlog.util

import com.sohum.bandlog.data.PlateItem
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Mirrors the web's src/lib/food/voicePlate.ts (v2.18 A1 photo + voice logging, the pure half; the
 * web copy wins). What the person says with a plate photo — "2 roti, less oil, extra dal", "do roti
 * aur thoda chawal", "no papad, plus a glass of chaas", "तीन रोटी" — is parsed into amounts and oil
 * cues and merged into the photo's items deterministically:
 *
 *   applyVoiceAmounts   counts ("2 roti" → 2 × the photo's own grams per roti), units ("1 katori
 *                       rice" → 150 g), extra / less / half / double / none, and foods the photo
 *                       didn't show (added at a typical portion, flagged [PlateItem.fromVoice]).
 *   applyVoiceOil       "less oil" (−30 % fat on oily dishes), "no oil / bina ghee" (−60 %),
 *                       "extra ghee / with butter" (+5 g fat on the dish it names, else the biggest).
 *
 * Amounts run BEFORE the web check, oil AFTER it. The merge is "set", not "add".
 * Tokenising splits on non-letters (\p{L}\p{M}) instead of relying on \b, so Devanagari works.
 * Same cases as scripts/check-v218-food.ts ↔ VoicePlateTest.
 */
object VoicePlate {
    /** set | extra | double | less | half | none */
    data class VoiceSeg(
        val raw: String,
        /** The food words as spoken, synonyms folded ("chapati" → "roti"), or null for a pure oil cue. */
        val food: String?,
        val count: Double?,
        /** Grams of ONE unit when the person named one ("katori" 150, "glass" 250), or an explicit gram amount. */
        val unitGrams: Double?,
        val unitLabel: String?,
        val mod: String,
    )

    /** level: less | none | extra */
    data class OilCue(val level: String, val food: String?)
    data class VoiceParse(val segments: List<VoiceSeg>, val oil: List<OilCue>)
    data class VoiceMerge(val items: List<PlateItem>, val changes: List<String>, val added: List<Int>)

    private val NUMBER_WORDS: Map<String, Double> = mapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0, "couple" to 2.0, "dozen" to 12.0,
        "ek" to 1.0, "do" to 2.0, "teen" to 3.0, "tin" to 3.0, "char" to 4.0, "chaar" to 4.0, "paanch" to 5.0, "panch" to 5.0, "chhe" to 6.0, "chhah" to 6.0, "saat" to 7.0, "aath" to 8.0, "nau" to 9.0, "das" to 10.0,
        "dedh" to 1.5, "dhai" to 2.5, "sawa" to 1.25,
        "एक" to 1.0, "दो" to 2.0, "तीन" to 3.0, "चार" to 4.0, "पाँच" to 5.0, "पांच" to 5.0, "छह" to 6.0, "सात" to 7.0, "आठ" to 8.0, "नौ" to 9.0, "दस" to 10.0, "डेढ़" to 1.5, "ढाई" to 2.5,
    )

    private class UnitDef(val grams: Double?, val label: String)

    /** grams of one unit; "piece"-like units are null (the food's own piece weight is used). */
    private val UNITS: Map<String, UnitDef> = run {
        val m = HashMap<String, UnitDef>()
        fun put(g: Double?, label: String, vararg keys: String) = keys.forEach { m[it] = UnitDef(g, label) }
        put(150.0, "katori", "katori", "katoris", "कटोरी")
        put(200.0, "bowl", "bowl", "bowls")
        put(250.0, "plate", "plate", "plates")
        put(250.0, "glass", "glass", "glasses", "गिलास")
        put(150.0, "cup", "cup", "cups")
        put(15.0, "spoon", "spoon", "spoons", "chammach", "chamach", "चम्मच")
        put(15.0, "tbsp", "tbsp")
        put(5.0, "tsp", "tsp", "teaspoon")
        put(60.0, "ladle", "ladle", "karchi")
        put(30.0, "scoop", "scoop", "scoops")
        put(30.0, "handful", "handful")
        put(30.0, "slice", "slice", "slices")
        put(null, "piece", "piece", "pieces", "pc", "pcs", "tukda")
        m
    }
    private val GRAM_WORDS = setOf("g", "gm", "gms", "gram", "grams", "ml")

    /** Typical one-piece weights (the same numbers as quantity.ts PIECE_GRAMS). */
    val PIECES: Map<String, Double> = mapOf(
        "roti" to 40.0, "paratha" to 80.0, "naan" to 90.0, "kulcha" to 80.0, "puri" to 25.0, "bhatura" to 70.0, "idli" to 40.0, "dosa" to 100.0, "uttapam" to 120.0, "vada" to 50.0, "appam" to 60.0,
        "dhokla" to 30.0, "samosa" to 60.0, "kachori" to 50.0, "momo" to 25.0, "egg" to 50.0, "toast" to 30.0, "bread" to 30.0, "ladoo" to 40.0, "cookie" to 12.0, "biscuit" to 10.0, "banana" to 120.0,
        "apple" to 180.0, "orange" to 130.0, "papad" to 12.0, "pakora" to 20.0, "cutlet" to 60.0, "kebab" to 40.0, "tikki" to 60.0, "thepla" to 50.0, "chilla" to 70.0,
    )

    private val SYNONYMS: Map<String, String> = mapOf(
        "chapati" to "roti", "chapatti" to "roti", "phulka" to "roti", "fulka" to "roti", "rotis" to "roti", "chapatis" to "roti", "रोटी" to "roti",
        "daal" to "dal", "dhal" to "dal", "दाल" to "dal",
        "chawal" to "rice", "chaawal" to "rice", "bhaat" to "rice", "bhat" to "rice", "चावल" to "rice",
        "dahi" to "curd", "yogurt" to "curd", "yoghurt" to "curd", "दही" to "curd",
        "sabji" to "sabzi", "subzi" to "sabzi", "subji" to "sabzi", "sabjee" to "sabzi", "bhaji" to "sabzi", "सब्जी" to "sabzi",
        "anda" to "egg", "ande" to "egg", "eggs" to "egg", "अंडा" to "egg",
        "murgh" to "chicken", "murg" to "chicken",
        "buttermilk" to "chaas", "chhach" to "chaas", "chhaas" to "chaas", "chaach" to "chaas", "छाछ" to "chaas",
        "achaar" to "achar", "pickle" to "achar",
        "poori" to "puri", "parantha" to "paratha", "laddu" to "ladoo",
        "potato" to "aloo", "alu" to "aloo",
        "salaad" to "salad", "papadum" to "papad", "pappad" to "papad",
    )

    private val STOP = setOf(
        "of", "with", "the", "and", "some", "my", "i", "had", "ate", "have", "also", "bhi", "ka", "ki", "ke", "wala", "wali", "sa", "si", "se", "on", "top",
        "in", "it", "is", "was", "there", "its", "it's", "that", "this", "plus", "aur", "or", "just", "only", "about", "around", "like", "for", "me", "mera", "meri",
        "tha", "thi", "hai", "hain", "liya", "khaya", "khayi", "khaaya", "add", "added", "please", "side", "along", "abhi", "yeh", "ye", "wo", "woh", "usme", "mein",
    )

    private val MOD_WORDS: Map<String, String> = run {
        val m = HashMap<String, String>()
        fun put(mod: String, vararg keys: String) = keys.forEach { m[it] = mod }
        put("extra", "extra", "more", "zyada", "jyada", "jada", "zyaada", "bada", "badi", "big", "large", "full", "heap")
        put("double", "double")
        put("less", "less", "kam", "thoda", "thodi", "thora", "little", "small", "chhota", "chota", "light", "bit")
        put("half", "half", "aadha", "adha", "aadhi", "adhi", "आधा", "आधी")
        put("none", "no", "without", "bina", "skip", "skipped", "nahi", "nahin", "not", "didnt", "didn", "none", "zero")
        m
    }

    private val OIL_WORDS = setOf("oil", "tel", "ghee", "butter", "makhan", "makkhan", "tadka", "oily", "greasy", "fried", "तेल", "घी")
    private val OIL_LESS = setOf("less", "kam", "thoda", "thodi", "little", "light", "low", "lite")
    private val OIL_NONE = setOf("no", "without", "bina", "zero", "free", "nahi", "nahin", "none", "dry")
    private val OIL_EXTRA = setOf("extra", "more", "zyada", "jyada", "with", "added", "double", "lots", "loaded", "top")

    /** Dishes that carry cooking fat (where "less oil" applies when no dish is named). */
    val OILY = Regex("dal|curry|masala|paneer|sabzi|bhaji|bhurji|fry|fried|paratha|pulao|biryani|khichdi|rajma|chole|chana|kadhi|gravy|korma|makhani|tadka|poha|upma|omelette|dosa|keema|aloo|gobi|bhindi|baingan|palak|matar|kofta|egg|chicken|mutton|fish|pakora|puri|bhatura|samosa|vada|noodles|maggi|fried rice", RegexOption.IGNORE_CASE)

    /** Dishes a count without a unit means katoris of. */
    private val SPOONABLE = Regex("dal|curry|sabzi|rajma|chole|chana|kadhi|raita|curd|khichdi|rice|pulao|biryani|poha|upma|sambar|rasam|halwa|kheer|soup|gravy|korma", RegexOption.IGNORE_CASE)

    /** Words that are food on their own ("chaas bhi" adds chaas); anything else needs an amount to be added. */
    private val KNOWN_FOOD = JsRe.re("\\b(chaas|lassi|salad|papad|achar|curd|raita|chutney|milk|tea|chai|coffee|juice|sweet|mithai|fruit|banana|apple|orange|mango|watermelon|onion|cucumber|sprouts|paneer|chicken|mutton|fish|egg|rice|roti|dal|sabzi|curry|soup|ghee|butter|cheese|bread|idli|dosa|vada|poha|upma|khichdi|biryani|pulao|halwa|kheer|ladoo|gulab jamun|rasgulla|jalebi|samosa|pakora|namkeen|bhujia|makhana|peanut|nuts|almond|whey|shake|smoothie|oats|muesli|cornflakes|pickle)\\b")

    /** Words that end in "s" but aren't plurals. */
    private val NOT_PLURAL = setOf("chaas", "chhaas", "oats", "chips", "fries", "peas", "beans", "nuts", "sprouts", "rajmas", "khus", "dhokla", "ras", "chaats")

    private fun jsRound(v: Double) = floor(v + 0.5)
    private fun r1(v: Double) = jsRound(v * 10) / 10

    private val ES_PLURAL = Regex("(ch|sh|x|o)es$")

    private fun singular(t: String): String {
        if (t in NOT_PLURAL) return t
        if (t.length > 3 && t.endsWith("es") && ES_PLURAL.containsMatchIn(t)) return t.dropLast(2)
        if (t.length > 3 && t.endsWith("s") && !t.endsWith("ss")) return t.dropLast(1)
        return t
    }

    private val PARENS = Regex("\\(.*?\\)")
    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{M}0-9']+")

    /** Lower-case word tokens with synonyms folded and plurals trimmed ("Chapatis" → "roti"). */
    fun foodTokens(s: String): List<String> =
        s.lowercase().replace(PARENS, " ").split(TOKEN_SPLIT).filter { it.isNotEmpty() }.map { t -> SYNONYMS[t] ?: SYNONYMS[singular(t)] ?: singular(t) }

    private val DECIMAL = Regex("^[0-9]+(\\.[0-9]+)?$")
    private val FRACTION = Regex("^[0-9]+/[0-9]+$")
    private val DEVANAGARI_DIGITS = Regex("^[०-९]+$")

    private fun parseNumber(tok: String): Double? {
        if (DECIMAL.matches(tok)) return tok.toDouble()
        if (FRACTION.matches(tok)) {
            val (a, b) = tok.split("/").map { it.toDouble() }
            return if (b != 0.0) a / b else null
        }
        when (tok) { "½" -> return 0.5; "¼" -> return 0.25; "¾" -> return 0.75 }
        if (DEVANAGARI_DIGITS.matches(tok)) return tok.map { "०१२३४५६७८९".indexOf(it) }.joinToString("").toDouble()
        return NUMBER_WORDS[tok]
    }

    // Letters/marks on either side mean "inside a word" (JS \b is ASCII-only, so it never splits Devanagari).
    private const val NL = "(?<![\\p{L}\\p{M}])"
    private const val NR = "(?![\\p{L}\\p{M}])"
    private val SEG_PUNCT = Regex("[.!?;\\n]+")
    private val SEG_SPLIT = Regex(",|" + JsRe.re("\\band\\b|\\baur\\b|\\bplus\\b|&|\\bthen\\b|\\balso\\b").pattern + "|${NL}या$NR|${NL}और$NR")

    /** Split an utterance into segments on commas, "and", "aur", "plus", "&", "then", "also", "या", "और". */
    private fun splitSegments(text: String): List<String> =
        text.lowercase().replace(SEG_PUNCT, ",").split(SEG_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }

    private val DIGIT_UNIT = JsRe.re("([0-9])(g|gm|gms|ml)\\b", ignoreCase = false)
    private val DIGIT_X = JsRe.re("([0-9])x\\b", ignoreCase = false)
    private val NT = JsRe.re("n't\\b", ignoreCase = false)
    private val SEG_TOKENS = Regex("[^\\p{L}\\p{M}0-9./½¼¾']+")
    private val OIL_FREE = Regex("oil[- ]?free")

    private class SegResult(val seg: VoiceSeg?, val oil: OilCue?)

    /** One segment → an oil cue (if any) and a food amount (if any). */
    private fun parseSegment(raw: String): SegResult {
        // "20g" / "150ml" → "20 g"; "2x" → "2"
        val toks = raw.replace(DIGIT_UNIT, "$1 $2").replace(DIGIT_X, "$1").replace(NT, " not")
            .split(SEG_TOKENS).filter { it.isNotEmpty() }

        // Oil cue: an oil word plus its direction anywhere in the segment.
        var oil: OilCue? = null
        val oilAt = toks.indexOfFirst { it in OIL_WORDS }
        val rest = ArrayList<String>()
        if (oilAt >= 0) {
            val level = when {
                toks.any { it in OIL_NONE } || OIL_FREE.containsMatchIn(raw) -> "none"
                toks.any { it in OIL_LESS } -> "less"
                toks.any { it in OIL_EXTRA } -> "extra"
                else -> "extra" // "fried" / "oily" / "greasy" / a bare oil word
            }
            // Everything that isn't the oil phrase may still name a food ("dal with extra ghee" → dal).
            for (t in toks) if (t !in OIL_WORDS && t !in OIL_LESS && t !in OIL_NONE && t !in OIL_EXTRA) rest.add(t)
            val foodWords = rest.filter { t -> t !in STOP && parseNumber(t) == null && UNITS[t] == null && t !in GRAM_WORDS && MOD_WORDS[t] == null }
            oil = OilCue(level, if (foodWords.isNotEmpty()) foodWords.joinToString(" ") { SYNONYMS[it] ?: it } else null)
            // "2 roti with ghee": the amount part still counts. A bare "less oil" has no food part.
            if (foodWords.isEmpty()) return SegResult(null, oil)
        } else rest.addAll(toks)

        var count: Double? = null
        var unitGrams: Double? = null
        var unitLabel: String? = null
        var mod = "set"
        val food = ArrayList<String>()
        var i = 0
        while (i < rest.size) {
            val t = rest[i]
            val n = parseNumber(t)
            val next = rest.getOrNull(i + 1)
            val article = t == "a" || t == "an"
            if (n != null && !article && next != null && next in GRAM_WORDS) {
                unitGrams = n
                unitLabel = if (next == "ml") "ml" else "g"
                count = 1.0
                i += 2
                continue
            }
            if (n != null) {
                // "a" / "an" only count when nothing else did ("a glass of chaas").
                if (count == null || !article) count = if (article) (count ?: 1.0) else n
                i++
                continue
            }
            val unit = UNITS[t]
            if (unit != null) {
                unitGrams = unit.grams
                unitLabel = unit.label
                i++
                continue
            }
            val m = MOD_WORDS[t]
            if (m != null) {
                // "half" with a count ("1 and a half") is rare; "half" alone is a modifier.
                if (m == "half" && count != null && mod == "set") count += 0.5
                else mod = if (m == "none" || mod == "set") m else mod
                i++
                continue
            }
            if (t in STOP || t in GRAM_WORDS) { i++; continue }
            food.add(t)
            i++
        }
        if (food.isEmpty()) return SegResult(null, oil)
        // "didn't eat the papad" / "no papad": a count means nothing then.
        if (mod == "none") count = null
        // Synonyms folded for the name ("chawal" → "rice"), plurals kept as said ("oats" stays "oats").
        val name = food.joinToString(" ") { SYNONYMS[it] ?: it }
        return SegResult(VoiceSeg(raw, name, count, unitGrams, unitLabel, mod), oil)
    }

    fun parseVoice(text: String?): VoiceParse {
        val segments = ArrayList<VoiceSeg>()
        val oil = ArrayList<OilCue>()
        for (s in splitSegments(text.orEmpty().take(400))) {
            val r = parseSegment(s)
            r.seg?.let { segments.add(it) }
            r.oil?.let { oil.add(it) }
        }
        return VoiceParse(segments, oil)
    }

    /** Which item name a spoken food means (most shared tokens; −1 when none). */
    fun matchName(names: List<String>, food: String): Int {
        val want = foodTokens(food).filter { it !in STOP }
        var best = -1
        var bestScore = 0
        names.forEachIndexed { i, name ->
            val have = foodTokens(name).toSet()
            val score = want.count { it in have }
            if (score > bestScore) { best = i; bestScore = score }
        }
        return best
    }

    /** Which plate item a spoken food means (most shared tokens; −1 when none). */
    fun matchItem(items: List<PlateItem>, food: String): Int = matchName(items.map { it.name }, food)

    /** The countable noun of a food name ("masala dosa" → dosa), or null. */
    fun pieceNoun(name: String): String? = foodTokens(name).lastOrNull { PIECES.containsKey(it) }

    /** An item at [factor] × its grams, every number scaled with it (density unchanged). */
    fun scaleItem(it: PlateItem, factor: Double): PlateItem {
        val f = max(0.0, factor)
        return it.copy(
            grams = max(1.0, jsRound(it.grams * f)),
            calories = jsRound(it.calories * f),
            proteinG = r1(it.proteinG * f),
            carbsG = r1(it.carbsG * f),
            fatG = r1(it.fatG * f),
            micros = it.micros.filterValues { v -> v.isFinite() }.mapValues { e -> r1(e.value * f) },
            gramsLow = it.gramsLow?.let { v -> jsRound(v * f) },
            gramsHigh = it.gramsHigh?.let { v -> jsRound(v * f) },
        )
    }

    private fun pct(f: Double) = "${if (f >= 1) "+" else "−"}${jsRound(abs(f - 1) * 100).toLong()}%"
    private val MOD_FACTOR = mapOf("extra" to 1.5, "double" to 2.0, "less" to 0.7, "half" to 0.5)

    /** Target grams for a spoken amount on a food (existing item or not). null = no amount said. */
    private fun spokenGrams(seg: VoiceSeg, current: PlateItem?): Double? {
        if (seg.count == null && seg.unitGrams == null) return null
        val n = seg.count ?: 1.0
        if (seg.unitLabel == "g" || seg.unitLabel == "ml") return seg.unitGrams
        if (seg.unitGrams != null) return n * seg.unitGrams
        val noun = pieceNoun(seg.food.orEmpty()) ?: current?.let { pieceNoun(it.name) }
        if (noun != null) {
            val piece = PIECES.getValue(noun)
            if (current != null) {
                val had = max(1.0, jsRound(current.grams / piece))
                return n * (current.grams / had)
            }
            return n * piece
        }
        if (SPOONABLE.containsMatchIn(seg.food.orEmpty())) return n * 150
        // A count of something with no natural unit: that many of the photo's portion (or 100 g each).
        return n * (current?.grams ?: 100.0)
    }

    private fun title(s: String) = s.replaceFirstChar { it.uppercase() }

    /** Counts, units, extra / less / none and foods the photo missed. [VoiceMerge.added] lists new items (to look up). */
    fun applyVoiceAmounts(items: List<PlateItem>, p: VoiceParse): VoiceMerge {
        val out = items.toMutableList()
        val changes = ArrayList<String>()
        val removed = HashSet<Int>()
        val newItems = ArrayList<PlateItem>()
        for (seg in p.segments) {
            val food = seg.food ?: continue
            val idx = matchItem(out, food)
            if (idx >= 0 && idx !in removed) {
                val it = out[idx]
                if (seg.mod == "none") {
                    removed.add(idx)
                    changes.add("Removed ${it.name}")
                    continue
                }
                var grams = spokenGrams(seg, it)
                if (grams == null && seg.mod != "set") grams = it.grams * MOD_FACTOR.getValue(seg.mod)
                else if (grams != null && seg.mod != "set") grams *= MOD_FACTOR.getValue(seg.mod)
                if (grams == null || !(grams > 0)) continue
                val factor = grams / max(1.0, it.grams)
                if (abs(factor - 1) < 0.02) continue
                out[idx] = scaleItem(it, factor)
                val noun = pieceNoun(it.name)
                val shown = out[idx]
                if (seg.count != null && noun != null && seg.unitGrams == null) changes.add("${title(it.name)} → ${FoodBits.num(seg.count)} $noun (${FoodBits.num(shown.grams)} g)")
                else if (seg.count != null || seg.unitGrams != null) changes.add("${title(it.name)} → ${FoodBits.num(shown.grams)} g")
                else changes.add("${title(it.name)}: ${seg.mod} (${pct(factor)})")
                continue
            }
            if (seg.mod == "none") continue // "no papad" when there's no papad
            // "umm okay", "that's it": not food. A new item needs an amount or a word that is food on its own.
            if (seg.count == null && seg.unitGrams == null && !KNOWN_FOOD.containsMatchIn(food) && pieceNoun(food) == null && !OILY.containsMatchIn(food) && !SPOONABLE.containsMatchIn(food)) continue
            var grams = spokenGrams(seg, null)
            if (grams == null) {
                val noun = pieceNoun(food)
                grams = if (noun != null) PIECES.getValue(noun) else if (SPOONABLE.containsMatchIn(food)) 150.0 else 100.0
            }
            if (seg.mod != "set") grams *= MOD_FACTOR.getValue(seg.mod)
            grams = max(1.0, jsRound(grams))
            newItems.add(
                PlateItem(
                    name = food, grams = grams, confidence = "medium", calories = 0.0, proteinG = 0.0, carbsG = 0.0, fatG = 0.0,
                    micros = emptyMap(), source = "estimated", foodId = null, fromVoice = true,
                ),
            )
            changes.add("Added $food (${FoodBits.num(grams)} g) from your voice")
        }
        val kept = out.filterIndexed { i, _ -> i !in removed }
        val added = newItems.indices.map { kept.size + it }
        return VoiceMerge(kept + newItems, changes, added)
    }

    /** Oil cues on the (already priced) items. */
    fun applyVoiceOil(items: List<PlateItem>, p: VoiceParse): Pair<List<PlateItem>, List<String>> {
        var out = items.toList()
        val changes = ArrayList<String>()
        for (cue in p.oil) {
            val named = cue.food?.let { matchItem(out, it) } ?: -1
            if (cue.level == "extra") {
                var idx = named
                if (idx < 0) {
                    val all = out.withIndex().toList()
                    val oily = all.filter { OILY.containsMatchIn(it.value.name) }
                    val pool = oily.ifEmpty { all }
                    idx = pool.sortedByDescending { it.value.calories }.firstOrNull()?.index ?: -1
                }
                if (idx < 0) continue
                out = out.mapIndexed { i, it -> if (i == idx) it.copy(fatG = r1(it.fatG + 5), calories = it.calories + 45) else it }
                changes.add("${title(out[idx].name)}: +1 tsp ghee (+45 kcal)")
                continue
            }
            val keep = if (cue.level == "none") 0.4 else 0.7
            val targets = if (named >= 0) listOf(named) else out.indices.filter { OILY.containsMatchIn(out[it].name) }
            if (targets.isEmpty()) continue
            var saved = 0.0
            out = out.mapIndexed { i, it ->
                if (i !in targets) it
                else {
                    val fat = r1(it.fatG * keep)
                    val kcal = jsRound((it.fatG - fat) * 9)
                    saved += kcal
                    it.copy(fatG = fat, calories = max(0.0, it.calories - kcal))
                }
            }
            val what = if (cue.level == "none") "No oil" else "Less oil"
            val s = FoodBits.num(saved)
            changes.add(if (named >= 0) "$what on ${out[named].name} (−$s kcal)" else "$what on ${targets.size} dish${if (targets.size == 1) "" else "es"} (−$s kcal)")
        }
        return out to changes
    }

    /** Both passes on items that are already priced (the "after the photo" path, and the tests). */
    fun mergeVoice(items: List<PlateItem>, text: String?): VoiceMerge {
        val p = parseVoice(text)
        val a = applyVoiceAmounts(items, p)
        val (oiled, oilChanges) = applyVoiceOil(a.items, p)
        return VoiceMerge(oiled, a.changes + oilChanges, a.added)
    }

    /** Voice-added items the web couldn't price (still 0 kcal) are dropped with a note, never logged at 0. */
    fun dropUnpriced(items: List<PlateItem>): Pair<List<PlateItem>, List<String>> {
        val notes = ArrayList<String>()
        val kept = items.filter {
            if (it.fromVoice && !(it.calories > 0)) {
                notes.add("Couldn't check \"${it.name}\" on the web. Add it by hand.")
                false
            } else true
        }
        return kept to notes
    }
}
