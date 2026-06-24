package com.gree1d.reappzuku.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;

import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.service.ShappkyService;

public class KillShortcutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "KillShortcutActivity: onCreate, dispatching WIDGET_KILL to ShappkyService");
        Intent service = new Intent(this, ShappkyService.class);
        service.setAction("WIDGET_KILL");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        AppDebugManager.d(Category.SHORTCUTS_WIDGETS, "KillShortcutActivity: onCreate service started, finishing activity");
        finish();
    }
}
