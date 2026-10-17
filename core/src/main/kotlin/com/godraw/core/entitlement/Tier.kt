package com.godraw.core.entitlement

/** Membership tiers, ordered from least to most capable. */
enum class Tier {
    FREE,
    PRO,
    MAX;

    fun atLeast(other: Tier): Boolean = ordinal >= other.ordinal
}
