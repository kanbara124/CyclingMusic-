package com.bupt.cyclingmusic

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bupt.cyclingmusic.service.CyclingSensorService
import com.bupt.cyclingmusic.service.MusicSyncService

class MainActivity : AppCompatActivity() {
    private lateinit var startButton: Button
    private lateinit var trainingButton: Button
    private lateinit var historyButton: Button
    private lateinit var cadenceText: TextView
    private lateinit var distanceText: TextView
    private lateinit var timeText: TextView
    private lateinit var trainingStatusText: TextView

    private var sensorService: CyclingSensorService? = null
    private var musicService: MusicSyncService? = null
    private var ridingService: RidingService? = null
    private var isSensorServiceBound = false
    private var isMusicServiceBound = false
    private var isRidingServiceBound = false

    private var isRiding = false
    private var startTime: Long = 0

    private var trainingMode = false
    private var targetCadence = 0
    private var cadenceTolerance = 5

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiRunnable = object : Runnable {
        override fun run() {
            if (isRiding) {
                updateUI()
                uiHandler.postDelayed(this, 500)
            }
        }
    }

    private val ridingStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RidingService.ACTION_RIDING_STOPPED) {
                val summaryIntent = Intent(this@MainActivity, RidingSummaryActivity::class.java).apply {
                    putExtra(RidingService.EXTRA_SUMMARY_DISTANCE,
                        intent.getDoubleExtra(RidingService.EXTRA_SUMMARY_DISTANCE, 0.0))
                    putExtra(RidingService.EXTRA_SUMMARY_DURATION,
                        intent.getLongExtra(RidingService.EXTRA_SUMMARY_DURATION, 0L))
                    putExtra(RidingService.EXTRA_SUMMARY_CADENCE,
                        intent.getFloatExtra(RidingService.EXTRA_SUMMARY_CADENCE, 0f))
                    putExtra(RidingService.EXTRA_SUMMARY_CALORIES,
                        intent.getIntExtra(RidingService.EXTRA_SUMMARY_CALORIES, 0))
                }
                startActivity(summaryIntent)
            }
        }
    }

    private val sensorConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            sensorService = (service as CyclingSensorService.LocalBinder).getService()
            isSensorServiceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isSensorServiceBound = false
        }
    }

    private val musicConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = (service as MusicSyncService.LocalBinder).getService()
            isMusicServiceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isMusicServiceBound = false
        }
    }

    private val ridingConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            ridingService = (service as RidingService.LocalBinder).getService()
            isRidingServiceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isRidingServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.startButton)
        trainingButton = findViewById(R.id.trainingButton)
        historyButton = findViewById(R.id.historyButton)
        cadenceText = findViewById(R.id.cadenceText)
        distanceText = findViewById(R.id.distanceText)
        timeText = findViewById(R.id.timeText)
        trainingStatusText = findViewById(R.id.trainingStatusText)

        startButton.setOnClickListener {
            if (!isRiding) startRiding() else stopRiding()
        }

        trainingButton.setOnClickListener {
            showTrainingModeDialog()
        }

        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        bindServices()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ridingStoppedReceiver, IntentFilter(RidingService.ACTION_RIDING_STOPPED), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(ridingStoppedReceiver, IntentFilter(RidingService.ACTION_RIDING_STOPPED))
        }
        requestRequiredPermissions()
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.BODY_SENSORS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun bindServices() {
        bindService(Intent(this, CyclingSensorService::class.java), sensorConnection, Context.BIND_AUTO_CREATE)
        bindService(Intent(this, MusicSyncService::class.java), musicConnection, Context.BIND_AUTO_CREATE)
        bindService(Intent(this, RidingService::class.java), ridingConnection, Context.BIND_AUTO_CREATE)
    }

    private fun startRiding() {
        isRiding = true
        startTime = System.currentTimeMillis()
        startButton.text = "停止骑行"
        startButton.setBackgroundColor(resources.getColor(R.color.accent_color, theme))
        trainingButton.isEnabled = false

        sensorService?.startRiding()

        val serviceIntent = Intent(this, RidingService::class.java).apply {
            action = "START_RIDING"
            if (trainingMode) putExtra("target_cadence", targetCadence)
        }
        startService(serviceIntent)
        uiHandler.postDelayed(uiRunnable, 500)
    }

    private fun stopRiding() {
        isRiding = false
        uiHandler.removeCallbacks(uiRunnable)
        sensorService?.stopRiding()
        musicService?.stopPlayback()
        startButton.text = "开始骑行"
        startButton.setBackgroundColor(resources.getColor(R.color.primary_color, theme))
        trainingButton.isEnabled = true
        trainingStatusText.visibility = View.GONE
        startService(Intent(this, RidingService::class.java).apply { action = "STOP_RIDING" })
    }

    private fun updateUI() {
        val cadence = sensorService?.getCurrentCadence() ?: 0f
        val elapsed = System.currentTimeMillis() - startTime
        val minutes = elapsed / 60000
        val seconds = (elapsed % 60000) / 1000
        val distance = ridingService?.getTotalDistance() ?: 0.0

        cadenceText.text = "踏频: ${cadence.toInt()} RPM"
        distanceText.text = "距离: ${String.format("%.2f", distance)} km"
        timeText.text = "时间: ${String.format("%02d:%02d", minutes, seconds)}"

        if (cadence > 0f) {
            musicService?.updateBpm(cadence)
        }

        ridingService?.updateCadence(cadence)

        updateTrainingFeedback(cadence)
    }

    private fun updateTrainingFeedback(cadence: Float) {
        if (!trainingMode || targetCadence == 0) {
            trainingStatusText.visibility = View.GONE
            return
        }
        trainingStatusText.visibility = View.VISIBLE
        val diff = cadence.toInt() - targetCadence
        when {
            cadence == 0f -> {
                trainingStatusText.text = "目标踏频: $targetCadence RPM"
                trainingStatusText.setTextColor(Color.parseColor("#8E8E93"))
            }
            diff > cadenceTolerance -> {
                trainingStatusText.text = "踏频过快 ↓ 减速 ${diff} RPM"
                trainingStatusText.setTextColor(Color.parseColor("#FF3B30"))
            }
            diff < -cadenceTolerance -> {
                trainingStatusText.text = "踏频过慢 ↑ 加速 ${-diff} RPM"
                trainingStatusText.setTextColor(Color.parseColor("#FF9500"))
            }
            else -> {
                trainingStatusText.text = "踏频完美 ✓ 保持节奏"
                trainingStatusText.setTextColor(Color.parseColor("#34C759"))
            }
        }
    }

    private fun showTrainingModeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_training_mode, null)
        val targetInput = dialogView.findViewById<android.widget.EditText>(R.id.targetCadenceInput)
        val toleranceInput = dialogView.findViewById<android.widget.EditText>(R.id.toleranceInput)

        if (trainingMode && targetCadence > 0) {
            targetInput.setText(targetCadence.toString())
            toleranceInput.setText(cadenceTolerance.toString())
        }

        AlertDialog.Builder(this)
            .setTitle("目标踏频训练")
            .setView(dialogView)
            .setPositiveButton("开启训练") { _, _ ->
                val target = targetInput.text.toString().toIntOrNull()
                val tolerance = toleranceInput.text.toString().toIntOrNull() ?: 5
                if (target != null && target > 0) {
                    trainingMode = true
                    targetCadence = target
                    cadenceTolerance = tolerance
                    trainingButton.text = "训练: $targetCadence RPM"
                    trainingButton.setBackgroundColor(Color.parseColor("#34C759"))
                }
            }
            .setNegativeButton("关闭训练") { _, _ ->
                trainingMode = false
                targetCadence = 0
                trainingButton.text = "训练模式"
                trainingButton.setBackgroundColor(Color.parseColor("#8E8E93"))
                trainingStatusText.visibility = View.GONE
            }
            .setNeutralButton("取消", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacks(uiRunnable)
        unregisterReceiver(ridingStoppedReceiver)
        if (isSensorServiceBound) unbindService(sensorConnection)
        if (isMusicServiceBound) unbindService(musicConnection)
        if (isRidingServiceBound) unbindService(ridingConnection)
    }
}
