package com.bupt.cyclingmusic.data.database

import android.content.Context
import com.bupt.cyclingmusic.data.RidingDatabase

object DatabaseHelper {
    @Volatile
    private var instance: RidingDatabase? = null

    fun getDatabase(context: Context): RidingDatabase {
        return instance ?: synchronized(this) {
            instance ?: RidingDatabase.getDatabase(context).also { instance = it }
        }
    }
}
