package com.gree1d.reappzuku.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;


public class CollectStatsReceiver extends BroadcastReceiver {

    private static final String FILE_NAME = "CollectStatsReceiver";

    public static final String ACTION_COLLECT_SNAPSHOT =
            "com.gree1d.reappzuku.COLLECT_SNAPSHOT";

    static final String WAKELOCK_TAG = "reappzuku:CollectStatsSnapshot";

    private static final long WAKELOCK_TIMEOUT_MS = 5_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_COLLECT_SNAPSHOT.equals(intent.getAction())) {
            AppDebugManager.w(Category.UTILS,
                    FILE_NAME + ": onReceive: unexpected intent, ignoring");
            return;
        }

        AppDebugManager.d(Category.UTILS,
                FILE_NAME + ": onReceive: snapshot alarm fired, acquiring WakeLock");

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
            wl.setReferenceCounted(false);
            wl.acquire(WAKELOCK_TIMEOUT_MS);
        } else {
            AppDebugManager.w(Category.UTILS,
                    FILE_NAME + ": onReceive: PowerManager is null, WakeLock skipped");
        }

        Intent serviceIntent = new Intent(context, ShappkyService.class);
        serviceIntent.setAction("TAKE_SNAPSHOT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
