package com.gree1d.reappzuku.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

// Debug cmd: logcat -s ReAppzukuDebug

public final class AppDebugManager {

    public static final String LOG_TAG = "ReAppzukuDebug";

    private static final String PREFS_NAME      = "AppDebugPrefs";
    private static final String KEY_ENABLED     = "debug_enabled";
    private static final String KEY_CAT_PREFIX  = "debug_cat_";


    public enum Category {

        // UI
        MAIN_PAGE                    ("Main Page"),
        SETTINGS_PAGE              ("Settings Page"),
        STATISTICS_PAGE              ("Statistics Page"),
        
        // App Core
        CORE                           ("App Core"),

        // Features
        FOREGROUND_SERVICE         ("Foreground Service"),
        TRIGGERS                        ("Triggers"),
        ADVANCED_CONDITIONS        ("Advanced Conditions"),
        SCAN                            ("Scan"),
        AUTO_KILL_BASE                 ("Auto-Kill Base"),
        AUTO_KILL_PRESETS             ("Auto-Kill Presets"),
        SHORTCUTS_WIDGETS          ("Shortcuts & Widget"),
        BACKGROUND_RESTRICTIONS   ("Background Restrictions"),
        RESTRICTIONS_SCHEDULER     ("Restrictions Scheduler"),
        SLEEP_MODE                    ("SleepMode"),
        BACKUP_RESTORE              ("Backup & Restore"),
        UTILS                           ("Other utils");

        public final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        String prefKey() {
            return KEY_CAT_PREFIX + name();
        }
    }

    private static volatile AppDebugManager sInstance;

    private final SharedPreferences mPrefs;

    private AppDebugManager(Context context) {
        mPrefs = context.getApplicationContext()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void init(Context context) {
        if (sInstance == null) {
            synchronized (AppDebugManager.class) {
                if (sInstance == null) {
                    sInstance = new AppDebugManager(context);
                }
            }
        }
    }

    private static AppDebugManager get() {
        if (sInstance == null) {
            return null;
        }
        return sInstance;
    }

    public static void setEnabled(boolean enabled) {
        AppDebugManager m = get();
        if (m == null) return;
        m.mPrefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static boolean isEnabled() {
        AppDebugManager m = get();
        if (m == null) return false;
        return m.mPrefs.getBoolean(KEY_ENABLED, false);
    }

    public static void setCategory(Category category, boolean enabled) {
        AppDebugManager m = get();
        if (m == null) return;
        m.mPrefs.edit().putBoolean(category.prefKey(), enabled).apply();
    }

    public static boolean isCategoryEnabled(Category category) {
        AppDebugManager m = get();
        if (m == null) return false;
        return m.mPrefs.getBoolean(category.prefKey(), false);
    }

    public static void enableCategories(Category... categories) {
        AppDebugManager m = get();
        if (m == null) return;
        SharedPreferences.Editor editor = m.mPrefs.edit();
        for (Category cat : categories) {
            editor.putBoolean(cat.prefKey(), true);
        }
        editor.apply();
    }

    public static void disableAllCategories() {
        AppDebugManager m = get();
        if (m == null) return;
        SharedPreferences.Editor editor = m.mPrefs.edit();
        for (Category cat : Category.values()) {
            editor.putBoolean(cat.prefKey(), false);
        }
        editor.apply();
    }


    private static boolean shouldLog(Category category) {
        AppDebugManager m = get();
        if (m == null) return false;
        if (!m.mPrefs.getBoolean(KEY_ENABLED, false)) return false;
        return m.mPrefs.getBoolean(category.prefKey(), false);
    }

    private static String format(Category category, String message) {
        return category.displayName + ": " + message;
    }

    public static void v(Category category, String message) {
        if (shouldLog(category)) Log.v(LOG_TAG, format(category, message));
    }

    public static void d(Category category, String message) {
        if (shouldLog(category)) Log.d(LOG_TAG, format(category, message));
    }

    public static void i(Category category, String message) {
        if (shouldLog(category)) Log.i(LOG_TAG, format(category, message));
    }

    public static void w(Category category, String message) {
        if (shouldLog(category)) Log.w(LOG_TAG, format(category, message));
    }

    public static void e(Category category, String message) {
        if (shouldLog(category)) Log.e(LOG_TAG, format(category, message));
    }

    public static void e(Category category, String message, Throwable throwable) {
        if (shouldLog(category)) Log.e(LOG_TAG, format(category, message), throwable);
    }

    public static void w(Category category, String message, Throwable throwable) {
        if (shouldLog(category)) Log.w(LOG_TAG, format(category, message), throwable);
    }
}
