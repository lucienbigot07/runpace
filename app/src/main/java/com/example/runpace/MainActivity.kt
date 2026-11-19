package com.example.runpace

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

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

/** Suivi GPS : l'écran pilote RunService, qui continue même téléphone verrouillé. */
@Composable
fun GpsCard() {
    val context = LocalContext.current
    val tracker = RunState.tracker
    val running = RunState.running
    var elapsedSec by remember { mutableLongStateOf(0L) }
    var targetText by remember { mutableStateOf("5:30") }
    val targetSec = Pace.parseDuration(targetText)
    LaunchedEffect(targetSec) { RunState.targetSec = targetSec }

    fun start() {
        RunState.targetSec = targetSec
        ContextCompat.startForegroundService(context, Intent(context, RunService::class.java))
    }

    fun stop() {
        context.startService(
            Intent(context, RunService::class.java).setAction(RunService.ACTION_STOP)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) start()
    }

    // Temps écoulé, rafraîchi chaque seconde.
    LaunchedEffect(running) {
        while (running) {
            elapsedSec = if (tracker.startMs > 0) {
                (System.currentTimeMillis() - tracker.startMs) / 1000
            } else {
                0L
            }
            delay(1000)
        }
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
            if (running) {
                Text(
                    "Vous pouvez verrouiller le téléphone, le suivi continue.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = {
                if (running) {
                    stop()
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val needNotif = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    if (granted && !needNotif) {
                        start()
                    } else {
                        val perms = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= 33) {
                            perms += Manifest.permission.POST_NOTIFICATIONS
                        }
                        permissionLauncher.launch(perms.toTypedArray())
                    }
                }
            }) {
                Text(if (running) "Arrêter" else "Démarrer")
            }
        }
    }
}
