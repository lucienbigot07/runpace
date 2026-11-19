package com.example.runpace

import kotlin.math.roundToLong

/** Calculs d'allure (temps par kilomètre). */
object Pace {

    /** Allure en secondes par km, ou null si la distance ou la durée est nulle. */
    fun secondsPerKm(distanceM: Double, durationS: Double): Double? {
        if (distanceM <= 0.0 || durationS <= 0.0) return null
        return durationS / (distanceM / 1000.0)
    }

    /** Formate une allure en secondes/km : 330.0 -> "5:30". */
    fun format(secondsPerKm: Double?): String {
        if (secondsPerKm == null || !secondsPerKm.isFinite()) return "--:--"
        val total = secondsPerKm.roundToLong()
        return "%d:%02d".format(total / 60, total % 60)
    }

    /** Formate une durée en secondes : 3725 -> "1:02:05". */
    fun formatHms(totalSeconds: Long): String =
        "%d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)

    /** Plage d'allure : fast = la plus rapide (ex. 5:20), slow = la plus lente (ex. 5:40). */
    data class Range(val fast: Double, val slow: Double)

    /** Construit une plage à partir de deux allures, dans n'importe quel ordre. */
    fun range(a: Double?, b: Double?): Range? {
        if (a == null || b == null) return null
        return Range(minOf(a, b), maxOf(a, b))
    }

    /** Message court affiché à l'écran : "Trop lent · 5:52 /km". */
    fun feedback(current: Double?, range: Range): String? {
        if (current == null) return null
        val verdict = when {
            current > range.slow -> "Trop lent"
            current < range.fast -> "Trop rapide"
            else -> "Bonne allure"
        }
        return "$verdict · ${format(current)} /km"
    }

    /** Phrase lue à voix haute : "Trop lent, 5 minutes 52. Accélère." */
    fun spoken(current: Double?, range: Range): String? {
        if (current == null) return null
        val pace = speak(current)
        return when {
            current > range.slow -> "Trop lent, $pace. Accélère."
            current < range.fast -> "Trop rapide, $pace. Ralentis."
            else -> "Bonne allure, $pace."
        }
    }

    /** 352.0 -> "5 minutes 52" ; 300.0 -> "5 minutes pile". */
    fun speak(secondsPerKm: Double): String {
        val total = secondsPerKm.roundToLong()
        val min = total / 60
        val sec = total % 60
        return if (sec == 0L) "$min minutes pile" else "$min minutes $sec"
    }

    /** Lit une durée "mm", "mm:ss" ou "h:mm:ss" et renvoie des secondes, ou null si invalide. */
    fun parseDuration(text: String): Double? {
        val parts = text.trim().split(":")
        if (parts.size !in 1..3) return null
        val nums = parts.map { it.replace(',', '.').toDoubleOrNull() ?: return null }
        if (nums.any { it < 0.0 }) return null
        return when (nums.size) {
            1 -> nums[0] * 60
            2 -> nums[0] * 60 + nums[1]
            else -> nums[0] * 3600 + nums[1] * 60 + nums[2]
        }
    }
}
