package com.sohum.bandlog.util

import java.time.Instant
import java.time.OffsetDateTime

/**
 * v2.13 Pro tier (spec §1), a 1:1 port of the web's lib/pro.ts.
 *
 * hasPro = plan ∈ {beta, pro} and (pro_until is null or in the future). When the `plan` column
 * isn't there (schema_v36 not applied yet) everyone has Pro. Everyone is on 'beta' for now, so
 * the paywall below exists and is tested but can't trigger.
 */
object Pro {
    const val FREE = "free"
    const val BETA = "beta"
    const val PRO = "pro"

    /** `app_config.pro`; the fallback when the row / table is missing is ₹700 / month. */
    data class Config(
        val priceInr: Int = 700,
        val period: String = "month",
        val betaAllPro: Boolean = true,
        val paymentsEnabled: Boolean = false,
    ) {
        /** "₹700 / month" */
        val priceLabel: String get() = "₹$priceInr / $period"
    }

    val DEFAULT_CONFIG = Config()

    /**
     * [planPresent] false = the profiles row has no `plan` column (v36 not applied) → true.
     * [proUntil] is an ISO timestamptz or null.
     */
    fun hasPro(plan: String?, proUntil: String?, planPresent: Boolean = true, now: Instant = Instant.now()): Boolean {
        if (!planPresent) return true
        val p = plan?.trim()?.lowercase()
        if (p != BETA && p != PRO) return false
        val until = proUntil?.trim()?.takeIf { it.isNotEmpty() } ?: return true
        val t = parseInstant(until) ?: return true
        return t.isAfter(now)
    }

    private fun parseInstant(s: String): Instant? =
        runCatching { OffsetDateTime.parse(s.replace(' ', 'T')).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(s) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s.replace(' ', 'T') + "Z").toInstant() }.getOrNull()

    /** Reads `app_config.value` for key 'pro'; any missing / bad field keeps its fallback. */
    fun parseConfig(json: org.json.JSONObject?): Config {
        if (json == null) return DEFAULT_CONFIG
        return Config(
            priceInr = json.optInt("price_inr", 700).takeIf { it > 0 } ?: 700,
            period = json.optString("period", "month").ifBlank { "month" },
            betaAllPro = json.optBoolean("beta_all_pro", true),
            paymentsEnabled = json.optBoolean("payments_enabled", false),
        )
    }

    /** Pro features (spec §1). Logging, scanning, squads, water, progress basics, measurements and photos stay free. */
    enum class Feature(val label: String) {
        ADAPTIVE_TARGETS("Adaptive weekly targets"),
        WHAT_TO_EAT("What should I eat?"),
        MENU_SCAN("Restaurant menu scan"),
        RECIPES("Recipe builder"),
        MICROS("Micronutrient dashboard"),
        FASTING("Fasting timer"),
        ROUTINES("Routines & planner"),
        PR_CHARTS("PR charts"),
        MUSCLE_MAP("Muscle map"),
        RECAPS("Weekly & monthly recaps"),
        SHARE_CARDS("Share cards"),
        BEFORE_AFTER("Before / after slider"),
    }

    /** One line per benefit on the Pro screen. */
    val BENEFITS: List<Pair<Feature, String>> = listOf(
        Feature.ROUTINES to "Routines, templates and a weekly planner, with a live workout mode and rest timer",
        Feature.PR_CHARTS to "PR charts with estimated 1-rep max for every lift",
        Feature.MUSCLE_MAP to "Muscle map: what you trained this week, set by set",
        Feature.RECAPS to "Weekly and monthly recap stories",
        Feature.SHARE_CARDS to "Story-sized share cards for PRs, streaks and your day",
        Feature.BEFORE_AFTER to "Before / after photo slider",
        Feature.ADAPTIVE_TARGETS to "Adaptive weekly calorie targets from your real trend",
        Feature.WHAT_TO_EAT to "\"What should I eat?\" picks for what's left today",
        Feature.MENU_SCAN to "Restaurant menu scan with a best pick",
        Feature.RECIPES to "Recipe builder with per-serving macros",
        Feature.MICROS to "Micronutrient dashboard",
        Feature.FASTING to "Fasting timer",
    )

    /** Paywall: may this user open [feature]? Free features aren't in [Feature], so they never reach here. */
    fun canUse(@Suppress("UNUSED_PARAMETER") feature: Feature, hasPro: Boolean): Boolean = hasPro

    /** What the Pro screen's button says: payments aren't enabled, so beta testers see a disabled "You're in the beta". */
    fun buttonLabel(plan: String?, hasPro: Boolean, cfg: Config): String = when {
        plan == BETA || (hasPro && !cfg.paymentsEnabled) -> "You're in the beta"
        hasPro -> "You're on Pro"
        !cfg.paymentsEnabled -> "Coming soon"
        else -> "Upgrade · ${cfg.priceLabel}"
    }

    /** The button is only live when payments are on and the user doesn't have Pro. */
    fun buttonEnabled(hasPro: Boolean, cfg: Config): Boolean = cfg.paymentsEnabled && !hasPro
}
