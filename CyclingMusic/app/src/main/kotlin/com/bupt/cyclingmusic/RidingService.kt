package com.bupt.cyclingmusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.bupt.cyclingmusic.data.database.DatabaseHelper
import com.bupt.cyclingmusic.data.entity.RidingRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class RidingService : Service() {
    private val binder = LocalBinder()
    @Volatile private var isRiding = false
    private var startTime: Long = 0
    @Volatile private var totalDistance = 0.0
    @Volatile private var currentCadence = 0f
    private val cadenceSamples = java.util.Collections.synchronizedList(mutableListOf<Float>())
    private var targetCadence = 0
    private val notificationUpdateInterval = 5000L

    private val timer = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    inner class LocalBinder : Binder() {
        fun getService(): RidingService = this@RidingService
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "riding_channel"
        const val ACTION_RIDING_STOPPED = "com.bupt.cyclingmusic.RIDING_STOPPED"
        const val EXTRA_SUMMARY_DISTANCE = "extra_distance"
        const val EXTRA_SUMMARY_DURATION = "extra_duration"
        const val EXTRA_SUMMARY_CADENCE = "extra_cadence"
        const val EXTRA_SUMMARY_CALORIES = "extra_calories"
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_RIDING" -> {
                val target = intent.getIntExtra("target_cadence", 0)
                startRiding(target)
            }
            "STOP_RIDING" -> stopRiding()
        }
        return START_STICKY
    }

    private fun startRiding(target: Int = 0) {
        if (!isRiding) {
            isRiding = true
            startTime = System.currentTimeMillis()
            totalDistance = 0.0
            cadenceSamples.clear()
            targetCadence = target
            startForeground(NOTIFICATION_ID, createNotification())
            timerRunnable = object : Runnable {
                override fun run() {
                    if (isRiding) {
                        totalDistance += calculateDistance(currentCadence, notificationUpdateInterval)
                        cadenceSamples.add(currentCadence)
                        updateNotification()
                        timer.postDelayed(this, notificationUpdateInterval)
                    }
                }
            }
            timer.postDelayed(timerRunnable!!, notificationUpdateInterval)
        }
    }

    private fun stopRiding() {
        if (isRiding) {
            isRiding = false
            timerRunnable?.let { timer.removeCallbacks(it) }
            val endTime = System.currentTimeMillis()
            val durationSeconds = (endTime - startTime) / 1000
            val avgCadence = if (cadenceSamples.isNotEmpty()) cadenceSamples.average().toFloat() else currentCadence
            val cal = calculateCalories(avgCadence, durationSeconds)
            saveRidingData(endTime, durationSeconds, avgCadence, cal)

            val summaryIntent = Intent(ACTION_RIDING_STOPPED).apply {
                setPackage(packageName)
                putExtra(EXTRA_SUMMARY_DISTANCE, totalDistance)
                putExtra(EXTRA_SUMMARY_DURATION, durationSeconds)
                putExtra(EXTRA_SUMMARY_CADENCE, avgCadence)
                putExtra(EXTRA_SUMMARY_CALORIES, cal)
            }
            sendBroadcast(summaryIntent)

            stopForeground(true)
            stopSelf()
        }
    }

    private fun calculateDistance(cadence: Float, timeMillis: Long): Double {
        val wheelCircumference = 2.1
        return cadence * wheelCircumference * (timeMillis / 60000.0)
    }

    private fun calculateCalories(avgCadence: Float, durationSeconds: Long): Int {
        // MET 估算：轻松骑行 MET≈6，正常骑行 MET≈8，激烈骑行 MET≈10
        val met = when {
            avgCadence < 60 -> 6.0
            avgCadence < 90 -> 8.0
            else -> 10.0
        }
        val weightKg = 70.0
        val hours = durationSeconds / 3600.0
        return (met * weightKg * hours).toInt()
    }

    fun updateCadence(cadence: Float) {
        currentCadence = cadence
    }

    fun getTotalDistance(): Double = totalDistance

    fun getTargetCadence(): Int = targetCadence

    private fun saveRidingData(endTime: Long, durationSeconds: Long, avgCadence: Float, calories: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = DatabaseHelper.getDatabase(applicationContext)
            val record = RidingRecord(
                startTime = Date(startTime),
                endTime = Date(endTime),
                totalDistance = totalDistance,
                averageCadence = avgCadence,
                duration = durationSeconds,
                calories = calories,
                targetCadence = targetCadence
            )
            db.ridingRecordDao().insert(record)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "骑行服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("骑行中")
            .setContentText("距离: ${String.format("%.2f", totalDistance)} km")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer.removeCallbacksAndMessages(null)
    }
}
