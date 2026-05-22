package com.bupt.cyclingmusic

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RidingSummaryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_riding_summary)

        val distance = intent.getDoubleExtra(RidingService.EXTRA_SUMMARY_DISTANCE, 0.0)
        val duration = intent.getLongExtra(RidingService.EXTRA_SUMMARY_DURATION, 0L)
        val avgCadence = intent.getFloatExtra(RidingService.EXTRA_SUMMARY_CADENCE, 0f)
        val calories = intent.getIntExtra(RidingService.EXTRA_SUMMARY_CALORIES, 0)

        findViewById<TextView>(R.id.summaryDistanceValue).text =
            String.format("%.2f km", distance)
        findViewById<TextView>(R.id.summaryDurationValue).text = formatTime(duration)
        findViewById<TextView>(R.id.summaryCadenceValue).text =
            "${avgCadence.toInt()} RPM"
        findViewById<TextView>(R.id.summaryCaloriesValue).text = "$calories kcal"

        findViewById<Button>(R.id.summaryDoneButton).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.summaryHistoryButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
            finish()
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
