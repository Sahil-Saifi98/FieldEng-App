package com.company.fieldapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AttendanceEntity::class, ExpenseEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE attendance ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE attendance ADD COLUMN employeeId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS expenses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId TEXT NOT NULL,
                        employeeId TEXT NOT NULL,
                        category TEXT NOT NULL,
                        amount REAL NOT NULL,
                        date TEXT NOT NULL,
                        description TEXT NOT NULL,
                        receiptImagePath TEXT,
                        status TEXT NOT NULL DEFAULT 'pending',
                        timestamp INTEGER NOT NULL,
                        isSynced INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS expenses")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS expenses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId TEXT NOT NULL,
                        employeeId TEXT NOT NULL,
                        tripId TEXT NOT NULL,
                        stationVisited TEXT NOT NULL,
                        periodFrom TEXT NOT NULL,
                        periodTo TEXT NOT NULL,
                        advanceAmount REAL NOT NULL DEFAULT 0.0,
                        expenseType TEXT NOT NULL,
                        details TEXT NOT NULL DEFAULT '',
                        travelFrom TEXT NOT NULL DEFAULT '',
                        travelTo TEXT NOT NULL DEFAULT '',
                        travelMode TEXT NOT NULL DEFAULT '',
                        daysCount INTEGER NOT NULL DEFAULT 0,
                        ratePerDay REAL NOT NULL DEFAULT 0.0,
                        amount REAL NOT NULL,
                        receiptImagePath TEXT,
                        status TEXT NOT NULL DEFAULT 'pending',
                        timestamp INTEGER NOT NULL,
                        isSynced INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }

        // Adds serverId and receiptUrl columns for backend sync
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE expenses ADD COLUMN serverId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE expenses ADD COLUMN receiptUrl TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "field_app_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}