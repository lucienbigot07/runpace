package com.example.runpace

import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Suit une course à partir des positions GPS.
 * - allure moyenne : depuis le départ
 * - allure actuelle : sur les ~20 dernières secondes (lissée)
 */
class PaceTracker {

    private val window = ArrayDeque<Pair<Long, Double>>() // (temps en ms, distance cumulée en m)
    private var lastLocation: Location? = null
    private var distance = 0.0

    var startMs = 0L
        private set

    var distanceM by mutableDoubleStateOf(0.0)
        private set

    var currentPace by mutableStateOf<Double?>(null)
        private set

    var averagePace by mutableStateOf<Double?>(null)
        private set

    fun reset() {
        window.clear()
        lastLocation = null
        distance = 0.0
        startMs = 0L
        distanceM = 0.0
        currentPace = null
        averagePace = null
    }

    fun onLocation(location: Location) {
        // On ignore les positions imprécises et le bruit GPS à l'arrêt.
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return

        val previous = lastLocation
        if (previous == null) {
            startMs = location.time
        } else {
            val step = previous.distanceTo(location).toDouble()
            if (step < MIN_STEP_M) return
            distance += step
        }
        lastLocation = location
        distanceM = distance

        val t = location.time
        window.addLast(t to distance)
        // On garde un seul point plus ancien que la fenêtre, pour couvrir toute la fenêtre.
        while (window.size > 2 && t - window[1].first >= WINDOW_MS) window.removeFirst()

        val (t0, d0) = window.first()
        currentPace = Pace.secondsPerKm(distance - d0, (t - t0) / 1000.0)
        averagePace = Pace.secondsPerKm(distance, (t - startMs) / 1000.0)
    }

    companion object {
        private const val WINDOW_MS = 20_000L
        private const val MAX_ACCURACY_M = 20f
        private const val MIN_STEP_M = 1.0
    }
}
