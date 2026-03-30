package com.company.fieldapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AttendanceEntity::class, ExpenseEntity::class],
    version = 7,                         // ← bumped from 6 → 7
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE expenses ADD COLUMN serverId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE expenses ADD COLUMN receiptUrl TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS attendance_new (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId      TEXT    NOT NULL DEFAULT '',
                        employeeId  TEXT    NOT NULL DEFAULT '',
                        selfiePath  TEXT    NOT NULL DEFAULT '',
                        latitude    REAL    NOT NULL DEFAULT 0.0,
                        longitude   REAL    NOT NULL DEFAULT 0.0,
                        address     TEXT    NOT NULL DEFAULT '',
                        timestamp   INTEGER NOT NULL DEFAULT 0,
                        isSynced    INTEGER NOT NULL DEFAULT 0
                    )
                """)
                try {
                    database.execSQL("""
                        INSERT INTO attendance_new
                            (id, userId, employeeId, selfiePath,
                             latitude, longitude, address, timestamp, isSynced)
                        SELECT
                            id,
                            COALESCE(userId,     ''),
                            COALESCE(employeeId, ''),
                            COALESCE(selfiePath, ''),
                            COALESCE(latitude,   0.0),
                            COALESCE(longitude,  0.0),
                            COALESCE(address,    ''),
                            COALESCE(timestamp,  0),
                            COALESCE(isSynced,   0)
                        FROM attendance
                    """)
                } catch (e: Exception) {
                    android.util.Log.w("AppDatabase",
                        "MIGRATION_5_6: could not copy rows — starting fresh. ${e.message}")
                }
                database.execSQL("DROP TABLE IF EXISTS attendance")
                database.execSQL("ALTER TABLE attendance_new RENAME TO attendance")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_attendance_synced ON attendance(isSynced, userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_attendance_user ON attendance(userId)")
            }
        }

        // ── v6 → v7 ───────────────────────────────────────────────────────────
        // Devices that received the v6 APK now have the correct table structure
        // but Room's expected schema hash still doesn't match because the @Entity
        // indices declaration was added. This migration drops and recreates the
        // table + indexes so the schema hash aligns with AttendanceEntity exactly.
        // All existing rows are preserved.
        // ─────────────────────────────────────────────────────────────────────
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS idx_attendance_synced")
                database.execSQL("DROP INDEX IF EXISTS idx_attendance_user")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS attendance_v7 (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId      TEXT    NOT NULL DEFAULT '',
                        employeeId  TEXT    NOT NULL DEFAULT '',
                        selfiePath  TEXT    NOT NULL DEFAULT '',
                        latitude    REAL    NOT NULL DEFAULT 0.0,
                        longitude   REAL    NOT NULL DEFAULT 0.0,
                        address     TEXT    NOT NULL DEFAULT '',
                        timestamp   INTEGER NOT NULL DEFAULT 0,
                        isSynced    INTEGER NOT NULL DEFAULT 0
                    )
                """)

                try {
                    database.execSQL("""
                        INSERT INTO attendance_v7
                            (id, userId, employeeId, selfiePath,
                             latitude, longitude, address, timestamp, isSynced)
                        SELECT
                            id,
                            COALESCE(userId,     ''),
                            COALESCE(employeeId, ''),
                            COALESCE(selfiePath, ''),
                            COALESCE(latitude,   0.0),
                            COALESCE(longitude,  0.0),
                            COALESCE(address,    ''),
                            COALESCE(timestamp,  0),
                            COALESCE(isSynced,   0)
                        FROM attendance
                    """)
                } catch (e: Exception) {
                    android.util.Log.w("AppDatabase",
                        "MIGRATION_6_7: row copy failed — starting fresh. ${e.message}")
                }

                database.execSQL("DROP TABLE IF EXISTS attendance")
                database.execSQL("ALTER TABLE attendance_v7 RENAME TO attendance")

                // Must match @Entity(indices=[...]) in AttendanceEntity exactly
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_attendance_synced ON attendance(isSynced, userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_attendance_user ON attendance(userId)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "field_app_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}