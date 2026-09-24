package com.sohum.bandlog.util

/** Display-name rules shared with the web app and the signup trigger. */
object Names {
    /**
     * The first run of letters in the email's local part, title-cased: `ayaan.khan@x` → "Ayaan",
     * `7sohum_99@x` → "Sohum". Blank when there are no letters at all.
     */
    fun nameFromEmail(email: String?): String {
        val local = email.orEmpty().substringBefore('@')
        val run = Regex("[A-Za-z]+").find(local)?.value.orEmpty()
        return run.lowercase().replaceFirstChar { it.uppercase() }
    }

    /** [name] when set, else the email-derived name, else [fallback]. */
    fun display(name: String?, email: String?, fallback: String = "You"): String =
        name?.trim()?.ifBlank { null } ?: nameFromEmail(email).ifBlank { fallback }

    /** One or two initials for an avatar circle. */
    fun initials(name: String): String {
        val parts = name.trim().split(Regex("[ \\t\\n]+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }
}
