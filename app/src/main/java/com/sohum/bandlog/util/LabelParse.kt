package com.sohum.bandlog.util

/**
 * Deterministic nutrition-table parser + sanity gate — Kotlin port of the web's
 * src/lib/labelParse.ts. Keep the two in sync; see that file's header for why this exists (a
 * front-of-pack-only label scan still produced a full fabricated nutrition table, and a barcode
 * scan cached physically-impossible Open Food Facts numbers unchecked).
 */

/** Per-100g (or per-serving, before it's scaled up) nutrition numbers. Null = not found. */
data class Per100(
    val calories: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val sugarG: Double? = null,
    val addedSugarG: Double? = null,
    val fatG: Double? = null,
    val saturatedFatG: Double? = null,
    val transFatG: Double? = null,
    val fiberG: Double? = null,
    val sodiumMg: Double? = null,
) {
    fun withKey(key: String, value: Double): Per100 = when (key) {
        "calories" -> copy(calories = calories ?: value)
        "protein_g" -> copy(proteinG = proteinG ?: value)
        "carbs_g" -> copy(carbsG = carbsG ?: value)
        "sugar_g" -> copy(sugarG = sugarG ?: value)
        "added_sugar_g" -> copy(addedSugarG = addedSugarG ?: value)
        "fat_g" -> copy(fatG = fatG ?: value)
        "saturated_fat_g" -> copy(saturatedFatG = saturatedFatG ?: value)
        "trans_fat_g" -> copy(transFatG = transFatG ?: value)
        "fiber_g" -> copy(fiberG = fiberG ?: value)
        "sodium_mg" -> copy(sodiumMg = sodiumMg ?: value)
        else -> this
    }

    fun coreOk(): Boolean = calories != null && proteinG != null && carbsG != null && fatG != null

    fun isEmpty(): Boolean = calories == null && proteinG == null && carbsG == null && sugarG == null &&
        addedSugarG == null && fatG == null && saturatedFatG == null && transFatG == null && fiberG == null && sodiumMg == null

    fun rounded(): Per100 = Per100(
        calories?.round2(), proteinG?.round2(), carbsG?.round2(), sugarG?.round2(), addedSugarG?.round2(),
        fatG?.round2(), saturatedFatG?.round2(), transFatG?.round2(), fiberG?.round2(), sodiumMg?.round2(),
    )
}

private fun Double.round2(): Double = Math.round(this * 100.0) / 100.0

data class ParsedLabel(
    val per100g: Per100,
    val perServing: Per100,
    val servingG: Double?,
    val hasTable: Boolean,
    val coreComplete: Boolean,
)

data class SanityResult(val ok: Boolean, val reasons: List<String>)

object LabelParse {
    private val KEY_PATTERNS: List<Pair<String, Regex>> = listOf(
        "trans_fat_g" to Regex("""\btrans[\s-]*fat""", RegexOption.IGNORE_CASE),
        "saturated_fat_g" to Regex("""\b(?:saturated|sat\.?)[\s-]*fat|of\s+which\s+saturates""", RegexOption.IGNORE_CASE),
        "added_sugar_g" to Regex("""\badded\s+sugars?""", RegexOption.IGNORE_CASE),
        "sugar_g" to Regex("""\b(?:total\s+)?sugars?\b|of\s+which\s+sugars""", RegexOption.IGNORE_CASE),
        "fiber_g" to Regex("""\bfib(?:re|er)\b""", RegexOption.IGNORE_CASE),
        "fat_g" to Regex("""\b(?:total\s+)?fat\b""", RegexOption.IGNORE_CASE),
        "carbs_g" to Regex("""\b(?:total\s+)?carbohydrates?\b""", RegexOption.IGNORE_CASE),
        "protein_g" to Regex("""\bprotein\b""", RegexOption.IGNORE_CASE),
        "sodium_mg" to Regex("""\bsodium\b""", RegexOption.IGNORE_CASE),
    )
    private val SALT_RE = Regex("""\bsalt\b""", RegexOption.IGNORE_CASE)
    private val SODIUM_RE = Regex("""\bsodium\b""", RegexOption.IGNORE_CASE)
    private val ENERGY_RE = Regex("""\benerg(?:y|ie)\b|\bcalories\b""", RegexOption.IGNORE_CASE)
    private val KCAL_RE = Regex("""(\d+(?:[.,]\d+)?)\s*k\s*cal\b""", RegexOption.IGNORE_CASE)
    private val KJ_RE = Regex("""(\d+(?:[.,]\d+)?)\s*kj\b""", RegexOption.IGNORE_CASE)
    private val SERVING_HEADER_RE = Regex("""per\s*serv(?:ing)?\b|per\s*pack\b|\(per\s*serving\)""", RegexOption.IGNORE_CASE)
    private val HUNDRED_HEADER_RE = Regex("""per\s*100\s*g|per\s*100g|amount\s+per\s*100""", RegexOption.IGNORE_CASE)
    private val SERVING_SIZE_RE = Regex("""serving\s*size[^0-9]{0,12}(\d+(?:[.,]\d+)?)\s*g\b""", RegexOption.IGNORE_CASE)
    private val BLANK_LINE_RE = Regex("""^[-:\s]*$""")

    private fun num(s: String): Double? {
        val m = Regex("""-?\d+(?:\.\d+)?""").find(s.replace(',', '.')) ?: return null
        return m.value.toDoubleOrNull()
    }

    /** First bare number this line (or one of the next couple of lines) carries. */
    private fun valueNear(lines: List<String>, i: Int, stripped: String): Double? {
        num(stripped)?.let { return it }
        var j = i + 1
        while (j < lines.size && j < i + 3) {
            num(lines[j])?.let { return it }
            if (lines[j].isNotBlank() && !BLANK_LINE_RE.matches(lines[j])) break
            j++
        }
        return null
    }

    private fun parseEnergyKcal(line: String): Double? {
        KCAL_RE.find(line)?.let { return num(it.groupValues[1]) }
        KJ_RE.find(line)?.let { v -> return num(v.groupValues[1])?.let { Math.round((it / 4.184) * 100.0) / 100.0 } }
        return null
    }

    /** Parses a label/OCR transcript into a nutrition table. Mirrors src/lib/labelParse.ts. */
    fun parse(text: String): ParsedLabel {
        var per100g = Per100()
        var perServing = Per100()
        var servingG: Double? = null
        var context = "100g"
        var sawHeader = false
        val matchedKeys = mutableSetOf<String>()

        val rawLines = text.split(Regex("""\r?\n"""))
        val consumed = mutableSetOf<Int>()

        for (i in rawLines.indices) {
            val line = rawLines[i]
            if (line.isBlank()) continue

            SERVING_SIZE_RE.find(line)?.let { m -> num(m.groupValues[1])?.let { if (it > 0) servingG = it } }
            if (HUNDRED_HEADER_RE.containsMatchIn(line)) { context = "100g"; sawHeader = true }
            else if (SERVING_HEADER_RE.containsMatchIn(line)) { context = "serving"; sawHeader = true }

            if (ENERGY_RE.containsMatchIn(line) && i !in consumed) {
                var kcal = parseEnergyKcal(line)
                if (kcal == null) {
                    var j = i + 1
                    while (j < rawLines.size && j < i + 3) {
                        kcal = parseEnergyKcal(rawLines[j])
                        if (kcal != null) break
                        if (rawLines[j].isNotBlank() && !BLANK_LINE_RE.matches(rawLines[j])) break
                        j++
                    }
                }
                if (kcal != null) {
                    if (context == "100g") per100g = per100g.withKey("calories", kcal!!) else perServing = perServing.withKey("calories", kcal!!)
                    matchedKeys.add("calories")
                    consumed.add(i)
                    continue
                }
            }

            if (SALT_RE.containsMatchIn(line) && !SODIUM_RE.containsMatchIn(line)) {
                val stripped = line.replace(HUNDRED_HEADER_RE, "").replace(SALT_RE, "")
                val g = valueNear(rawLines, i, stripped)
                if (g != null) {
                    val sodiumMg = Math.round(g * 400 * 100.0) / 100.0
                    if (context == "100g") { if (per100g.sodiumMg == null) per100g = per100g.withKey("sodium_mg", sodiumMg) }
                    else { if (perServing.sodiumMg == null) perServing = perServing.withKey("sodium_mg", sodiumMg) }
                    matchedKeys.add("sodium_mg")
                    continue
                }
            }

            for ((key, re) in KEY_PATTERNS) {
                if (!re.containsMatchIn(line)) continue
                val stripped = line.replace(HUNDRED_HEADER_RE, "").replace(re, "")
                val v = valueNear(rawLines, i, stripped) ?: continue
                if (context == "100g") per100g = per100g.withKey(key, v) else perServing = perServing.withKey(key, v)
                matchedKeys.add(key)
                break
            }
        }

        // A real nutrition table either says so ("per 100 g" / "per serving") near at least one
        // matched row, or has enough distinct rows to not be a stray "Protein" mention in
        // front-of-pack marketing copy ("...21g Non GMO Protein...", the seeds-label bug).
        val hasTable = (sawHeader && matchedKeys.isNotEmpty()) || matchedKeys.size >= 3

        if (hasTable) {
            val sg = servingG
            if (sg != null && sg > 0 && !per100g.coreOk() && perServing.coreOk()) {
                per100g = derivePer100FromServing(perServing, sg)
            }
        } else {
            per100g = Per100()
            perServing = Per100()
        }

        return ParsedLabel(per100g, perServing, servingG, hasTable, hasTable && per100g.coreOk())
    }

    /** Scales every field of a per-serving table up to per-100g using the printed serving size. */
    fun derivePer100FromServing(perServing: Per100, servingG: Double): Per100 {
        if (servingG <= 0) return Per100()
        val k = 100.0 / servingG
        fun sc(v: Double?) = v?.let { Math.round(it * k * 100.0) / 100.0 }
        return Per100(
            sc(perServing.calories), sc(perServing.proteinG), sc(perServing.carbsG), sc(perServing.sugarG),
            sc(perServing.addedSugarG), sc(perServing.fatG), sc(perServing.saturatedFatG), sc(perServing.transFatG),
            sc(perServing.fiberG), sc(perServing.sodiumMg),
        )
    }

    /**
     * The sanity gate every per_100g goes through before it's trusted, whatever produced it (OCR
     * parser or Open Food Facts). Catches physically-impossible numbers like the True Elements
     * Muesli barcode bug (1037 kcal / 100 g from mis-scaled OFF per-serving data).
     */
    fun sanityCheck(p: Per100): SanityResult {
        val reasons = mutableListOf<String>()
        val calories = p.calories
        val protein = p.proteinG
        val carbs = p.carbsG
        val fat = p.fatG
        if (calories == null || protein == null || carbs == null || fat == null) {
            return SanityResult(false, listOf("missing a core field (calories/protein/carbs/fat)"))
        }
        if (calories < 0 || calories > 905) reasons.add("calories $calories out of 0-905")
        for ((name, v) in listOf("protein" to protein, "carbs" to carbs, "fat" to fat, "sugar" to p.sugarG, "saturated fat" to p.saturatedFatG)) {
            if (v != null && v < 0) reasons.add("$name is negative ($v)")
        }
        val macroSum = protein + carbs + fat
        if (macroSum > 102) reasons.add("protein+carbs+fat = ${Math.round(macroSum * 10) / 10.0} g, over 100 g")
        val atwater = 4 * protein + 4 * carbs + 9 * fat
        val allowed = calories * 0.25 + 15
        if (Math.abs(atwater - calories) > allowed) reasons.add("Atwater estimate ${Math.round(atwater)} kcal vs stated $calories kcal (outside +/-${Math.round(allowed)})")
        val sugar = p.sugarG
        if (sugar != null && sugar > carbs + 0.5) reasons.add("sugar $sugar g exceeds carbs $carbs g")
        val saturated = p.saturatedFatG
        if (saturated != null && saturated > fat + 0.5) reasons.add("saturated fat $saturated g exceeds fat $fat g")
        return SanityResult(reasons.isEmpty(), reasons)
    }

    // ---- barcode digits recovered from a label transcript ----

    private fun eanChecksumValid(digits: String): Boolean {
        if (!Regex("""^\d{8}$|^\d{13}$""").matches(digits)) return false
        val nums = digits.map { it - '0' }.toMutableList()
        val check = nums.removeAt(nums.size - 1)
        var sum = 0
        for (i in nums.indices) {
            val posFromRight = nums.size - i
            sum += nums[i] * (if (posFromRight % 2 == 1) 3 else 1)
        }
        val computed = (10 - (sum % 10)) % 10
        return computed == check
    }

    /**
     * Best-effort EAN-13/EAN-8/UPC-A recovery from OCR'd label text: tolerates spaces/dashes and
     * the usual OCR confusions (i/I/l -> 1, O/o -> 0), and only returns a code whose check digit
     * is valid.
     */
    fun extractBarcode(text: String): String? {
        val runs = Regex("""[0-9oOiIl][0-9oOiIl \-]{6,18}[0-9oOiIl]""").findAll(text).map { it.value }
        for (run in runs) {
            val cleaned = run.replace(Regex("[oO]"), "0").replace(Regex("[iIl]"), "1").replace(Regex("[^0-9]"), "")
            for (len in listOf(13, 12, 8)) {
                var start = 0
                while (start + len <= cleaned.length) {
                    val candidate = cleaned.substring(start, start + len)
                    if (len == 12) {
                        val asEan13 = "0$candidate"
                        if (eanChecksumValid(asEan13)) return asEan13
                    } else if (eanChecksumValid(candidate)) {
                        return candidate
                    }
                    start++
                }
            }
        }
        return null
    }
}
