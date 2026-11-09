package de.honeybalecta.datenfaehre

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager

/** Hält die App wach, solange die Warteschlange läuft – auch im Hintergrund und bei ausgeschaltetem Bildschirm. */
class Dienst : Service() {
    private var wachsperre: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instanz = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val verwaltung = getSystemService(NotificationManager::class.java)
        verwaltung.createNotificationChannel(
            NotificationChannel(KANAL, "Übertragungen", NotificationManager.IMPORTANCE_LOW)
        )
        val oeffnen = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val hinweis = Notification.Builder(this, KANAL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Dateiübertragung läuft")
            .setContentText("Die Warteschlange wird abgearbeitet.")
            .setOngoing(true)
            .setContentIntent(oeffnen)
            .build()
        try {
            startForeground(1, hinweis, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (e: Exception) {
            // Start aus dem Hintergrund nicht erlaubt: Die Übertragung läuft weiter, solange die App offen ist.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!gewuenscht) {
            beenden()
            return START_NOT_STICKY
        }
        if (wachsperre == null) {
            val strom = getSystemService(PowerManager::class.java)
            wachsperre = strom.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "datenfaehre:uebertragung").apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L)
            }
        }
        return START_NOT_STICKY
    }

    private fun beenden() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { wachsperre?.let { if (it.isHeld) it.release() } }
        wachsperre = null
        if (instanz === this) instanz = null
        super.onDestroy()
    }

    companion object {
        private const val KANAL = "uebertragung"
        @Volatile private var gewuenscht = false
        @Volatile private var instanz: Dienst? = null

        fun starten(context: Context) {
            gewuenscht = true
            try {
                context.startForegroundService(Intent(context, Dienst::class.java))
            } catch (e: Exception) {
                // siehe oben
            }
        }

        fun stoppen() {
            gewuenscht = false
            instanz?.beenden()
        }
    }
}
