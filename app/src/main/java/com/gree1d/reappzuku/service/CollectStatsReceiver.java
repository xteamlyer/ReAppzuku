package com.gree1d.reappzuku.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.manager.CollectStatsManager;

public class CollectStatsReceiver extends BroadcastReceiver {

    private static final String FILE_NAME = "CollectStatsReceiver";

    public static final String ACTION_COLLECT_SNAPSHOT =
            "com.gree1d.reappzuku.COLLECT_SNAPSHOT";

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
        PowerManager.WakeLock wl = null;
        if (pm != null) {
            wl = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "reappzuku:CollectStatsReceiver");
            wl.setReferenceCounted(false);
            wl.acquire(WAKELOCK_TIMEOUT_MS);
        } else {
            AppDebugManager.w(Category.UTILS,
                    FILE_NAME + ": onReceive: PowerManager is null, WakeLock skipped");
        }

        final PowerManager.WakeLock finalWl = wl;
        try {
            ShellManager shellManager = new ShellManager(context);
            CollectStatsManager mgr = new CollectStatsManager(context, shellManager);
            mgr.takeSnapshotAsync(() -> {
                AppDebugManager.d(Category.UTILS,
                        FILE_NAME + ": snapshot completed, releasing WakeLock");
                if (finalWl != null && finalWl.isHeld()) finalWl.release();
            });
        } catch (Exception e) {
            AppDebugManager.e(Category.UTILS,
                    FILE_NAME + ": onReceive: failed to start snapshot", e);
            if (finalWl != null && finalWl.isHeld()) finalWl.release();
        }

        Intent serviceIntent = new Intent(context, ShappkyService.class);
        serviceIntent.setAction("RESCHEDULE_SNAPSHOT");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
