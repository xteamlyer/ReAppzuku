package com.gree1d.reappzuku.utils;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.R;

public class ShappkyQuickTile extends TileService {

    private ShellManager shellManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onTileAdded");
        TileService.requestListeningState(this, new ComponentName(this, ShappkyQuickTile.class));
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onStartListening");
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) {
            AppDebugManager.w(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: updateTileState tile is null, skipping");
            return;
        }

        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_force_stop));
        tile.setLabel(getString(R.string.tile_kill_app_label));
        tile.setContentDescription(getString(R.string.tile_kill_app_subtitle));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(getString(R.string.tile_kill_app_subtitle));
        }

        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: updateTileState tile updated to STATE_ACTIVE");
    }

    @Override
    public void onClick() {
        super.onClick();
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onClick");
        if (shellManager == null) {
            shellManager = new ShellManager(this, handler, executor);
            AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onClick ShellManager initialized");
        }

        executor.execute(() -> {
            if (!shellManager.resolveAnyShellPermission()) {
                AppDebugManager.w(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onClick no shell permission available");
                handler.post(() -> {
                    shellManager.checkShellPermissions();
                    Toast.makeText(this, "Shizuku or Root permission required", Toast.LENGTH_SHORT).show();
                    updateTileState();
                });
                return;
            }

            String packageName = null;

            String dumpOutput = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'");
            if (dumpOutput != null && !dumpOutput.isEmpty()) {
                packageName = extractPackageFromActivityDump(dumpOutput);
                AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onClick activity dump resolved pkg=" + packageName);
            }

            if (packageName == null) {
                String windowOutput = shellManager.runShellCommandAndGetFullOutput(
                        "dumpsys window | grep mCurrentFocus");
                if (windowOutput != null && !windowOutput.isEmpty()) {
                    packageName = extractPackageFromWindowDump(windowOutput);
                    AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onClick window dump resolved pkg=" + packageName);
                }
            }

            if (packageName == null) {
                String topOutput = shellManager.runShellCommandAndGetFullOutput("cmd activity get-top-activity");
                if (topOutput != null && topOutput.contains("ActivityRecord")) {
                    int start = topOutput.indexOf("u0 ");
                    if (start != -1) {
                        String sub = topOutput.substring(start + 3);
                        int slash = sub.indexOf("/");
                        if (slash != -1) {
                            packageName = sub.substring(0, slash).trim();
                        }
                    }
                }
                AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onClick get-top-activity resolved pkg=" + packageName);
            }

            if (packageName != null && !packageName.equals(getPackageName()) && !packageName.equals("com.android.systemui")) {
                shellManager.runShellCommand("cmd statusbar collapse", null);

                final String killedPackage = packageName;
                AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onClick killing foreground pkg=" + killedPackage);
                String cmd = "am force-stop " + killedPackage;
                shellManager.runShellCommand(cmd, () -> {
                    AppDebugManager.i(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onClick kill success pkg=" + killedPackage);
                    logKilledPackage(killedPackage);
                    handler.post(() -> {
                        Toast.makeText(this, "Killed: " + killedPackage, Toast.LENGTH_SHORT).show();
                        updateTileState();
                    });
                }, () -> {
                    AppDebugManager.w(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onClick kill failed pkg=" + killedPackage);
                    handler.post(() -> {
                        Toast.makeText(this, "Failed to kill: " + killedPackage, Toast.LENGTH_SHORT).show();
                        updateTileState();
                    });
                });
            } else {
                AppDebugManager.w(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onClick no killable foreground app found, resolved pkg=" + packageName);
                handler.post(() -> {
                    Toast.makeText(this, "No killable foreground app found", Toast.LENGTH_SHORT).show();
                    updateTileState();
                });
            }
        });
    }

    private String extractPackageFromActivityDump(String output) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.contains("com.android.systemui")) continue;

            String[] parts = line.trim().split("\\s+");
            for (String part : parts) {
                if (part.contains("/")) {
                    String potentialPkg = part.split("/")[0];
                    if (potentialPkg.contains(".") && Character.isLetter(potentialPkg.charAt(0))) {
                        return potentialPkg;
                    }
                }
            }
        }
        return null;
    }

    private String extractPackageFromWindowDump(String output) {
        if (output.contains("com.android.systemui")) return null;

        int slashIndex = output.indexOf("/");
        if (slashIndex > 0) {
            int start = slashIndex - 1;
            while (start > 0 && (Character.isLetterOrDigit(output.charAt(start - 1)) || output.charAt(start - 1) == '.')) {
                start--;
            }
            String potentialPkg = output.substring(start, slashIndex);
            if (potentialPkg.contains(".") && Character.isLetter(potentialPkg.charAt(0))) {
                return potentialPkg;
            }
        }
        return null;
    }

    private void logKilledPackage(String packageName) {
        executor.execute(() -> {
            try {
                com.gree1d.reappzuku.db.AppStatsDao appStatsDao =
                        com.gree1d.reappzuku.db.AppDatabase.getInstance(getApplicationContext()).appStatsDao();
                com.gree1d.reappzuku.db.AppStats stats = appStatsDao.getStats(packageName);
                String appName = resolveInstalledAppName(packageName);

                if (stats == null) {
                    stats = new com.gree1d.reappzuku.db.AppStats(packageName);
                    stats.appName = appName;
                    appStatsDao.insert(stats);
                    AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: logKilledPackage inserted new stats entry for pkg=" + packageName);
                } else if ((stats.appName == null || stats.appName.trim().isEmpty())
                        && appName != null && !appName.trim().isEmpty()) {
                    appStatsDao.updateAppName(packageName, appName);
                    AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: logKilledPackage updated appName for pkg=" + packageName + " name=" + appName);
                }

                appStatsDao.incrementKill(packageName, System.currentTimeMillis(), "Quick Tile");
                AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: logKilledPackage kill incremented for pkg=" + packageName);
            } catch (Exception e) {
                AppDebugManager.e(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: logKilledPackage failed for pkg=" + packageName, e);
            }
        });
    }

    private String resolveInstalledAppName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(appInfo);
            if (label != null) {
                return label.toString();
            }
        } catch (PackageManager.NameNotFoundException e) {
            AppDebugManager.w(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: resolveInstalledAppName package not found pkg=" + packageName);
        }
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyQuickTile: onDestroy, shutting down executor");
        executor.shutdownNow();
    }
}
