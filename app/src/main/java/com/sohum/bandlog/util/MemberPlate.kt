package com.sohum.bandlog.util

/**
 * v2.17 member plate (schema_v41: profiles.member_no, profiles.is_founder).
 *
 * In order: is_founder → the gold "FOUNDER" plate; member_no 1..50 → "OG #07" (two digits, gunmetal);
 * otherwise no plate. Before v41 is applied both columns are missing (null here) → no plate at all.
 */
sealed class MemberPlate {
    abstract val label: String

    data object Founder : MemberPlate() { override val label = "FOUNDER" }

    data class Og(val memberNo: Int) : MemberPlate() {
        override val label: String get() = "OG #" + memberNo.toString().padStart(2, '0')
    }

    companion object {
        const val OG_LIMIT = 50

        fun of(isFounder: Boolean?, memberNo: Int?): MemberPlate? = when {
            isFounder == true -> Founder
            memberNo != null && memberNo in 1..OG_LIMIT -> Og(memberNo)
            else -> null
        }
    }
}
