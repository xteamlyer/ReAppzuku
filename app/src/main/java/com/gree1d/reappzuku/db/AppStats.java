package com.gree1d.reappzuku.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "app_stats",
    indices = {
        @Index(value = {"packageName"}),
        @Index(value = {"packageName", "lastKillTime"}),
        @Index(value = {"lastKillTime"})
    }
)
public class AppStats {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String packageName;

    public String appName;
    
    public int relaunchCount;

    public long totalRecoveredKb;
   
    public long lastKillTime;

    public long lastRelaunchTime;

    public String lastKillSource;

    public AppStats(@NonNull String packageName) {
        this.packageName = packageName;
    }
}
