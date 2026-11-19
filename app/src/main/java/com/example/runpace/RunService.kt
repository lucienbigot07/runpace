package com.example.runpace

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

/** État de la course partagé entre le service et l'écran. */
object RunState {
    val tracker = PaceTracker()
    var running by mutableStateOf(false)
    var range: Pace.Range? = null
}

/**
 * Service de premier plan : suit le GPS et annonce l'allure,
 * même quand le téléphone est verrouillé.
 */
class RunService : Service() {

    private lateinit var client: FusedLocationProviderClient
    private lateinit var tts: TextToSpeech
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticks = 0

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { RunState.tracker.onLocation(it) }
        }
    }

    // Toutes les secondes : notification à jour, annonce vocale toutes les 15 s.
    private val tick = object : Runnable {
        override fun run() {
            if (!RunState.running) return
            ticks++
            val tracker = RunState.tracker
            val range = RunState.range
            val message = range?.let { Pace.feedback(tracker.currentPace, it) }
            if (ticks % 5 == 0) notify(buildNotification(message))
            if (ticks % ANNOUNCE_EVERY_S == 0) {
                range?.let { Pace.spoken(tracker.currentPace, it) }?.let {
                    tts.speak(it, TextToSpeech.QUEUE_FLUSH, null, "pace")
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts.language = Locale.FRENCH
        }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRun()
            else -> startRun()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun startRun() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(null), type)

        if (RunState.running) return
        RunState.tracker.reset()
        RunState.running = true
        ticks = 0

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RunPace:run")
            .apply { acquire() }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        handler.postDelayed(tick, 1000)
        tts.speak("C'est parti", TextToSpeech.QUEUE_FLUSH, null, "start")
    }

    private fun stopRun() {
        RunState.running = false
        handler.removeCallbacks(tick)
        client.removeLocationUpdates(callback)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (RunState.running) stopRun()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Course en cours", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(message: String?): Notification {
        val tracker = RunState.tracker
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RunService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val text = "Allure ${Pace.format(tracker.currentPace)} /km · " +
            "%.2f km".format(tracker.distanceM / 1000)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(message ?: "Course en cours")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(0, "Arrêter", stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.example.runpace.STOP"
        private const val CHANNEL_ID = "run"
        private const val NOTIFICATION_ID = 1
        private const val ANNOUNCE_EVERY_S = 15
    }
}
