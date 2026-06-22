package com.gree1d.reappzuku.utils;

import android.content.ComponentName;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.manager.AutoKillManager;
import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.manager.BackgroundAppManager;

public class ShappkyBackgroundKillTile extends TileService {

    private ShellManager shellManager;
    private AutoKillManager backgroundAppManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyBackgroundKillTile: onTileAdded");
        TileService.requestListeningState(this, new ComponentName(this, ShappkyBackgroundKillTile.class));
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyBackgroundKillTile: onStartListening");
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) {
            AppDebugManager.w(Category.SHORTCUTS_WIDGETS, "ShappkyBackgroundKillTile: updateTileState tile is null, skipping");
            return;
        }

        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_force_stop));
        tile.setLabel(getString(R.string.tile_background_kill_label));
        tile.setContentDescription(getString(R.string.tile_background_kill_subtitle));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(getString(R.string.tile_background_kill_subtitle));
        }

        tile.setState(Tile.STATE_ACTIVE);
        tile.updateTile();
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyBackgroundKillTile: updateTileState tile updated to STATE_ACTIVE");
    }

    @Override
    public void onClick() {
        super.onClick();
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyBackgroundKillTile: onClick");
        if (shellManager == null) {
            shellManager = new ShellManager(this, handler, executor);
            AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyBackgroundKillTile: onClick ShellManager initialized");
        }
        if (backgroundAppManager == null) {
            BackgroundAppManager appManager = new BackgroundAppManager(this, handler, executor, shellManager);
            backgroundAppManager = new AutoKillManager(this, handler, executor, shellManager, appManager.getCurrentAppsList());
            AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyBackgroundKillTile: onClick AutoKillManager initialized");
        }

        executor.execute(() -> {
            if (!shellManager.resolveAnyShellPermission()) {
                AppDebugManager.w(Category.SHORTCUTS_WIDGETS, "ShappkyBackgroundKillTile: onClick no shell permission available");
                handler.post(() -> {
                    shellManager.checkShellPermissions();
                    Toast.makeText(this, "Shizuku or Root permission required", Toast.LENGTH_SHORT).show();
                    updateTileState();
                });
                return;
            }

            AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyBackgroundKillTile: onClick starting performAutoKill");
            backgroundAppManager.performAutoKill(() -> {
                AppDebugManager.i(Category.SHORTCUTS_WIDGETS, "ShappkyBackgroundKillTile: onClick performAutoKill finished");
                Toast.makeText(this, "Configured background kill finished", Toast.LENGTH_SHORT).show();
                updateTileState();
            }, "Quick Tile");
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "ShappkyBackgroundKillTile: onDestroy, shutting down executor");
        executor.shutdownNow();
    }
}
