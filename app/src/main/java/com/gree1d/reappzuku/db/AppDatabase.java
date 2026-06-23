package com.gree1d.reappzuku.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;

@Database(
    entities = {
        AppStats.class,
        ResourceSnapshot.class,
        BgRestrictionLog.class,
        SchedulerLog.class,
        SleepModeLog.class
    },
    version = 12,
    exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE app_stats ADD COLUMN totalRecoveredKb INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `resource_snapshots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `packageName` TEXT, `batteryMah` REAL NOT NULL, `ramMb` REAL NOT NULL, `cpuTimeMs` INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_resource_snapshots_packageName_timestamp` ON `resource_snapshots` (`packageName`, `timestamp`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_resource_snapshots_timestamp` ON `resource_snapshots` (`timestamp`)");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE resource_snapshots ADD COLUMN totalCpuJiffies INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE resource_snapshots ADD COLUMN activeCpuJiffies INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE resource_snapshots ADD COLUMN batteryLevelPct INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE resource_snapshots ADD COLUMN totalRawPwiBatch REAL NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `bg_restriction_log` (" +
                       "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                       "`timestamp` INTEGER NOT NULL, " +
                       "`packageName` TEXT, " +
                       "`action` TEXT, " +
                       "`outcome` TEXT, " +
                       "`detail` TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bg_restriction_log_packageName` ON `bg_restriction_log` (`packageName`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bg_restriction_log_timestamp` ON `bg_restriction_log` (`timestamp`)");

            db.execSQL("CREATE TABLE IF NOT EXISTS `scheduler_log` (" +
                       "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                       "`timestamp` INTEGER NOT NULL, " +
                       "`packageName` TEXT, " +
                       "`action` TEXT, " +
                       "`outcome` TEXT, " +
                       "`detail` TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduler_log_packageName` ON `scheduler_log` (`packageName`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduler_log_timestamp` ON `scheduler_log` (`timestamp`)");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `sleep_mode_log` (" +
                       "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                       "`timestamp` INTEGER NOT NULL, " +
                       "`packageName` TEXT, " +
                       "`action` TEXT, " +
                       "`outcome` TEXT)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_mode_log_packageName` ON `sleep_mode_log` (`packageName`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_mode_log_timestamp` ON `sleep_mode_log` (`timestamp`)");
        }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE app_stats ADD COLUMN lastKillSource TEXT");
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE sleep_mode_log ADD COLUMN method TEXT");
            db.execSQL("ALTER TABLE sleep_mode_log ADD COLUMN freezeType TEXT");
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE resource_snapshots ADD COLUMN minRamMb REAL NOT NULL DEFAULT 0.0");
            db.execSQL("ALTER TABLE resource_snapshots ADD COLUMN maxRamMb REAL NOT NULL DEFAULT 0.0");
            db.execSQL("ALTER TABLE resource_snapshots ADD COLUMN isTemporary INTEGER NOT NULL DEFAULT 1");
        }
    };

    /**
     * Миграция 11 → 12: пересоздаём таблицу app_stats.
     *
     * Изменения схемы:
     *  - PK: packageName TEXT → id INTEGER AUTOINCREMENT
     *  - packageName становится обычным NOT NULL столбцом
     *  - Удалён столбец killCount (теперь считается через COUNT(*) в DAO)
     *  - Добавлены индексы: packageName, (packageName, lastKillTime), lastKillTime
     *
     * Перенос данных:
     *  Каждая существующая строка (агрегат по пакету) превращается
     *  в одну kill-запись с relaunchCount и totalRecoveredKb «как есть».
     */
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 1. Создаём новую таблицу с autoincrement PK
            db.execSQL("CREATE TABLE IF NOT EXISTS `app_stats_new` (" +
                       "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                       "`packageName` TEXT NOT NULL, " +
                       "`appName` TEXT, " +
                       "`relaunchCount` INTEGER NOT NULL DEFAULT 0, " +
                       "`totalRecoveredKb` INTEGER NOT NULL DEFAULT 0, " +
                       "`lastKillTime` INTEGER NOT NULL DEFAULT 0, " +
                       "`lastRelaunchTime` INTEGER NOT NULL DEFAULT 0, " +
                       "`lastKillSource` TEXT)");

            // 2. Переносим существующие данные.
            //    killCount в старой схеме отсутствует как отдельная запись —
            //    каждая строка становится одной kill-записью.
            db.execSQL("INSERT INTO `app_stats_new` " +
                       "(`packageName`, `appName`, `relaunchCount`, `totalRecoveredKb`, " +
                       "`lastKillTime`, `lastRelaunchTime`, `lastKillSource`) " +
                       "SELECT `packageName`, `appName`, `relaunchCount`, `totalRecoveredKb`, " +
                       "`lastKillTime`, `lastRelaunchTime`, `lastKillSource` " +
                       "FROM `app_stats`");

            // 3. Удаляем старую таблицу
            db.execSQL("DROP TABLE `app_stats`");

            // 4. Переименовываем новую
            db.execSQL("ALTER TABLE `app_stats_new` RENAME TO `app_stats`");

            // 5. Создаём индексы для быстрого поиска по пакету и времени
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_stats_packageName` " +
                       "ON `app_stats` (`packageName`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_stats_packageName_lastKillTime` " +
                       "ON `app_stats` (`packageName`, `lastKillTime`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_stats_lastKillTime` " +
                       "ON `app_stats` (`lastKillTime`)");
        }
    };

    public abstract AppStatsDao appStatsDao();
    public abstract ResourceSnapshotDao resourceSnapshotDao();
    public abstract BgRestrictionLog.Dao bgRestrictionLogDao();
    public abstract SchedulerLog.Dao schedulerLogDao();
    public abstract SleepModeLog.Dao sleepModeLogDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    AppDatabase.class, "appzuku_db")
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12
                    )
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
