package com.example.runpace

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

// Palette
private val Bg = Color(0xFF0F1115)
private val CardBg = Color(0xFF1A1D23)
private val CardBorder = Color(0xFF262A32)
private val Orange = Color(0xFFFF5A1F)
private val Green = Color(0xFF2ECC71)
private val Amber = Color(0xFFFFB020)
private val Red = Color(0xFFFF4D4D)
private val TextDim = Color(0xFF8A919E)

private val RunColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    background = Bg,
    surface = Bg,
    onSurface = Color.White,
    error = Red
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = RunColors) {
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    RunScreen()
                }
            }
        }
    }
}

@Composable
fun RunScreen() {
    val context = LocalContext.current
    val tracker = RunState.tracker
    val running = RunState.running
    var elapsedSec by remember { mutableLongStateOf(0L) }

    val prefs = remember { context.getSharedPreferences("runpace", Context.MODE_PRIVATE) }
    var fastText by remember { mutableStateOf(prefs.getString("fast", "5:20") ?: "5:20") }
    var slowText by remember { mutableStateOf(prefs.getString("slow", "5:40") ?: "5:40") }
    val range = Pace.range(Pace.parseDuration(fastText), Pace.parseDuration(slowText))

    LaunchedEffect(fastText, slowText) {
        RunState.range = range
        prefs.edit().putString("fast", fastText).putString("slow", slowText).apply()
    }

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

    fun start() {
        RunState.range = range
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

    fun onMainButton() {
        if (running) {
            stop()
            return
        }
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
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    val status = if (running) Pace.status(tracker.currentPace, range) else null

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Header(running)
        HeroCard(running, tracker.currentPace, status, range)
        StatsRow(tracker.distanceM, elapsedSec, tracker.averagePace)
        RangeCard(fastText, slowText, range != null, { fastText = it }, { slowText = it })
        MainButton(running, enabled = range != null || running, onClick = ::onMainButton)
        if (running) {
            Text(
                "Vous pouvez verrouiller le téléphone ou changer d'app : le suivi continue.",
                color = TextDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        CalculatorCard()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Header(running: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("RunPace", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("Contrôle d'allure GPS", color = TextDim, fontSize = 14.sp)
        }
        if (running) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Orange.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Orange))
                Spacer(Modifier.width(6.dp))
                Text("EN COURSE", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CardBg)
            .padding(20.dp)
    ) { content() }
}

@Composable
private fun HeroCard(
    running: Boolean,
    currentPace: Double?,
    status: Pace.Status?,
    range: Pace.Range?
) {
    val accent by animateColorAsState(
        when (status) {
            Pace.Status.OK -> Green
            Pace.Status.SLOW -> Red
            Pace.Status.FAST -> Amber
            null -> TextDim
        },
        label = "accent"
    )
    val label = when {
        !running -> "Prêt à courir"
        status == Pace.Status.OK -> "Bonne allure"
        status == Pace.Status.SLOW -> "Trop lent · accélère"
        status == Pace.Status.FAST -> "Trop rapide · ralentis"
        else -> "Recherche du GPS…"
    }

    AppCard(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("ALLURE ACTUELLE", color = TextDim, fontSize = 12.sp, letterSpacing = 2.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Pace.format(if (running) currentPace else null),
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Black,
                    color = if (status != null) accent else Color.White
                )
                Text(
                    " /km",
                    fontSize = 20.sp,
                    color = TextDim,
                    modifier = Modifier.padding(bottom = 18.dp)
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(label, color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            if (range != null) {
                Spacer(Modifier.height(20.dp))
                PaceGauge(range, if (running) currentPace else null, accent)
            }
        }
    }
}

/** Jauge horizontale : zone verte = plage visée, point = allure actuelle. */
@Composable
private fun PaceGauge(range: Pace.Range, current: Double?, accent: Color) {
    val margin = maxOf(30.0, (range.slow - range.fast))
    val min = range.fast - margin // plus rapide, à gauche
    val max = range.slow + margin // plus lent, à droite
    fun pos(p: Double) = ((p - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
    val marker by animateFloatAsState(current?.let { pos(it) } ?: -1f, label = "marker")

    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val h = 10.dp.toPx()
            val y = (size.height - h) / 2
            val r = CornerRadius(h / 2, h / 2)
            drawRoundRect(CardBorder, Offset(0f, y), Size(size.width, h), r)
            val x0 = pos(range.fast) * size.width
            val x1 = pos(range.slow) * size.width
            drawRoundRect(Green.copy(alpha = 0.55f), Offset(x0, y), Size(x1 - x0, h), r)
            if (marker >= 0f) {
                val cx = marker * size.width
                drawCircle(Color.White, 11.dp.toPx(), Offset(cx, size.height / 2))
                drawCircle(accent, 7.dp.toPx(), Offset(cx, size.height / 2))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("rapide", color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                "${Pace.format(range.fast)} – ${Pace.format(range.slow)}",
                color = Green,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "lent",
                color = TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatsRow(distanceM: Double, elapsedSec: Long, avgPace: Double?) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("DISTANCE", "%.2f".format(distanceM / 1000), "km", Modifier.weight(1f))
        StatTile("TEMPS", Pace.formatHms(elapsedSec), "", Modifier.weight(1f))
        StatTile("MOYENNE", Pace.format(avgPace), "/km", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(vertical = 14.dp, horizontal = 12.dp)
    ) {
        Text(label, color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (unit.isNotEmpty()) Text(unit, color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun RangeCard(
    fastText: String,
    slowText: String,
    valid: Boolean,
    onFast: (String) -> Unit,
    onSlow: (String) -> Unit
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Plage d'allure visée", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PaceField(fastText, onFast, "De", Modifier.weight(1f))
                Text("→", color = TextDim, fontSize = 20.sp)
                PaceField(slowText, onSlow, "À", Modifier.weight(1f))
            }
            Text(
                if (valid) "En min/km, par exemple 5:20 → 5:40" else "Format attendu : 5:20",
                color = if (valid) TextDim else Red,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun PaceField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        textStyle = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        ),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Orange,
            unfocusedBorderColor = CardBorder,
            focusedLabelColor = Orange,
            cursorColor = Orange
        ),
        modifier = modifier
    )
}

@Composable
private fun MainButton(running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (running) Red else Orange, label = "btn")
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Text(
            if (running) "■  ARRÊTER" else "▶  DÉMARRER",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}

/** Calcul manuel, replié par défaut. */
@Composable
fun CalculatorCard() {
    var open by remember { mutableStateOf(false) }
    var distanceText by remember { mutableStateOf("10") }
    var timeText by remember { mutableStateOf("50:00") }

    val distanceKm = distanceText.replace(',', '.').toDoubleOrNull()
    val seconds = Pace.parseDuration(timeText)
    val pace = if (distanceKm != null && seconds != null) {
        Pace.secondsPerKm(distanceKm * 1000, seconds)
    } else {
        null
    }

    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Calculateur d'allure",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(if (open) "▲" else "▼", color = TextDim)
            }
            if (open) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaceField(distanceText, { distanceText = it }, "Distance (km)", Modifier.weight(1f))
                    PaceField(timeText, { timeText = it }, "Durée", Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Allure : ", color = TextDim)
                    Text(Pace.format(pace), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Orange)
                    Text(" /km", color = TextDim)
                }
            }
        }
    }
}
