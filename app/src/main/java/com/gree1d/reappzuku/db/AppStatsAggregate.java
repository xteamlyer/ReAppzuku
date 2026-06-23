package com.gree1d.reappzuku.db;

public class AppStatsAggregate {
    public String packageName;
    public String appName;

    public int killCount;

    public int relaunchCount;

    public long totalRecoveredKb;

    public long lastKillTime;

    public long lastRelaunchTime;

    public String lastKillSource;
}
