package com.bupt.cyclingmusic.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bupt.cyclingmusic.data.entity.RidingRecord
import java.util.Date

@Dao
interface RidingRecordDao {
    @Insert
    fun insert(record: RidingRecord)

    @Query("SELECT * FROM riding_records ORDER BY startTime DESC")
    fun getAllRecords(): List<RidingRecord>

    @Query("SELECT * FROM riding_records WHERE startTime >= :startDate AND startTime <= :endDate ORDER BY startTime DESC")
    fun getRecordsByDateRange(startDate: Date, endDate: Date): List<RidingRecord>

    @Query("SELECT SUM(duration) FROM riding_records")
    fun getTotalDuration(): Long

    @Query("SELECT SUM(totalDistance) FROM riding_records")
    fun getTotalDistance(): Double

    @Query("SELECT SUM(calories) FROM riding_records")
    fun getTotalCalories(): Int
}
