package com.gree1d.reappzuku.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AppStatsDao {

    // -------------------------------------------------------------------------
    // Вставка
    // -------------------------------------------------------------------------

    /** Вставляет новую kill-запись. Возвращает присвоенный id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AppStats stats);

    // -------------------------------------------------------------------------
    // Одиночные записи
    // -------------------------------------------------------------------------

    /**
     * Последняя kill-запись конкретного пакета.
     * Используется в recordSuccessfulKills() для проверки наличия appName.
     */
    @Query("SELECT * FROM app_stats WHERE packageName = :packageName ORDER BY lastKillTime DESC LIMIT 1")
    AppStats getLatestByPackage(String packageName);

    // -------------------------------------------------------------------------
    // Агрегатные запросы для UI-статистики
    // -------------------------------------------------------------------------

    /**
     * Все пакеты с агрегированными данными за указанный период.
     * killCount  = COUNT(*) kill-записей пакета за период.
     * relaunchCount = SUM(relaunchCount) по всем записям пакета за период.
     * totalRecoveredKb = SUM суммарно освобождённой RAM за период.
     * lastKillTime / lastRelaunchTime / lastKillSource — последние значения.
     *
     * Используется для экранов "12 ч / 24 ч / 7 дней" и Top Offenders.
     * Возвращает AppStatsAggregate, а не AppStats.
     */
    @Query("SELECT packageName, appName, " +
           "COUNT(*) AS killCount, " +
           "SUM(relaunchCount) AS relaunchCount, " +
           "SUM(totalRecoveredKb) AS totalRecoveredKb, " +
           "MAX(lastKillTime) AS lastKillTime, " +
           "MAX(lastRelaunchTime) AS lastRelaunchTime, " +
           "MAX(lastKillSource) AS lastKillSource " +
           "FROM app_stats " +
           "WHERE lastKillTime > :sinceTime " +
           "GROUP BY packageName " +
           "ORDER BY killCount DESC")
    List<AppStatsAggregate> getAllStatsSince(long sinceTime);

    /**
     * Все пакеты с агрегированными данными за всё время.
     * Используется для Top Offenders без фильтра по времени.
     * Возвращает AppStatsAggregate, а не AppStats.
     */
    @Query("SELECT packageName, appName, " +
           "COUNT(*) AS killCount, " +
           "SUM(relaunchCount) AS relaunchCount, " +
           "SUM(totalRecoveredKb) AS totalRecoveredKb, " +
           "MAX(lastKillTime) AS lastKillTime, " +
           "MAX(lastRelaunchTime) AS lastRelaunchTime, " +
           "MAX(lastKillSource) AS lastKillSource " +
           "FROM app_stats " +
           "GROUP BY packageName " +
           "ORDER BY killCount DESC")
    List<AppStatsAggregate> getAllStats();

    // -------------------------------------------------------------------------
    // Обновление полей (только последняя запись пакета)
    // -------------------------------------------------------------------------

    /**
     * Инкрементирует relaunchCount и обновляет lastRelaunchTime
     * только у самой свежей kill-записи пакета.
     */
    @Query("UPDATE app_stats SET " +
           "relaunchCount = relaunchCount + 1, " +
           "lastRelaunchTime = :time " +
           "WHERE id = (" +
               "SELECT id FROM app_stats " +
               "WHERE packageName = :packageName " +
               "ORDER BY lastKillTime DESC LIMIT 1" +
           ")")
    void incrementRelaunch(String packageName, long time);

    /**
     * Добавляет освобождённую RAM (KB) к последней kill-записи пакета.
     * Вызывается при подтверждении deferred RSS из pendingRss.
     */
    @Query("UPDATE app_stats SET totalRecoveredKb = totalRecoveredKb + :recoveredKb " +
           "WHERE id = (" +
               "SELECT id FROM app_stats " +
               "WHERE packageName = :packageName " +
               "ORDER BY lastKillTime DESC LIMIT 1" +
           ")")
    void addRecoveredKb(String packageName, long recoveredKb);

    /**
     * Обновляет appName у всех записей пакета, где имя пустое.
     */
    @Query("UPDATE app_stats SET appName = :appName " +
           "WHERE packageName = :packageName AND (appName IS NULL OR appName = '')")
    void updateAppName(String packageName, String appName);

    // -------------------------------------------------------------------------
    // Счётчики и очистка
    // -------------------------------------------------------------------------

    /** Общее количество kill-записей в таблице. */
    @Query("SELECT COUNT(*) FROM app_stats")
    int getCount();

    /**
     * Удаляет :deleteCount самых старых записей по lastKillTime.
     * Вызывается перед INSERT при достижении лимита STATS_LIMIT.
     */
    @Query("DELETE FROM app_stats WHERE id IN (" +
           "SELECT id FROM app_stats ORDER BY lastKillTime ASC LIMIT :deleteCount)")
    void deleteOldestStats(int deleteCount);

    /** Полная очистка таблицы. */
    @Query("DELETE FROM app_stats")
    void deleteAll();
}
