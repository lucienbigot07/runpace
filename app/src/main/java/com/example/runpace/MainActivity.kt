package com.example.runpace

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import android.speech.tts.TextToSpeech
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CalculatorCard()
                        GpsCard()
                    }
                }
            }
        }
    }
}

/** Calcul manuel : distance et durée saisies -> allure. */
@Composable
fun CalculatorCard() {
    var distanceText by remember { mutableStateOf("10") }
    var timeText by remember { mutableStateOf("50:00") }

    val distanceKm = distanceText.replace(',', '.').toDoubleOrNull()
    val seconds = Pace.parseDuration(timeText)
    val pace = if (distanceKm != null && seconds != null) {
        Pace.secondsPerKm(distanceKm * 1000, seconds)
    } else {
        null
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Calculer l'allure", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = distanceText,
                onValueChange = { distanceText = it },
                label = { Text("Distance (km)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = timeText,
                onValueChange = { timeText = it },
                label = { Text("Durée (mm:ss ou h:mm:ss)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Allure : ${Pace.format(pace)} /km",
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

/** Suivi GPS en temps réel : allure actuelle, moyenne, distance et temps. */
@SuppressLint("MissingPermission")
@Composable
fun GpsCard() {
    val context = LocalContext.current
    val client = remember { LocationServices.getFusedLocationProviderClient(context) }
    val tracker = remember { PaceTracker() }
    var running by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableLongStateOf(0L) }
    var targetText by remember { mutableStateOf("5:30") }
    val targetSec = Pace.parseDuration(targetText)
    val targetState = rememberUpdatedState(targetSec)

    // Synthèse vocale en français : annonce l'allure toutes les 15 s pendant la course.
    val tts = remember {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) engine.language = Locale.FRENCH
        }
        engine
    }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    val callback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { tracker.onLocation(it) }
            }
        }
    }

    fun start() {
        tracker.reset()
        elapsedSec = 0L
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        running = true
    }

    fun stop() {
        client.removeLocationUpdates(callback)
        running = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) start()
    }

    // Met à jour le temps écoulé une fois par seconde pendant la course.
    LaunchedEffect(running) {
        var ticks = 0
        while (running) {
            elapsedSec = if (tracker.startMs > 0) {
                (System.currentTimeMillis() - tracker.startMs) / 1000
            } else {
                0L
            }
            ticks++
            val target = targetState.value
            if (ticks % 15 == 0 && target != null) {
                Pace.feedback(tracker.currentPace, target)?.let {
                    tts.speak(it, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
            delay(1000)
        }
    }

    // Arrête les mises à jour GPS si l'écran disparaît.
    DisposableEffect(Unit) {
        onDispose { client.removeLocationUpdates(callback) }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Suivi GPS", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it },
                label = { Text("Allure cible (mm:ss /km)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Allure actuelle : ${Pace.format(tracker.currentPace)} /km",
                style = MaterialTheme.typography.headlineSmall
            )
            if (running && targetSec != null) {
                Pace.feedback(tracker.currentPace, targetSec)?.let {
                    Text(it, style = MaterialTheme.typography.headlineMedium)
                }
            }
            Text("Allure moyenne : ${Pace.format(tracker.averagePace)} /km")
            Text("Distance : ${"%.2f".format(tracker.distanceM / 1000)} km")
            Text("Temps : ${Pace.formatHms(elapsedSec)}")
            Button(onClick = {
                if (running) {
                    stop()
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        start()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            }) {
                Text(if (running) "Arrêter" else "Démarrer")
            }
        }
    }
}
