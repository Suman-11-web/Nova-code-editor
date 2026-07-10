package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecentFile::class, EditorTab::class], version = 1, exportSchema = false)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun novaDao(): NovaDao

    companion object {
        @Volatile
        private var INSTANCE: NovaDatabase? = null

        fun getDatabase(context: Context): NovaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NovaDatabase::class.java,
                    "nova_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
