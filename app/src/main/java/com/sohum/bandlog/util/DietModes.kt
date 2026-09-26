package com.sohum.bandlog.util

import com.sohum.bandlog.data.Profile
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * v2.13 §4 diet modes (`profiles.diet_mode`, schema_v36). Pure Kotlin — a line-for-line port of the
 * web's src/lib/dietModes.ts (same numbers, same words, same food filters; see NutritionMathTest).
 *
 * Protein is g per kg of the same body weight Goals uses (the current weight). Carbs and fat are
 * worked out from the calories left after protein: most modes keep the app's default (fat 25 % of
 * calories, carbs the rest); keto caps carbs at 50 g, low-carb at 26 % of calories (never over 130 g),
 * Mediterranean sets fat to 35 %. Switching a mode never changes calories.
 *
 * Food filters only shape suggestions (what-to-eat, quick picks, menu best pick). They never block logging.
 */
object DietModes {

    data class Mode(
        val key: String,
        val label: String,
        /** One line under the name in the picker. */
        val short: String,
        /** Can't be picked under 18. */
        val adultsOnly: Boolean,
        /** "The science": the 1–2 line rationale and its source. */
        val science: String,
        val source: String,
        /** Shown in the confirm step (keto only today). */
        val warning: String? = null,
    )

    const val BALANCED = "balanced"
    const val HIGH_PROTEIN = "high_protein"
    const val VEGETARIAN = "vegetarian"
    const val EGGETARIAN = "eggetarian"
    const val VEGAN = "vegan"
    const val JAIN = "jain"
    const val KETO = "keto"
    const val LOW_CARB = "low_carb"
    const val MEDITERRANEAN = "mediterranean"

    val ALL: List<Mode> = listOf(
        Mode(BALANCED, "Balanced", "The app's default split", false,
            "Protein from your training and age, fat about a quarter of your calories, carbs the rest. The everyday pattern Indian dietary guidelines recommend.",
            "ICMR-NIN Dietary Guidelines for Indians and RDA (2020)"),
        Mode(HIGH_PROTEIN, "High protein", "2 g per kg, for lifting and cutting", false,
            "More protein helps you keep and build muscle when you train hard or eat a little less. 2 g/kg is the top of the range for active adults; under 18 it stays at 1.6 g/kg.",
            "ISSN position stand: protein and exercise (Jäger et al., 2017): 1.4–2.0 g/kg/day"),
        Mode(VEGETARIAN, "Vegetarian", "No meat, fish or egg", false,
            "Dal, paneer, curd, soya and milk cover protein well when you eat a mix of them through the day. 1.6 g/kg keeps it on target.",
            "ICMR-NIN 2020; Academy of Nutrition and Dietetics position on vegetarian diets (2016)"),
        Mode(EGGETARIAN, "Eggetarian", "Vegetarian plus eggs", false,
            "Eggs add a cheap, complete protein to a vegetarian plate. 1.6 g/kg keeps protein on target.",
            "ICMR-NIN 2020"),
        Mode(VEGAN, "Vegan", "No animal foods, dairy, ghee or honey", false,
            "Plant proteins digest a little less completely, so the target is 10–15 % higher: 1.8 g/kg. Mix dals, soya, tofu, nuts and grains.",
            "Academy of Nutrition and Dietetics position on vegetarian diets (Melina et al., 2016)"),
        Mode(JAIN, "Jain", "Vegetarian, no roots or tubers", false,
            "No onion, garlic, potato, carrot, beetroot, radish, ginger or other roots, and no honey. Dals, paneer, curd and grains carry the protein at 1.6 g/kg.",
            "ICMR-NIN 2020"),
        Mode(KETO, "Keto", "Carbs under 50 g a day. Adults only", true,
            "Very low carb (under 50 g a day), with fat making up the rest of your calories and protein at 1.6 g/kg. It works for some people, but it's hard to keep up.",
            "Low-carbohydrate diet definitions (Feinman et al., Nutrition 2015)",
            warning = "Not for pregnancy, type 1 diabetes, or anyone on diabetes or blood-pressure medicines without a doctor's OK."),
        Mode(LOW_CARB, "Low carb", "Carbs about a quarter of calories. Adults only", true,
            "Carbs at 26 % of calories (never over 130 g), fat the rest, protein 1.8 g/kg. The standard definition of a low-carb diet.",
            "Feinman et al., Nutrition 2015"),
        Mode(MEDITERRANEAN, "Mediterranean", "More healthy fats, fish and nuts", false,
            "About 35 % of calories from fat, mostly oils, nuts and fish, with plenty of vegetables and legumes. Protein at least 1.2 g/kg.",
            "PREDIMED trial (Estruch et al., NEJM 2018)"),
    )

    /** Under 18 only these can be picked (the spec's list). */
    val TEEN_MODES = listOf(BALANCED, HIGH_PROTEIN, VEGETARIAN, EGGETARIAN, VEGAN, JAIN)
    const val NOT_FOR_TEENS = "Not recommended under 18"

    fun isMode(key: String?): Boolean = ALL.any { it.key == key }
    fun byKey(key: String?): Mode = ALL.firstOrNull { it.key == key } ?: ALL.first()

    /** Whether [mode] can be picked at [age] (null age = unknown, treated as an adult like Goals). */
    fun allowed(mode: String, age: Int?): Boolean = !Goals.isTeen(age) || mode in TEEN_MODES

    /** The mode the maths uses: an under-18 account holding an adults-only mode counts as balanced. */
    fun effective(mode: String?, age: Int?): String {
        val m = if (isMode(mode)) mode!! else BALANCED
        return if (allowed(m, age)) m else BALANCED
    }

    /** g/kg per mode; null = balanced (Goals decides). */
    fun proteinPerKg(mode: String, teen: Boolean): Double? = when (mode) {
        BALANCED -> null
        HIGH_PROTEIN -> if (teen) 1.6 else 2.0
        VEGAN, LOW_CARB -> 1.8
        MEDITERRANEAN -> 1.2
        else -> 1.6
    }

    const val KETO_CARBS_G = 50
    const val LOW_CARB_PCT = 0.26
    const val LOW_CARB_MAX_G = 130

    private fun jsRound(v: Double): Int = floor(v + 0.5).toInt()

    /**
     * Macro targets for [calories] in [mode]. Calories are never changed. Protein: the mode's g/kg of
     * the current weight (Mediterranean: at least 1.2 g/kg, never under the balanced amount); balanced
     * uses Goals. Without weight or age the current protein target is kept.
     */
    fun targets(p: Profile, calories: Double, mode: String, today: String = Goals.todayIso()): Goals.Targets {
        val age = Goals.ageYears(p.dob, today)
        val m = effective(mode, age)
        val teen = Goals.isTeen(age)
        val kg = p.weightKg?.takeIf { it > 0 }
        val kcal = max(0, jsRound(calories))
        val balanced = if (kg != null && age != null) Goals.proteinTargetG(age, kg, p.gender, p.weeklyWorkoutTarget) else p.proteinTargetG
        val perKg = proteinPerKg(m, teen)
        var protein = if (perKg == null || kg == null) balanced else jsRound(perKg * kg)
        if (m == MEDITERRANEAN && kg != null) protein = max(protein, balanced)
        // Protein can never take more than the whole budget.
        protein = max(0, min(protein, floor(kcal / 4.0).toInt()))
        val left = kcal - protein * 4
        if (m == KETO || m == LOW_CARB) {
            val cap = if (m == KETO) KETO_CARBS_G else min(LOW_CARB_MAX_G, jsRound(kcal * LOW_CARB_PCT / 4))
            val carbs = max(0, min(cap, floor(left / 4.0).toInt()))
            val fat = max(0, jsRound((left - carbs * 4) / 9.0))
            return Goals.Targets(kcal, protein, carbs, fat)
        }
        if (m == MEDITERRANEAN) {
            val fat = min(jsRound(kcal * 0.35 / 9), floor(left / 9.0).toInt())
            val carbs = max(0, jsRound((left - fat * 9) / 4.0))
            return Goals.Targets(kcal, protein, carbs, fat)
        }
        // balanced, high_protein and the food-pattern modes: fat 25 %, carbs the rest (Goals.macrosFor).
        return Goals.macrosFor(kcal.toDouble(), protein)
    }

    /** What a mode leaves out, in words (for the what-to-eat footnote). */
    fun excludes(mode: String): String? = when (mode) {
        VEGETARIAN -> "meat, fish and egg"
        EGGETARIAN -> "meat and fish"
        VEGAN -> "meat, fish, egg, dairy, ghee and honey"
        JAIN -> "meat, fish, egg, onion, garlic, roots and honey"
        else -> null
    }

    // ---------------------------------------------------------------- food filters

    /** Word lists matched against food names (lower-case, whole words or word starts). Same as the web. */
    private val MEAT = listOf("chicken", "mutton", "lamb", "goat", "beef", "pork", "keema", "kheema", "bacon", "ham", "sausage", "salami", "pepperoni", "turkey", "duck", "meat", "murgh", "gosht", "tangdi", "tandoori chicken", "nihari", "haleem", "shawarma", "liver", "kaleji", "seekh", "galouti", "boti", "kebab")
    private val FISH = listOf("fish", "prawn", "prawns", "shrimp", "crab", "lobster", "tuna", "salmon", "rohu", "pomfret", "surmai", "bangda", "mackerel", "sardine", "hilsa", "ilish", "anchovy", "squid", "calamari", "seafood", "machli", "machhi", "jhinga", "katla", "basa", "tilapia", "mussel", "oyster", "clam")
    private val EGG = listOf("egg", "eggs", "omelette", "omelet", "anda", "ande", "frittata", "mayonnaise", "mayo", "eggnog", "shakshuka")
    private val DAIRY = listOf("milk", "paneer", "curd", "dahi", "yogurt", "yoghurt", "ghee", "butter", "cheese", "cream", "khoya", "khoa", "mawa", "lassi", "raita", "kheer", "chaas", "buttermilk", "whey", "ice cream", "rabri", "rabdi", "rasgulla", "rasmalai", "gulab jamun", "kulfi", "shrikhand", "malai", "milkshake", "shake", "chai", "latte", "cappuccino", "kalakand", "sandesh", "peda", "burfi", "barfi", "halwa", "payasam", "basundi", "makhani", "tikka masala", "korma", "dudh", "doodh", "chhena", "chena", "custard", "pudding")
    /** "peanut butter", "coconut milk" … aren't dairy. */
    private val NOT_DAIRY = listOf("peanut butter", "almond butter", "nut butter", "cocoa butter", "coconut milk", "almond milk", "soy milk", "soya milk", "oat milk", "rice milk", "vegan", "coconut cream", "cashew milk")
    private val HONEY = listOf("honey", "shahad")
    private val JAIN_ROOTS = listOf("onion", "pyaz", "pyaaz", "kanda", "garlic", "lahsun", "lehsun", "lasun", "potato", "potatoes", "aloo", "alu", "batata", "carrot", "carrots", "gajar", "beetroot", "beet", "radish", "mooli", "ginger", "adrak", "sweet potato", "shakarkandi", "yam", "suran", "jimikand", "arbi", "colocasia", "turnip", "shalgam", "tuber", "samosa", "vada pav", "pav bhaji", "masala dosa", "french fries", "fries", "potato chips", "tikki", "hash brown", "leek", "spring onion", "scallion")

    private val wordRx = HashMap<String, Regex>()
    private fun hasWord(name: String, words: List<String>): Boolean = words.any { w ->
        wordRx.getOrPut(w) { Regex("(^|[^a-z])" + Regex.escape(w) + "(s|es)?([^a-z]|$)") }.containsMatchIn(name)
    }

    private data class Excluded(val meat: Boolean, val fish: Boolean, val egg: Boolean, val dairy: Boolean, val honey: Boolean, val roots: Boolean)

    private fun excluded(mode: String): Excluded {
        val veg = mode == VEGETARIAN || mode == JAIN
        return Excluded(
            meat = veg || mode == EGGETARIAN || mode == VEGAN,
            fish = veg || mode == EGGETARIAN || mode == VEGAN,
            egg = veg || mode == VEGAN,
            dairy = mode == VEGAN,
            honey = mode == VEGAN || mode == JAIN,
            roots = mode == JAIN,
        )
    }

    private val SAYS_VEG = Regex("\\b(veg|veggie|vegetable|vegetarian|paneer|soya|tofu|mushroom|dal|hara bhara|chana|rajma|corn|palak)\\b")
    private val NON_VEG = Regex("\\bnon veg\\b")
    private val NAMED_MEAT = Regex("\\b(chicken|mutton|lamb|beef|pork|keema|fish|prawn)\\b")
    private val EGGLESS = Regex("\\beggless\\b")

    /** Why [name] doesn't fit [mode] ("meat", "egg", …); empty when it fits. Name matching only. */
    fun conflicts(mode: String, name: String): List<String> {
        val n = " " + name.lowercase().replace(Regex("[^a-z\\s]"), " ").replace(Regex("\\s+"), " ").trim() + " "
        val x = excluded(mode)
        val out = mutableListOf<String>()
        // "Veg biryani" / "veg momos" name themselves; a bare "veg" wins over the dish's usual meat.
        val saysVeg = SAYS_VEG.containsMatchIn(n) && !NON_VEG.containsMatchIn(n)
        if (x.meat && hasWord(n, MEAT) && !(saysVeg && !NAMED_MEAT.containsMatchIn(n))) out += "meat"
        if (x.fish && hasWord(n, FISH)) out += "fish"
        if (x.egg && hasWord(n, EGG) && !EGGLESS.containsMatchIn(n)) out += "egg"
        if (x.dairy && hasWord(n, DAIRY) && NOT_DAIRY.none { n.contains(it) }) out += "dairy"
        if (x.honey && hasWord(n, HONEY)) out += "honey"
        if (x.roots && hasWord(n, JAIN_ROOTS)) out += "roots"
        return out
    }

    fun allows(mode: String, name: String): Boolean = conflicts(mode, name).isEmpty()
}
