package com.bupt.cyclingmusic.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "riding_records")
data class RidingRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Date,
    val endTime: Date,
    val totalDistance: Double,
    val averageCadence: Float,
    val duration: Long,
    val calories: Int = 0,
    val targetCadence: Int = 0
)
