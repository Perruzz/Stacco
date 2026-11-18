package io.github.perruzz.stacco.domain

import java.time.LocalDate
import kotlin.random.Random

/**
 * When the "buy me a coffee" card may appear on Today: never in the first two weeks after the
 * install, then at most once every 21–30 days (a new random gap each time), never again once the
 * user says so.
 */
object SupportRules {

    const val GRACE_DAYS = 14L
    val GAP_DAYS = 21..30

    fun isDue(prompt: SupportPrompt, installed: LocalDate, today: LocalDate): Boolean {
        if (prompt.dismissed) return false
        if (today.isBefore(installed.plusDays(GRACE_DAYS))) return false
        val next = prompt.nextAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return true
        return !today.isBefore(next)
    }

    /** The prompt once shown or postponed on [today]: it may come back in 21–30 days. */
    fun postponed(prompt: SupportPrompt, today: LocalDate, random: Random = Random.Default): SupportPrompt =
        prompt.copy(nextAt = today.plusDays(random.nextInt(GAP_DAYS.first, GAP_DAYS.last + 1).toLong()).toString())
}
