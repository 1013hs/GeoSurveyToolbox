package com.geosurvey.toolbox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.geosurvey.toolbox.MainActivity
import com.geosurvey.toolbox.data.track.TrackPointEntity
import com.geosurvey.toolbox.data.track.TrackRepository
import com.geosurvey.toolbox.gnss.GnssManager
import com.geosurvey.toolbox.gnss.model.GnssFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectJob: Job? = null

    private lateinit var repository: TrackRepository
    private lateinit var gnssManager: GnssManager

    private var currentTrackId: String = ""
    private var isRecording = false

    override fun onCreate() {
        super.onCreate()
        repository = TrackRepository(applicationContext)
        gnssManager = GnssManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (isRecording) return
        isRecording = true
        currentTrackId = UUID.randomUUID().toString()

        val notification = buildNotification("正在记录轨迹…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        collectJob = serviceScope.launch {
            gnssManager.observeFixes()
                .catch { e -> e.printStackTrace() }
                .collect { fix ->
                    savePoint(fix)
                    updateNotification(fix)
                }
        }
    }

    private fun stopRecording() {
        isRecording = false
        collectJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun savePoint(fix: GnssFix) {
        // 质量过低的点不记录（可后续调整阈值）
        if (fix.quality.ordinal >= 3) return

        val entity = TrackPointEntity(
            trackId = currentTrackId,
            timestamp = fix.utcTimeMillis,
            latitude = fix.latitude,
            longitude = fix.longitude,
            altitude = fix.altitudeEllipsoid,
            speed = fix.speed,
            bearing = fix.bearing,
            accuracy = fix.accuracyHorizontal,
            satelliteCount = fix.usedSatelliteCount,
            hdop = fix.hdop,
            pdop = fix.pdop,
            qualityLabel = fix.quality.label
        )
        repository.insertPoint(entity)
    }

    private fun updateNotification(fix: GnssFix) {
        val acc = String.format("%.1f", fix.accuracyHorizontal)
        val text = "卫星:" + fix.usedSatelliteCount + "  精度:" + acc + "m  质量:" + fix.quality.label
        val notification = buildNotification(text)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoSurvey 轨迹记录中")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "轨迹记录服务",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "后台持续记录GNSS轨迹"
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        collectJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.geosurvey.toolbox.START_TRACKING"
        const val ACTION_STOP = "com.geosurvey.toolbox.STOP_TRACKING"
        private const val CHANNEL_ID = "track_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
