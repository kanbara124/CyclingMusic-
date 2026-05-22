package com.bupt.cyclingmusic.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.bupt.cyclingmusic.data.converter.DateConverter
import com.bupt.cyclingmusic.data.dao.RidingRecordDao
import com.bupt.cyclingmusic.data.entity.RidingRecord

@Database(entities = [RidingRecord::class], version = 2)
@TypeConverters(DateConverter::class)
abstract class RidingDatabase : RoomDatabase() {
    abstract fun ridingRecordDao(): RidingRecordDao

    companion object {
        @Volatile
        private var INSTANCE: RidingDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE riding_records ADD COLUMN calories INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE riding_records ADD COLUMN targetCadence INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): RidingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RidingDatabase::class.java,
                    "riding_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
