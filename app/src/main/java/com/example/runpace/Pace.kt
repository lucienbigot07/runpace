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

    /** Compare l'allure actuelle à la cible : message à afficher / lire à voix haute. */
    fun feedback(current: Double?, target: Double, toleranceS: Double = 5.0): String? {
        if (current == null) return null
        return when {
            current > target + toleranceS -> "Trop lent, accélère"
            current < target - toleranceS -> "Trop rapide, ralentis"
            else -> "Bonne allure"
        }
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
