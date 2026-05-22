package com.bupt.cyclingmusic

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bupt.cyclingmusic.data.database.DatabaseHelper
import com.bupt.cyclingmusic.data.RidingDatabase
import com.bupt.cyclingmusic.data.entity.RidingRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var backButton: Button
    private lateinit var totalRidesTextView: TextView
    private lateinit var totalDistanceTextView: TextView
    private lateinit var totalTimeTextView: TextView
    private lateinit var totalCaloriesTextView: TextView
    private lateinit var historyListView: ListView

    private lateinit var database: RidingDatabase
    private val historyRecords = mutableListOf<RidingRecord>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        initViews()
        setupClickListeners()
        database = DatabaseHelper.getDatabase(this)
        loadHistoryRecords()
    }

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        totalRidesTextView = findViewById(R.id.totalRidesTextView)
        totalDistanceTextView = findViewById(R.id.totalDistanceTextView)
        totalTimeTextView = findViewById(R.id.totalTimeTextView)
        totalCaloriesTextView = findViewById(R.id.totalCaloriesTextView)
        historyListView = findViewById(R.id.historyListView)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }
    }

    private fun loadHistoryRecords() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val records = database.ridingRecordDao().getAllRecords()
                withContext(Dispatchers.Main) {
                    historyRecords.clear()
                    historyRecords.addAll(records)
                    updateStats()
                    setupListView()
                }
            } catch (e: Exception) {
                Log.e("HistoryActivity", "加载历史记录失败: ${e.message}")
            }
        }
    }

    private fun updateStats() {
        val totalRides = historyRecords.size
        val totalDistance = historyRecords.sumOf { it.totalDistance }
        val totalTime = historyRecords.sumOf { it.duration }
        val totalCalories = historyRecords.sumOf { it.calories }

        totalRidesTextView.text = "$totalRides"
        totalDistanceTextView.text = String.format("%.1f", totalDistance)
        totalTimeTextView.text = formatTime(totalTime)
        totalCaloriesTextView.text = "$totalCalories"
    }

    private fun setupListView() {
        val adapter = HistoryRecordAdapter()
        historyListView.adapter = adapter
    }

    private inner class HistoryRecordAdapter : BaseAdapter() {

        override fun getCount(): Int = historyRecords.size

        override fun getItem(position: Int): Any = historyRecords[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_history_record, parent, false)

            val record = historyRecords[position]

            val dateTextView = view.findViewById<TextView>(R.id.dateTextView)
            val timeTextView = view.findViewById<TextView>(R.id.timeTextView)
            val distanceTextView = view.findViewById<TextView>(R.id.distanceTextView)
            val durationTextView = view.findViewById<TextView>(R.id.durationTextView)
            val cadenceTextView = view.findViewById<TextView>(R.id.cadenceTextView)
            val speedTextView = view.findViewById<TextView>(R.id.speedTextView)
            val caloriesTextView = view.findViewById<TextView>(R.id.caloriesTextView)

            val date = record.startTime
            dateTextView.text = dateFormat.format(date)
            timeTextView.text = timeFormat.format(date)
            distanceTextView.text = "${String.format("%.1f", record.totalDistance)} km"
            durationTextView.text = formatTime(record.duration)
            cadenceTextView.text = "${record.averageCadence.toInt()} RPM"
            caloriesTextView.text = "${record.calories} kcal"
            val avgSpeed = if (record.duration > 0) record.totalDistance / (record.duration / 3600.0) else 0.0
            speedTextView.text = "${String.format("%.1f", avgSpeed)} km/h"

            return view
        }
    }

    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
}
