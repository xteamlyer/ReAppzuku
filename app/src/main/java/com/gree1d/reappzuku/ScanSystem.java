package com.gree1d.reappzuku;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanSystem {

    public enum Category {
        WAKELOCK, NETWORK, FGS, ALARM, SENSOR, LOCATION
    }

    public static class Finding {
        public final Category category;
        public final String   detail;

        public Finding(Category category, String detail) {
            this.category = category;
            this.detail   = detail;
        }
    }

    public static class AppLoad {
        public final String        packageName;
        public final String        appName;
        public final List<Finding> findings;

        public AppLoad(String packageName, String appName) {
            this.packageName = packageName;
            this.appName     = appName;
            this.findings    = new ArrayList<>();
        }
    }

    private static final long WINDOW_MS = 5 * 60 * 1000L;

    private final Context      context;
    private final ShellManager shellManager;

    public ScanSystem(Context context, ShellManager shellManager) {
        this.context      = context.getApplicationContext();
        this.shellManager = shellManager;
    }

    public List<AppLoad> scan(List<AppModel> apps) {
        Map<String, AppLoad> map = new LinkedHashMap<>();
        for (AppModel app : apps) {
            map.put(app.getPackageName(), new AppLoad(app.getPackageName(), app.getAppName()));
        }

        ExecutorService pool = Executors.newFixedThreadPool(6);

        Future<Map<String, List<Finding>>> fWakelocks = pool.submit(this::collectWakelocks);
        Future<Map<String, List<Finding>>> fNetwork   = pool.submit(this::collectNetwork);
        Future<Map<String, List<Finding>>> fFgs       = pool.submit(this::collectFgs);
        Future<Map<String, List<Finding>>> fAlarms    = pool.submit(this::collectAlarmWakeups);
        Future<Map<String, List<Finding>>> fSensors   = pool.submit(this::collectSensors);
        Future<Map<String, List<Finding>>> fLocation  = pool.submit(this::collectLocation);

        pool.shutdown();

        mergeInto(map, safeGet(fWakelocks));
        mergeInto(map, safeGet(fNetwork));
        mergeInto(map, safeGet(fFgs));
        mergeInto(map, safeGet(fAlarms));
        mergeInto(map, safeGet(fSensors));
        mergeInto(map, safeGet(fLocation));

        List<AppLoad> result = new ArrayList<>();
        for (AppLoad load : map.values()) {
            if (!load.findings.isEmpty()) result.add(load);
        }
        return result;
    }

    private void mergeInto(Map<String, AppLoad> map, Map<String, List<Finding>> data) {
        if (data == null) return;
        for (Map.Entry<String, List<Finding>> e : data.entrySet()) {
            AppLoad load = map.get(e.getKey());
            if (load != null) load.findings.addAll(e.getValue());
        }
    }

    private <T> T safeGet(Future<T> f) {
        try { return f.get(); } catch (Exception e) { return null; }
    }

    private Map<String, List<Finding>> collectWakelocks() {
        Map<String, List<Finding>> result = new LinkedHashMap<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput("dumpsys power");
            if (out == null) return result;

            boolean inSection = false;
            long nowElapsed = android.os.SystemClock.elapsedRealtime();

            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Wake Locks:"))        { inSection = true;  continue; }
                if (t.startsWith("Suspend Blockers:"))  { inSection = false; continue; }
                if (!inSection)                           continue;
                if (!t.contains("PARTIAL_WAKE_LOCK"))    continue;

                Pattern timePat = Pattern.compile("acquireTime=(\\d+)");
                Matcher mTime = timePat.matcher(t);
                if (mTime.find()) {
                    long acquireElapsed = Long.parseLong(mTime.group(1));
                    if (nowElapsed - acquireElapsed > WINDOW_MS) continue;
                }

                Pattern tagPat = Pattern.compile("tag=([^,\\s]+)");
                Matcher mTag = tagPat.matcher(t);
                String tag = mTag.find() ? mTag.group(1) : "";

                Pattern pkgPat = Pattern.compile("([a-z][\\w]*(?:\\.[a-z][\\w]*){1,})");
                Matcher mPkg = pkgPat.matcher(t);
                while (mPkg.find()) {
                    String pkg = mPkg.group(1);
                    if (result.containsKey(pkg) || pkg.contains(".")) {
                        addFinding(result, pkg, new Finding(Category.WAKELOCK,
                                tag.isEmpty() ? "PARTIAL_WAKE_LOCK" : tag));
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private Map<String, List<Finding>> collectNetwork() {
        Map<String, List<Finding>> result = new LinkedHashMap<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput("dumpsys netstats detail");
            if (out == null) return result;

            String currentPkg = null;
            long   totalRx    = 0;
            long   totalTx    = 0;

            Pattern uidLinePat  = Pattern.compile("^\\s*UID=?(\\d+)");
            Pattern pkgLinePat  = Pattern.compile("package=([\\w.]+)");
            Pattern bytesPat    = Pattern.compile("rxBytes=(\\d+).*?txBytes=(\\d+)");

            for (String line : out.split("\n")) {
                Matcher mPkg = pkgLinePat.matcher(line);
                if (mPkg.find()) {
                    if (currentPkg != null && totalRx + totalTx > 4096) {
                        addFinding(result, currentPkg, new Finding(Category.NETWORK,
                                "RX " + formatBytes(totalRx) + " / TX " + formatBytes(totalTx)));
                    }
                    currentPkg = mPkg.group(1);
                    totalRx = 0;
                    totalTx = 0;
                    continue;
                }
                if (currentPkg == null) continue;
                Matcher mBytes = bytesPat.matcher(line);
                if (mBytes.find()) {
                    totalRx += Long.parseLong(mBytes.group(1));
                    totalTx += Long.parseLong(mBytes.group(2));
                }
            }
            if (currentPkg != null && totalRx + totalTx > 4096) {
                addFinding(result, currentPkg, new Finding(Category.NETWORK,
                        "RX " + formatBytes(totalRx) + " / TX " + formatBytes(totalTx)));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private Map<String, List<Finding>> collectFgs() {
        Map<String, List<Finding>> result = new LinkedHashMap<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput("dumpsys activity services");
            if (out == null) return result;

            Pattern svcPat  = Pattern.compile("ServiceRecord\\{[^}]+\\s+([\\w.]+)/([\\w.$]+)");
            Pattern fgsPat  = Pattern.compile("isForeground=true");
            Pattern typePat = Pattern.compile("foregroundServiceType=(\\d+)");

            String  curPkg     = null;
            String  curSvc     = null;
            boolean isFg       = false;
            String  fgsTypeStr = null;

            for (String line : out.split("\n")) {
                String t = line.trim();
                Matcher mSvc = svcPat.matcher(t);
                if (mSvc.find()) {
                    if (curPkg != null && isFg) {
                        String detail = buildFgsDetail(curSvc, fgsTypeStr);
                        addFinding(result, curPkg, new Finding(Category.FGS, detail));
                    }
                    curPkg     = mSvc.group(1);
                    curSvc     = mSvc.group(2);
                    isFg       = false;
                    fgsTypeStr = null;
                    continue;
                }
                if (t.contains("isForeground=true")) isFg = true;
                Matcher mType = typePat.matcher(t);
                if (mType.find()) fgsTypeStr = mType.group(1);
            }
            if (curPkg != null && isFg) {
                addFinding(result, curPkg, new Finding(Category.FGS,
                        buildFgsDetail(curSvc, fgsTypeStr)));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private Map<String, List<Finding>> collectAlarmWakeups() {
        Map<String, List<Finding>> result = new LinkedHashMap<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput("dumpsys alarm");
            if (out == null) return result;

            boolean inStats = false;
            Pattern statPat = Pattern.compile(
                    "^\\s*([a-z][\\w]*(?:\\.[a-z][\\w]*){1,}).*?\\+(\\d+)ms.*?(\\d+)\\s+wakes",
                    Pattern.CASE_INSENSITIVE);
            Pattern altPat  = Pattern.compile(
                    "^\\s*([a-z][\\w]*(?:\\.[a-z][\\w]*){1,})\\s+running.*?wakeups=(\\d+)",
                    Pattern.CASE_INSENSITIVE);

            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Alarm Stats:") || t.startsWith("Top Alarms:")) {
                    inStats = true; continue;
                }
                if (!inStats) continue;
                if (t.isEmpty()) { inStats = false; continue; }

                Matcher m1 = statPat.matcher(line);
                if (m1.find()) {
                    int wakes = Integer.parseInt(m1.group(3));
                    if (wakes > 0) {
                        addFinding(result, m1.group(1), new Finding(Category.ALARM,
                                wakes + " wakeup" + (wakes > 1 ? "s" : "") +
                                ", +" + m1.group(2) + "ms runtime"));
                    }
                    continue;
                }
                Matcher m2 = altPat.matcher(line);
                if (m2.find()) {
                    int wakeups = Integer.parseInt(m2.group(2));
                    if (wakeups > 0) {
                        addFinding(result, m2.group(1), new Finding(Category.ALARM,
                                wakeups + " wakeup" + (wakeups > 1 ? "s" : "")));
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private Map<String, List<Finding>> collectSensors() {
        Map<String, List<Finding>> result = new LinkedHashMap<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput("dumpsys sensorservice");
            if (out == null) return result;

            boolean inActive  = false;
            Pattern pkgPat    = Pattern.compile("([a-z][\\w]*(?:\\.[a-z][\\w]*){1,})");
            Pattern periodPat = Pattern.compile("samplingPeriod=(\\d+)");
            Pattern namePat   = Pattern.compile("Sensor\\s*\\{([^}]+)\\}|type=(\\w+)");

            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Active sensors:") || t.startsWith("Listeners:") ||
                        t.startsWith("Active connections:")) {
                    inActive = true; continue;
                }
                if (inActive && t.isEmpty()) { inActive = false; continue; }
                if (!inActive) continue;

                Matcher mPeriod = periodPat.matcher(t);
                long period = -1;
                if (mPeriod.find()) period = Long.parseLong(mPeriod.group(1));
                if (period == 0) continue;

                Matcher mPkg = pkgPat.matcher(t);
                if (!mPkg.find()) continue;
                String pkg = mPkg.group(1);

                String sensorName = "";
                Matcher mName = namePat.matcher(t);
                if (mName.find()) {
                    sensorName = mName.group(1) != null ? mName.group(1).trim() : mName.group(2);
                }

                String detail = sensorName.isEmpty()
                        ? (period > 0 ? "period " + period + "μs" : "active")
                        : sensorName + (period > 0 ? ", period " + period + "μs" : "");
                addFinding(result, pkg, new Finding(Category.SENSOR, detail));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private Map<String, List<Finding>> collectLocation() {
        Map<String, List<Finding>> result = new LinkedHashMap<>();
        try {
            String out = shellManager.runShellCommandAndGetFullOutput("dumpsys location");
            if (out == null) return result;

            boolean inReceivers = false;
            Pattern pkgPat      = Pattern.compile("([a-z][\\w]*(?:\\.[a-z][\\w]*){1,})");
            Pattern intervalPat = Pattern.compile("interval=(\\d+)");
            Pattern accuracyPat = Pattern.compile("(HIGH_ACCURACY|BALANCED|LOW_POWER|NO_POWER|" +
                    "high|balanced|low|passive)", Pattern.CASE_INSENSITIVE);
            Pattern providerPat = Pattern.compile("provider=(\\w+)");

            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Active receivers:") || t.startsWith("GPS requests:") ||
                        t.startsWith("Location Listeners:") || t.startsWith("active clients:")) {
                    inReceivers = true; continue;
                }
                if (inReceivers && t.isEmpty()) { inReceivers = false; continue; }
                if (!inReceivers) continue;

                Matcher mInterval = intervalPat.matcher(t);
                if (mInterval.find() && Long.parseLong(mInterval.group(1)) == 0) continue;

                Matcher mPkg = pkgPat.matcher(t);
                if (!mPkg.find()) continue;
                String pkg = mPkg.group(1);

                String provider = "";
                Matcher mProv = providerPat.matcher(t);
                if (mProv.find()) provider = mProv.group(1);

                String accuracy = "";
                Matcher mAcc = accuracyPat.matcher(t);
                if (mAcc.find()) accuracy = mAcc.group(1).toUpperCase();

                String interval = mInterval.find() ? mInterval.group(1) + "ms" : "";

                StringBuilder detail = new StringBuilder();
                if (!provider.isEmpty())  detail.append(provider);
                if (!accuracy.isEmpty())  detail.append(detail.length() > 0 ? ", " : "").append(accuracy);
                if (!interval.isEmpty())  detail.append(detail.length() > 0 ? ", " : "").append("каждые ").append(interval);

                addFinding(result, pkg, new Finding(Category.LOCATION,
                        detail.length() > 0 ? detail.toString() : "active"));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private String buildFgsDetail(String svcClass, String typeStr) {
        StringBuilder sb = new StringBuilder();
        if (svcClass != null && !svcClass.isEmpty()) sb.append(svcClass);
        if (typeStr != null) {
            int type = 0;
            try { type = Integer.parseInt(typeStr); } catch (Exception ignored) {}
            String typeName = fgsTypeName(type);
            if (!typeName.isEmpty()) sb.append(sb.length() > 0 ? " · " : "").append(typeName);
        }
        return sb.length() > 0 ? sb.toString() : "foreground";
    }

    private String fgsTypeName(int type) {
        switch (type) {
            case 1:   return "dataSync";
            case 2:   return "mediaPlayback";
            case 4:   return "phoneCall";
            case 8:   return "location";
            case 16:  return "connectedDevice";
            case 32:  return "mediaProjection";
            case 64:  return "camera";
            case 128: return "microphone";
            case 256: return "health";
            case 512: return "remoteMessaging";
            case 1024:return "systemExempted";
            case 2048:return "shortService";
            default:  return type > 0 ? "type=" + type : "";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024)       return bytes + " B";
        if (bytes < 1048576)    return (bytes / 1024) + " KB";
        return (bytes / 1048576) + " MB";
    }

    private String resolvePkg(String uid) {
        try {
            String[] pkgs = context.getPackageManager()
                    .getPackagesForUid(Integer.parseInt(uid));
            if (pkgs != null && pkgs.length > 0) return pkgs[0];
        } catch (Exception ignored) {}
        return null;
    }

    private void addFinding(Map<String, List<Finding>> map, String pkg, Finding finding) {
        if (!map.containsKey(pkg)) map.put(pkg, new ArrayList<>());
        map.get(pkg).add(finding);
    }
}
