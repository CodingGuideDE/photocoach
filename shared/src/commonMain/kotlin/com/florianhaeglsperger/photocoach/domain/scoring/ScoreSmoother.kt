package com.florianhaeglsperger.photocoach.domain.scoring

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Glaettet den Live-Score, damit die Zahl im Sucher nicht bei jedem Frame springt.
 *
 * Exponentieller gleitender Mittelwert mit Zeitkonstante [timeConstantMs] — ueber die
 * echten Frame-Abstaende gerechnet, nicht pro Frame. So verhaelt er sich gleich, egal ob die
 * Analyse gerade mit 10 Hz laeuft oder das Geraet unter Last nur 5 Hz schafft.
 *
 * Anders als [VerdictStabilizer] haelt er nichts fest: eine Zahl darf sich stetig bewegen,
 * sie soll nur nicht zappeln. Faellt der Score weg (`null`, kein Motiv), beginnt die
 * Glaettung beim naechsten Wert von vorn — ein alter Wert aus einer anderen Szene waere
 * sonst noch sekundenlang im neuen enthalten.
 */
class ScoreSmoother(private val timeConstantMs: Long = DEFAULT_TIME_CONSTANT_MS) {

    private var value: Float? = null
    private var lastMs: Long = 0L

    fun update(score: Int?, nowMs: Long): Int? {
        if (score == null) {
            value = null
            return null
        }
        val previous = value
        val next = if (previous == null) {
            score.toFloat()
        } else {
            val dt = (nowMs - lastMs).coerceAtLeast(0L)
            val alpha = 1f - exp(-dt.toFloat() / timeConstantMs)
            previous + alpha * (score - previous)
        }
        value = next
        lastMs = nowMs
        return next.roundToInt()
    }

    companion object {
        /** Nach einer halben Sekunde ist ein Sprung zu gut 60 % angekommen. */
        const val DEFAULT_TIME_CONSTANT_MS = 500L
    }
}
