package com.bupt.cyclingmusic.service

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.HandlerThread
import android.os.IBinder
import java.util.Collections
import kotlin.math.sqrt

class CyclingSensorService : Service(), SensorEventListener {
    private val binder = LocalBinder()
    private lateinit var sensorManager: SensorManager
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: android.os.Handler

    @Volatile
    private var currentCadence = 0f
    private val accelData = Collections.synchronizedList(mutableListOf<Float>())
    private val gyroData = Collections.synchronizedList(mutableListOf<Float>())

    private var filteredAccel = FloatArray(3)
    private var filteredGyro = FloatArray(3)
    private val alpha = 0.2f

    private val BUFFER_LIMIT = 200

    inner class LocalBinder : Binder() {
        fun getService(): CyclingSensorService = this@CyclingSensorService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        handlerThread = HandlerThread("SensorThread")
        handlerThread.start()
        handler = android.os.Handler(handlerThread.looper)
    }

    fun startRiding() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler)
        }
    }

    fun stopRiding() {
        sensorManager.unregisterListener(this)
        accelData.clear()
        gyroData.clear()
    }

    fun getCurrentCadence(): Float = currentCadence

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                filteredAccel[0] = alpha * event.values[0] + (1 - alpha) * filteredAccel[0]
                filteredAccel[1] = alpha * event.values[1] + (1 - alpha) * filteredAccel[1]
                filteredAccel[2] = alpha * event.values[2] + (1 - alpha) * filteredAccel[2]
                val magnitude = sqrt(
                    filteredAccel[0] * filteredAccel[0] +
                    filteredAccel[1] * filteredAccel[1] +
                    filteredAccel[2] * filteredAccel[2]
                ).toFloat()
                accelData.add(magnitude)
            }
            Sensor.TYPE_GYROSCOPE -> {
                filteredGyro[0] = alpha * event.values[0] + (1 - alpha) * filteredGyro[0]
                filteredGyro[1] = alpha * event.values[1] + (1 - alpha) * filteredGyro[1]
                filteredGyro[2] = alpha * event.values[2] + (1 - alpha) * filteredGyro[2]
                val magnitude = sqrt(
                    filteredGyro[0] * filteredGyro[0] +
                    filteredGyro[1] * filteredGyro[1] +
                    filteredGyro[2] * filteredGyro[2]
                ).toFloat()
                gyroData.add(magnitude)
            }
        }

        val shouldProcess = accelData.size >= 100 || gyroData.size >= 100
            || accelData.size >= BUFFER_LIMIT || gyroData.size >= BUFFER_LIMIT
        if (shouldProcess) {
            handler.post {
                val accelSnapshot = synchronized(accelData) { accelData.toList().also { accelData.clear() } }
                val gyroSnapshot = synchronized(gyroData) { gyroData.toList().also { gyroData.clear() } }
                currentCadence = calculateCadence(accelSnapshot, gyroSnapshot)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateCadence(accelList: List<Float>, gyroList: List<Float>): Float {
        // 对两路信号独立峰值检测再加权融合，避免 zip 丢弃数据
        val accelCadence = detectPeakCadence(accelList)
        val gyroCadence = detectPeakCadence(gyroList)

        return when {
            accelList.isEmpty() && gyroList.isEmpty() -> 0f
            accelList.isEmpty() -> gyroCadence
            gyroList.isEmpty() -> accelCadence
            else -> (accelCadence * 0.6f + gyroCadence * 0.4f)
        }
    }

    private fun detectPeakCadence(data: List<Float>): Float {
        if (data.size < 2) return 0f
        val mean = data.average().toFloat()
        var peaks = 0
        for (i in 1 until data.size - 1) {
            if (data[i] > mean && data[i] > data[i - 1] && data[i] > data[i + 1]) {
                peaks++
            }
        }
        val timeWindow = data.size / 50f
        return if (timeWindow > 0) (peaks / timeWindow) * 60f else 0f
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        handlerThread.quitSafely()
    }
}
