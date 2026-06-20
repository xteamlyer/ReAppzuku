package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.utils.AppModel;

import static com.gree1d.reappzuku.core.PreferenceKeys.*;
import com.gree1d.reappzuku.utils.SleepModeLogManager;
import com.gree1d.reappzuku.core.ProtectedApps;

public class SleepModeManager {
    private static final String TAG = "SleepModeManager";

    public enum FreezeType {
        TIMER,
        PERMANENT
    }

    public enum FreezeMethod {
        SUSPEND,
        DISABLE
    }

    private final Context context;
    private final Handler handler;
    private final ExecutorService executor;
    private final ShellManager shellManager;
    private final SharedPreferences sharedpreferences;
    private RestrictionsScheduler scheduler;
    private final Set<String> systemPackages = new HashSet<>();

    public SleepModeManager(Context context, Handler handler, ExecutorService executor,
            ShellManager shellManager) {
        this.context = context;
        this.handler = handler;
        this.executor = executor;
        this.shellManager = shellManager;
        this.sharedpreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public void setScheduler(RestrictionsScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public Set<String> getSleepModeApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_SLEEP_MODE_APPS, new HashSet<>()));
    }

    public Set<String> getPermanentFreezeApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_SLEEP_MODE_APPS_PERMANENT, new HashSet<>()));
    }

    public FreezeType getFreezeType(String packageName) {
        if (getPermanentFreezeApps().contains(packageName)) return FreezeType.PERMANENT;
        if (getSleepModeApps().contains(packageName)) return FreezeType.TIMER;
        return null;
    }

    public Set<String> getFrozenTimerApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_SLEEP_MODE_APPS_FROZEN, new HashSet<>()));
    }

    public Set<String> getSuspendMethodApps() {
        return new HashSet<>(sharedpreferences.getStringSet(KEY_SLEEP_MODE_APPS_SUSPEND_METHOD, new HashSet<>()));
    }

    public FreezeMethod getFreezeMethod(String packageName) {
        if (isSystemPackage(packageName) || systemPackages.contains(packageName)) {
            return FreezeMethod.SUSPEND;
        }
        return getSuspendMethodApps().contains(packageName) ? FreezeMethod.SUSPEND : FreezeMethod.DISABLE;
    }

    public void setFreezeMethod(String packageName, FreezeMethod method) {
        if (isSystemPackage(packageName) || systemPackages.contains(packageName)) return;
        Set<String> suspendApps = getSuspendMethodApps();
        applyMethodToSet(suspendApps, packageName, method);
        sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS_SUSPEND_METHOD, suspendApps).apply();
    }

    private void applyMethodToSet(Set<String> suspendApps, String packageName, FreezeMethod method) {
        if (method == FreezeMethod.SUSPEND) {
            suspendApps.add(packageName);
        } else {
            suspendApps.remove(packageName);
        }
    }

    private void markFrozen(String packageName) {
        Set<String> frozen = getFrozenTimerApps();
        frozen.add(packageName);
        sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS_FROZEN, frozen).apply();
    }

    private void markUnfrozen(String packageName) {
        Set<String> frozen = getFrozenTimerApps();
        frozen.remove(packageName);
        sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS_FROZEN, frozen).apply();
    }

    public boolean isSystemPackage(String packageName) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public boolean reapplyPermanentFreeze(String packageName) {
        return freezeApp(packageName);
    }

    private boolean freezeApp(String packageName) {
        return freezeAppWithMethod(packageName, getFreezeMethod(packageName));
    }

    private boolean unfreezeApp(String packageName) {
        return unfreezeAppWithMethod(packageName, getFreezeMethod(packageName));
    }

    private boolean freezeAppWithMethod(String packageName, FreezeMethod method) {
        if (method == FreezeMethod.SUSPEND) {
            return shellManager.runShellCommandBlocking("pm suspend --user 0 " + packageName);
        }
        return shellManager.runShellCommandBlocking("pm disable-user --user 0 " + packageName);
    }

    private boolean unfreezeAppWithMethod(String packageName, FreezeMethod method) {
        if (method == FreezeMethod.SUSPEND) {
            return shellManager.runShellCommandBlocking("pm unsuspend --user 0 " + packageName);
        }
        return shellManager.runShellCommandBlocking("pm enable " + packageName);
    }

    public void saveSleepModeApps(Set<String> timerPackages, Set<String> permanentPackages,
            Map<String, FreezeMethod> newMethods, Runnable onComplete) {
        Set<String> previousPermanent = getPermanentFreezeApps();
        Set<String> previousTimer = getSleepModeApps();
        Set<String> previousFrozenTimer = getFrozenTimerApps();

        Map<String, FreezeMethod> oldMethods = new java.util.HashMap<>();
        Set<String> allTouched = new HashSet<>();
        allTouched.addAll(previousPermanent);
        allTouched.addAll(previousTimer);
        allTouched.addAll(permanentPackages);
        allTouched.addAll(timerPackages);
        for (String packageName : allTouched) {
            oldMethods.put(packageName, getFreezeMethod(packageName));
        }

        sharedpreferences.edit()
                .putStringSet(KEY_SLEEP_MODE_APPS, new HashSet<>(timerPackages))
                .putStringSet(KEY_SLEEP_MODE_APPS_PERMANENT, new HashSet<>(permanentPackages))
                .apply();

        if (newMethods != null) {
            Set<String> suspendApps = getSuspendMethodApps();
            for (Map.Entry<String, FreezeMethod> entry : newMethods.entrySet()) {
                applyMethodToSet(suspendApps, entry.getKey(), entry.getValue());
            }
            sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS_SUSPEND_METHOD, suspendApps).apply();
        }

        Set<String> removedFromTimer = new HashSet<>(previousTimer);
        removedFromTimer.removeAll(timerPackages);
        if (!removedFromTimer.isEmpty()) {
            Set<String> frozen = getFrozenTimerApps();
            frozen.removeAll(removedFromTimer);
            sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS_FROZEN, frozen).apply();
        }

        Map<String, FreezeMethod> finalOldMethods = oldMethods;
        executor.execute(() -> {
            for (String packageName : allTouched) {
                FreezeType oldType = previousPermanent.contains(packageName) ? FreezeType.PERMANENT
                        : previousTimer.contains(packageName) ? FreezeType.TIMER : null;
                FreezeType newType = permanentPackages.contains(packageName) ? FreezeType.PERMANENT
                        : timerPackages.contains(packageName) ? FreezeType.TIMER : null;
                FreezeMethod oldMethod = finalOldMethods.get(packageName);
                FreezeMethod newMethod = getFreezeMethod(packageName);

                boolean wasPhysicallyFrozen = oldType == FreezeType.PERMANENT
                        || (oldType == FreezeType.TIMER && previousFrozenTimer.contains(packageName));
                boolean shouldBePhysicallyFrozen = newType == FreezeType.PERMANENT;

                if (!wasPhysicallyFrozen && !shouldBePhysicallyFrozen) continue;

                boolean methodChanged = oldMethod != newMethod;
                boolean needsUnfreeze = wasPhysicallyFrozen && (!shouldBePhysicallyFrozen || methodChanged);
                boolean needsFreeze = shouldBePhysicallyFrozen && (!wasPhysicallyFrozen || methodChanged);

                if (needsUnfreeze) {
                    boolean ok = unfreezeAppWithMethod(packageName, oldMethod);
                    SleepModeLogManager.logUnfreeze(context, packageName, ok, oldMethod, oldType);
                    if (oldType == FreezeType.TIMER) markUnfrozen(packageName);
                }
                if (needsFreeze) {
                    boolean ok = freezeAppWithMethod(packageName, newMethod);
                    SleepModeLogManager.logFreeze(context, packageName, ok, newMethod, newType);
                }
            }
            if (onComplete != null) handler.post(onComplete);
        });
    }

    public void saveSleepModeApps(Set<String> timerPackages, Set<String> permanentPackages,
            Runnable onComplete) {
        saveSleepModeApps(timerPackages, permanentPackages, null, onComplete);
    }

    public void saveSleepModeApps(Set<String> packages) {
        sharedpreferences.edit().putStringSet(KEY_SLEEP_MODE_APPS, new HashSet<>(packages)).apply();
    }

    public boolean isSleepModeEnabled() {
        return sharedpreferences.getBoolean(KEY_SLEEP_MODE_ENABLED, false);
    }

    public void setSleepModeEnabled(boolean enabled) {
        sharedpreferences.edit().putBoolean(KEY_SLEEP_MODE_ENABLED, enabled).apply();
    }

    public void loadSleepModeApps(Consumer<List<AppModel>> callback) {
        executor.execute(() -> {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            Set<String> timerApps = getSleepModeApps();
            Set<String> permanentApps = getPermanentFreezeApps();
            List<AppModel> result = new ArrayList<>();
            systemPackages.clear();
            for (ApplicationInfo appInfo : packages) {
                if (appInfo.packageName.equals(context.getPackageName())) continue;
                if (ProtectedApps.isProtected(context, appInfo.packageName)) continue;
                if ((appInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) continue;
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    systemPackages.add(appInfo.packageName);
                }
                boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                AppModel model = new AppModel(
                        pm.getApplicationLabel(appInfo).toString(),
                        appInfo.packageName,
                        "-",
                        0,
                        pm.getApplicationIcon(appInfo),
                        isSystem,
                        (appInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0,
                        ProtectedApps.isProtected(context, appInfo.packageName));
                boolean selected = timerApps.contains(appInfo.packageName)
                        || permanentApps.contains(appInfo.packageName);
                model.setSelected(selected);
                if (permanentApps.contains(appInfo.packageName)) {
                    model.setFreezeType(FreezeType.PERMANENT);
                } else if (timerApps.contains(appInfo.packageName)) {
                    model.setFreezeType(FreezeType.TIMER);
                }
                result.add(model);
            }
            handler.post(() -> callback.accept(result));
        });
    }

    public void freezeBackgroundRestrictedApps(Runnable onComplete) {
        Set<String> packages = getSleepModeApps();
        if (packages.isEmpty()) {
            if (onComplete != null) handler.post(onComplete);
            return;
        }
        executor.execute(() -> {
            Set<String> alreadyFrozen = getFrozenTimerApps();
            for (String packageName : packages) {
                if (alreadyFrozen.contains(packageName)) continue;
                if (scheduler != null && scheduler.isProtected(packageName, RestrictionsScheduler.PROTECT_SLEEP_MODE)) continue;
                FreezeMethod method = getFreezeMethod(packageName);
                boolean ok = freezeApp(packageName);
                if (ok) markFrozen(packageName);
                SleepModeLogManager.logFreeze(context, packageName, ok, method, FreezeType.TIMER);
            }
            if (onComplete != null) handler.post(onComplete);
        });
    }

    public void unfreezeBackgroundRestrictedApps(Runnable onComplete) {
        Set<String> packages = getSleepModeApps();
        if (packages.isEmpty()) {
            if (onComplete != null) handler.post(onComplete);
            return;
        }
        executor.execute(() -> {
            for (String packageName : packages) {
                FreezeMethod method = getFreezeMethod(packageName);
                boolean ok = unfreezeApp(packageName);
                if (ok) markUnfrozen(packageName);
                SleepModeLogManager.logUnfreeze(context, packageName, ok, method, FreezeType.TIMER);
            }
            if (onComplete != null) handler.post(onComplete);
        });
    }
}
