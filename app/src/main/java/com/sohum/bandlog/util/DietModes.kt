package com.sohum.bandlog.util

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * v2.13 §4 diet modes (`profiles.diet_mode`, schema_v36). Pure Kotlin, no Android — the twin of
 * the web's src/lib/dietModes.ts; the numbers must match (see DietModesTest).
 *
 * Protein is g per kg of the same body weight goals.ts uses (the profile's weight). Carbs and fat
 * are a share of the calories left after protein. Calories never change when the mode changes.
 * Food filters only shape suggestions (what-to-eat, quick picks); they never block logging.
 *
 * Under 18 (science-spec, Goals.isTeen): only balanced, high_protein, vegetarian, eggetarian, vegan
 * and jain can be picked, and every mode's protein is capped at 1.6 g/kg (never below the ICMR-NIN
 * adolescent amount the balanced plan gives).
 */
object DietModes {

    data class Mode(
        val key: String,
        val label: String,
        /** One line under the name in the picker. */
        val blurb: String,
        /** "The science" sheet: the 1–2 line rationale. */
        val science: String,
        /** Where the rationale comes from. */
        val source: String,
        /** Keto / low-carb / Mediterranean: adults only. */
        val adultsOnly: Boolean = false,
        /** Shown before switching (keto). */
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

    const val TEEN_NOT_RECOMMENDED = "Not recommended under 18"

    val ALL: List<Mode> = listOf(
        Mode(
            BALANCED, "Balanced", "Your current plan: protein for how you train, fat 25%, carbs the rest",
            "Protein follows how often you train (1.6 g/kg at 3+ sessions a week, else about 1 g/kg), fat is 25% of calories and carbs fill the rest.",
            "ICMR-NIN 2020 RDA; ISSN position stand 2017",
        ),
        Mode(
            HIGH_PROTEIN, "High protein", "2.0 g/kg protein, fat 25%, carbs the rest",
            "Active people building or keeping muscle do well on 1.4–2.0 g of protein per kg a day. This mode sits at the top of that range (1.6 g/kg under 18).",
            "ISSN position stand: protein and exercise (Jäger et al., 2017)",
        ),
        Mode(
            VEGETARIAN, "Vegetarian", "No meat, fish or egg · 1.6 g/kg protein",
            "Lacto-vegetarian: dairy stays in. Dal, paneer, curd, soy and milk can cover 1.6 g/kg when you mix sources through the day.",
            "Academy of Nutrition and Dietetics position on vegetarian diets (2016)",
        ),
        Mode(
            EGGETARIAN, "Eggetarian", "No meat or fish · 1.6 g/kg protein",
            "Vegetarian plus eggs. Eggs are one of the easiest complete proteins, which makes 1.6 g/kg simpler to reach.",
            "Academy of Nutrition and Dietetics position on vegetarian diets (2016)",
        ),
        Mode(
            VEGAN, "Vegan", "No animal foods, dairy, ghee or honey · 1.8 g/kg protein",
            "Plant proteins are a little less digestible, so the target is 10–15% higher. Soy, dal, chana and peanuts do the heavy lifting.",
            "Academy of Nutrition and Dietetics position on vegetarian diets (2016)",
        ),
        Mode(
            JAIN, "Jain", "Vegetarian, no roots, onion, garlic or honey · 1.6 g/kg",
            "Vegetarian without onion, garlic, potato, carrot, beetroot, radish, ginger or other roots and tubers, and no honey. Dal, paneer and curd carry the protein.",
            "Academy of Nutrition and Dietetics position on vegetarian diets (2016)",
        ),
        Mode(
            KETO, "Keto", "Carbs 50 g a day at most, fat fills the rest · 1.6 g/kg protein",
            "Very low carb pushes the body to run mostly on fat. It works for some adults, but it's hard to keep up and isn't right for everyone.",
            "Adults only. Talk to a doctor first if you're pregnant, have type 1 diabetes, or take diabetes or blood-pressure medicines.",
            adultsOnly = true,
            warning = "Not for pregnancy, type 1 diabetes, or anyone on diabetes or blood-pressure medicines without a doctor's OK.",
        ),
        Mode(
            LOW_CARB, "Low carb", "Carbs 26% of calories (130 g at most) · 1.8 g/kg protein",
            "Low carb means under 26% of calories from carbs (about 130 g a day or less), with protein kept high to protect muscle.",
            "Feinman et al., Nutrition 2015 (definition of low-carbohydrate diets). Adults only.",
            adultsOnly = true,
        ),
        Mode(
            MEDITERRANEAN, "Mediterranean", "Fat 35% from oils, nuts and fish · at least 1.2 g/kg protein",
            "More healthy fats from oils, nuts, seeds and fish, lots of vegetables and pulses. Linked with better heart health.",
            "PREDIMED trial (Estruch et al., NEJM 2018). Adults only.",
            adultsOnly = true,
        ),
    )

    fun byKey(key: String?): Mode = ALL.firstOrNull { it.key == key } ?: ALL.first()
    fun isMode(key: String?): Boolean = ALL.any { it.key == key }

    /** Whether [key] can be picked at [age] (null age = adult rules; the app asks for DOB in onboarding). */
    fun allowed(key: String, age: Int?): Boolean = !(Goals.isTeen(age) && byKey(key).adultsOnly)

    /** The mode the maths actually uses: an adult-only mode saved on an under-18 account counts as balanced. */
    fun effective(key: String?, age: Int?): String {
        val k = if (isMode(key)) key!! else BALANCED
        return if (allowed(k, age)) k else BALANCED
    }

    /** g/kg of each mode for adults (null = the balanced rule from Goals.proteinTargetG). */
    fun proteinPerKg(key: String, teen: Boolean): Double? = when (key) {
        HIGH_PROTEIN -> if (teen) 1.6 else 2.0
        VEGETARIAN, EGGETARIAN, JAIN, KETO -> 1.6
        VEGAN, LOW_CARB -> 1.8
        MEDITERRANEAN -> 1.2 // "at least": see [proteinG]
        else -> null
    }

    /** Teen cap on every mode's per-kg protein. */
    const val TEEN_MAX_G_PER_KG = 1.6

    /**
     * Protein g/day for [key]. Balanced = Goals.proteinTargetG. Mediterranean = max(1.2 g/kg, the
     * balanced amount). Under 18: min(mode g/kg, 1.6) × kg, never under the ICMR-NIN table.
     */
    fun proteinG(key: String, age: Int, kg: Double, sex: String?, workoutsPerWeek: Int): Int {
        val teen = Goals.isTeen(age)
        val mode = effective(key, age)
        val balanced = Goals.proteinTargetG(age, kg, sex, workoutsPerWeek)
        val perKg = proteinPerKg(mode, teen) ?: return balanced
        if (teen) return max(balanced, (min(perKg, TEEN_MAX_G_PER_KG) * kg).roundToInt())
        if (mode == MEDITERRANEAN) return max(balanced, (perKg * kg).roundToInt())
        return (perKg * kg).roundToInt()
    }

    /** Keto's carb ceiling. */
    const val KETO_CARBS_G = 50
    /** Low-carb: 26 % of calories, never over 130 g. */
    const val LOW_CARB_PCT = 0.26
    const val LOW_CARB_MAX_G = 130

    /**
     * Carbs and fat for [calories] and [protein] under [key]:
     * balanced / high protein / vegetarian / eggetarian / vegan / jain: fat 25 %, carbs the rest (Goals.macrosFor);
     * keto: carbs ≤ 50 g, fat the rest; low carb: carbs 26 % (≤ 130 g), fat the rest; Mediterranean: fat 35 %, carbs the rest.
     */
    fun macros(key: String, calories: Double, protein: Int): Goals.Targets {
        val kcal = calories.roundToInt()
        val left = max(0.0, calories - protein * 4)
        return when (key) {
            KETO -> {
                val carbs = min(KETO_CARBS_G, (left / 4).toInt())
                val fat = ((calories - protein * 4 - carbs * 4) / 9).roundToInt().coerceAtLeast(0)
                Goals.Targets(kcal, protein, carbs, fat)
            }
            LOW_CARB -> {
                val carbs = min(min(LOW_CARB_MAX_G, (calories * LOW_CARB_PCT / 4).roundToInt()), (left / 4).toInt())
                val fat = ((calories - protein * 4 - carbs * 4) / 9).roundToInt().coerceAtLeast(0)
                Goals.Targets(kcal, protein, carbs, fat)
            }
            MEDITERRANEAN -> {
                val fat = (calories * 0.35 / 9).roundToInt()
                val carbs = ((calories - protein * 4 - fat * 9) / 4).roundToInt().coerceAtLeast(0)
                Goals.Targets(kcal, protein, carbs, fat)
            }
            else -> Goals.macrosFor(calories, protein)
        }
    }

    /**
     * New macro targets for [key] at the SAME [calories]. Needs weight + age (else null: the screen
     * asks for Personal details, like Auto generate).
     */
    fun targets(key: String, calories: Int, age: Int?, kg: Double?, sex: String?, workoutsPerWeek: Int): Goals.Targets? {
        if (age == null || kg == null || kg <= 0) return null
        val mode = effective(key, age)
        val protein = proteinG(mode, age, kg, sex, workoutsPerWeek)
        return macros(mode, calories.toDouble(), protein)
    }

    // ---------------------------------------------------------------- food filters

    private val MEAT = listOf(
        "chicken", "mutton", "lamb", "goat", "beef", "pork", "bacon", "ham", "sausage", "salami", "pepperoni", "turkey", "duck", "keema", "kheema",
        "murg", "murgh", "gosht", "boti", "kebab", "kabab", "tikka", "tandoori chicken", "biryani chicken", "chicken biryani", "mutton biryani", "meat", "liver", "nihari", "rogan josh",
        "haleem", "shawarma",
    )
    private val FISH = listOf("fish", "prawn", "shrimp", "crab", "lobster", "tuna", "salmon", "sardine", "mackerel", "rohu", "pomfret", "surmai", "bangda", "hilsa", "machli", "macchi", "squid", "seafood", "anchovy", "cod")
    private val EGG = listOf("egg", "omelette", "omelet", "anda", "bhurji egg", "egg bhurji", "mayonnaise", "mayo")
    private val DAIRY = listOf(
        "milk", "curd", "dahi", "paneer", "cheese", "ghee", "butter", "yogurt", "yoghurt", "lassi", "chaas", "buttermilk", "raita", "cream", "malai", "khoa", "khoya",
        "whey", "kheer", "rabdi", "rasgulla", "rasmalai", "gulab jamun", "shrikhand", "ice cream", "kulfi", "milkshake", "tea with milk", "chai", "coffee with milk", "latte", "cappuccino",
        "mawa", "peda", "barfi", "burfi", "casein",
    )
    private val HONEY = listOf("honey", "shahad")
    private val ROOTS = listOf(
        "onion", "pyaz", "pyaaz", "garlic", "lahsun", "lehsun", "potato", "aloo", "alu ", "carrot", "gajar", "beetroot", "beet", "radish", "mooli", "ginger", "adrak",
        "sweet potato", "shakarkandi", "yam", "suran", "jimikand", "arbi", "taro", "turnip", "shalgam", "tapioca", "sabudana", "cassava", "fries", "chips", "samosa", "vada pav", "aloo paratha",
        "pav bhaji", "dosa masala", "masala dosa",
    )

    private fun hasAny(text: String, words: List<String>): Boolean {
        val t = " " + text.lowercase().replace(Regex("[^a-z ]"), " ") + " "
        return words.any { w ->
            val k = w.trim()
            // Whole word (or phrase) match, plural "s" allowed: "eggs", "onions".
            Regex("(^|\\s)" + Regex.escape(k) + "s?(\\s|$)").containsMatchIn(t)
        }
    }

    /** Plant sources that read like a banned word but aren't ("eggplant", "egg-free", "soy milk"). */
    private val PLANT_EXCEPTIONS = listOf("eggplant", "egg free", "eggless", "soy milk", "soya milk", "almond milk", "oat milk", "coconut milk", "peanut butter", "vegan", "tofu", "soya chunk")

    /** What a mode rules out, in words (for the picker and the what-to-eat footnote). */
    fun excludes(key: String): String? = when (key) {
        VEGETARIAN -> "meat, fish and egg"
        EGGETARIAN -> "meat and fish"
        VEGAN -> "meat, fish, egg, dairy, ghee, paneer, curd and honey"
        JAIN -> "meat, fish, egg, onion, garlic, roots and tubers, and honey"
        else -> null
    }

    /**
     * Whether a food fits [key]'s filter, judged from its name (and preset [category] when known).
     * Only for suggestions; logging is never blocked. Unknown foods pass.
     */
    fun allows(key: String, name: String, category: String? = null): Boolean {
        val n = name.lowercase()
        val plantish = PLANT_EXCEPTIONS.any { n.contains(it) }
        val meat = hasAny(n, MEAT)
        val fish = hasAny(n, FISH)
        val egg = !n.contains("eggplant") && !n.contains("eggless") && !n.contains("egg free") && hasAny(n, EGG)
        val dairy = !plantish && hasAny(n, DAIRY)
        val honey = hasAny(n, HONEY)
        val roots = hasAny(n, ROOTS)
        return when (key) {
            VEGETARIAN -> !meat && !fish && !egg
            EGGETARIAN -> !meat && !fish
            VEGAN -> !meat && !fish && !egg && !dairy && !honey
            JAIN -> !meat && !fish && !egg && !honey && !roots
            else -> true
        }
    }
}
