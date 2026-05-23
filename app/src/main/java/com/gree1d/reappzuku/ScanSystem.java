package com.gree1d.reappzuku;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;

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

        Future<Map<String, List<Finding>>> fWakelocks = pool.submit(() -> collectWakelocks(map));
        Future<Map<String, List<Finding>>> fNetwork   = pool.submit(() -> collectNetwork(map));
        Future<Map<String, List<Finding>>> fFgs       = pool.submit(() -> collectFgs(map));
        Future<Map<String, List<Finding>>> fAlarms    = pool.submit(() -> collectAlarms(map));
        Future<Map<String, List<Finding>>> fSensors   = pool.submit(() -> collectSensors(map));
        Future<Map<String, List<Finding>>> fLocation  = pool.submit(() -> collectLocation(map));

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

    private Map<String, List<Finding>> collectWakelocks(Map<String, AppLoad> knownApps) {
        Map<String, List<Finding>> result = new LinkedHashMap<>();
        String out = shellManager.runShellCommandAndGetFullOutput("dumpsys power");
        if (out == null) return result;

        StringBuilder wlBlock = new StringBuilder();
        boolean inSection = false;
        for (String line : out.split("\n")) {
            if (line.trim().startsWith("Wake Locks:"))           { inSection = true;  continue; }
            if (inSection && line.trim().startsWith("Suspend Blockers:")) break;
            if (inSection) wlBlock.append(line).append("\n");
        }
        if (wlBlock.length() == 0) return result;

        Pattern tagPat    = Pattern.compile("'([^']{1,60})'");
        Pattern heldMsPat = Pattern.compile("held=(\\d+)ms");
        Pattern heldLeg   = Pattern.compile("(\\d+m\\s*\\d+s|\\d+s)");
        Pattern acqPat    = Pattern.compile("\\bacq(?:uire)?[=:](\\d+)");
        Pattern relPat    = Pattern.compile("\\brel(?:ease)?[=:](\\d+)");

        for (String line : wlBlock.toString().split("\n")) {
            String matchedPkg = null;
            for (String pkg : knownApps.keySet()) {
                String uid = resolveUid(pkg);
                if ((uid != null && line.contains("uid=" + uid)) || line.contains(pkg)) {
                    matchedPkg = pkg;
                    break;
                }
            }
            if (matchedPkg == null) continue;

            String typeLabel;
            if      (line.contains("PARTIAL"))   typeLabel = "Partial";
            else if (line.contains("FULL"))       typeLabel = "Full";
            else if (line.contains("SCREEN"))     typeLabel = "Screen";
            else if (line.contains("PROXIMITY"))  typeLabel = "Proximity";
            else                                  typeLabel = "WakeLock";

            String tag = "";
            Matcher mTag = tagPat.matcher(line);
            if (mTag.find()) tag = mTag.group(1);

            String heldStr = "";
            Matcher mHeldMs = heldMsPat.matcher(line);
            if (mHeldMs.find()) {
                heldStr = formatDuration(Long.parseLong(mHeldMs.group(1)));
            } else {
                Matcher mLeg = heldLeg.matcher(line);
                if (mLeg.find()) heldStr = mLeg.group(1);
            }

            String acqRel = "";
            Matcher mAcq = acqPat.matcher(line);
            Matcher mRel = relPat.matcher(line);
            if (mAcq.find() && mRel.find()) {
                acqRel = "acq=" + mAcq.group(1) + " rel=" + mRel.group(1);
            }

            StringBuilder detail = new StringBuilder(typeLabel);
            if (!tag.isEmpty())     detail.append(" · ").append(tag);
            if (!heldStr.isEmpty()) detail.append(" · held ").append(heldStr);
            if (!acqRel.isEmpty())  detail.append(" · ").append(acqRel);

            addFinding(result, matchedPkg, new Finding(Category.WAKELOCK, detail.toString()));
        }
        return result;
    }

    private Map<String, List<Finding>> collectNetwork(Map<String, AppLoad> knownApps) {
        Map<String, List<Finding>> result = new LinkedHashMap<>();
        String out = shellManager.runShellCommandAndGetFullOutput("dumpsys netstats detail");
        if (out == null) return result;

        Pattern pkgPat   = Pattern.compile("package=([\\w.]+)");
        Pattern bytesPat = Pattern.compile("rxBytes=(\\d+).*?txBytes=(\\d+)");

        String currentPkg = null;
        long   totalRx    = 0, totalTx = 0;

        for (String line : out.split("\n")) {
            Matcher mPkg = pkgPat.matcher(line);
            if (mPkg.find()) {
                if (currentPkg != null && knownApps.containsKey(currentPkg)
                        && totalRx + totalTx > 10 * 1024) {
                    addFinding(result, currentPkg, new Finding(Category.NETWORK,
                            "RX " + formatBytes(totalRx) + " / TX " + formatBytes(totalTx)));
                }
                currentPkg = mPkg.group(1);
                totalRx = 0; totalTx = 0;
                continue;
            }
            if (currentPkg == null) continue;
            Matcher mBytes = bytesPat.matcher(line);
            if (mBytes.find()) {
                totalRx += Long.parseLong(mBytes.group(1));
                totalTx += Long.parseLong(mBytes.group(2));
            }
        }
        if (currentPkg != null && knownApps.containsKey(currentPkg)
                && totalRx + totalTx > 10 * 1024) {
            addFinding(result, currentPkg, new Finding(Category.NETWORK,
                    "RX " + formatBytes(totalRx) + " / TX " + formatBytes(totalTx)));
        }

        for (String pkg : knownApps.keySet()) {
            if (result.containsKey(pkg)) continue;
            String uid = resolveUid(pkg);
            if (uid == null) continue;
            try {
                String stats = shellManager.runShellCommandAndGetFullOutput(
                        "cat /proc/net/xt_qtaguid/stats | grep \" " + uid + " \"");
                if (stats == null || stats.trim().isEmpty()) continue;
                long rx = 0, tx = 0;
                for (String line : stats.split("\n")) {
                    String[] cols = line.trim().split("\\s+");
                    if (cols.length > 6) {
                        try { rx += Long.parseLong(cols[5]); } catch (Exception ignored) {}
                        try { tx += Long.parseLong(cols[7]); } catch (Exception ignored) {}
                    }
                }
                if (rx + tx > 10 * 1024) {
                    addFinding(result, pkg, new Finding(Category.NETWORK,
                            "RX " + formatBytes(rx) + " / TX " + formatBytes(tx)));
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private Map<String, List<Finding>> collectFgs(Map<String, AppLoad> knownApps) {
        Map<String, List<Finding>> result = new LinkedHashMap<>();
        String out = shellManager.runShellCommandAndGetFullOutput("dumpsys activity services");
        if (out == null) return result;

        Pattern svcPat  = Pattern.compile(
                "ServiceRecord\\{[^}]+\\s+([\\w.]+)/([\\w.$]+)");
        Pattern typePat = Pattern.compile("foregroundServiceType=(\\d+)");
        Pattern chanPat = Pattern.compile("channelId=([\\w.\\-]+)");
        Pattern killPat = Pattern.compile("allowOomManagement=(true|false)");

        String  curPkg  = null, curSvc = null, curChan = null, curType = null;
        boolean isFg    = false;
        boolean killable = false;

        for (String line : out.split("\n")) {
            String t = line.trim();
            Matcher mSvc = svcPat.matcher(t);
            if (mSvc.find()) {
                if (curPkg != null && isFg && knownApps.containsKey(curPkg)) {
                    addFinding(result, curPkg, new Finding(Category.FGS,
                            buildFgsDetail(curSvc, curType, curChan, killable)));
                }
                curPkg   = mSvc.group(1);
                curSvc   = mSvc.group(2);
                isFg     = false;
                curType  = null;
                curChan  = null;
                killable = false;
                continue;
            }
            if (t.contains("isForeground=true")) isFg = true;
            Matcher mType = typePat.matcher(t);
            if (mType.find() && curType == null) curType = mType.group(1);
            Matcher mChan = chanPat.matcher(t);
            if (mChan.find() && curChan == null) curChan = mChan.group(1);
            Matcher mKill = killPat.matcher(t);
            if (mKill.find()) killable = "true".equals(mKill.group(1));
        }
        if (curPkg != null && isFg && knownApps.containsKey(curPkg)) {
            addFinding(result, curPkg, new Finding(Category.FGS,
                    buildFgsDetail(curSvc, curType, curChan, killable)));
        }
        return result;
    }

    private Map<String, List<Finding>> collectAlarms(Map<String, AppLoad> knownApps) {
        Map<String, List<Finding>> result = new LinkedHashMap<>();
        String out = shellManager.runShellCommandAndGetFullOutput("dumpsys alarm");
        if (out == null) return result;

        AppTriggersAnalyzer.AlarmDumpsysParser parser =
                new AppTriggersAnalyzer.AlarmDumpsysParser();

        Pattern topPat = Pattern.compile(
                "(\\S+)\\s+running,\\s*(\\d+)\\s+wakeups?,\\s*(\\d+)\\s+alarms?:\\s*\\d+:([\\w.]+)\\s+tag=(\\S+)");
        boolean inTop = false;
        Map<String, int[]> wakeupCounts = new LinkedHashMap<>();

        for (String line : out.split("\n")) {
            String t = line.trim();
            if (t.startsWith("Top Alarms:") || t.startsWith("Top alarm senders:") ||
                    t.startsWith("Alarm Stats:")) {
                inTop = true; continue;
            }
            if (inTop && (t.isEmpty() || (t.endsWith(":") && !t.startsWith("+")))) {
                inTop = false; continue;
            }
            if (!inTop) continue;
            Matcher m = topPat.matcher(t);
            if (!m.find()) continue;
            String pkg     = m.group(4);
            int    wakeups = Integer.parseInt(m.group(2));
            if (!knownApps.containsKey(pkg) || wakeups == 0) continue;
            wakeupCounts.put(pkg, new int[]{wakeups});
        }

        for (Map.Entry<String, int[]> e : wakeupCounts.entrySet()) {
            String pkg     = e.getKey();
            int    wakeups = e.getValue()[0];
            List<AppTriggersAnalyzer.AlarmEntry> entries = parser.parseEntries(out, pkg);
            StringBuilder sb = new StringBuilder(wakeups + " wakeup" + (wakeups > 1 ? "s" : ""));
            for (AppTriggersAnalyzer.AlarmEntry ae : entries) {
                if (!ae.isWakeup) continue;
                String shortTag = ae.tag != null
                        ? ae.tag.replaceAll(".*\\.([^.]+)$", "$1") : "";
                if (!shortTag.isEmpty()) sb.append(" · ").append(shortTag);
                if (ae.intervalMs > 0) sb.append(" @").append(formatInterval(ae.intervalMs));
                break;
            }
            addFinding(result, pkg, new Finding(Category.ALARM, sb.toString()));
        }
        return result;
    }

    private Map<String, List<Finding>> collectSensors(Map<String, AppLoad> knownApps) {
        Map<String, List<Finding>> result = new LinkedHashMap<>();
        String out = shellManager.runShellCommandAndGetFullOutput("dumpsys sensorservice");
        if (out == null) return result;

        boolean inConn = false, relevant = false;
        String  relevantPkg = null;
        List<String> found  = new ArrayList<>();

        for (String line : out.split("\n")) {
            String t = line.trim();

            if (t.startsWith("Connection Number:") || t.startsWith("Active connections")) {
                if (relevant && relevantPkg != null && !found.isEmpty()) {
                    for (String s : found) addFinding(result, relevantPkg, new Finding(Category.SENSOR, s));
                }
                inConn = true; relevant = false; relevantPkg = null; found.clear();
                continue;
            }
            if (!inConn) continue;

            if (t.startsWith("packageName=") || t.startsWith("package=") ||
                    t.startsWith("Identity=")) {
                relevant = false; relevantPkg = null;
                for (String pkg : knownApps.keySet()) {
                    if (t.contains(pkg)) { relevant = true; relevantPkg = pkg; break; }
                }
            }
            if (!relevant) continue;

            if (t.startsWith("Sensor:") || t.startsWith("SensorName=") ||
                    t.startsWith("sensor=")) {
                String raw = t.replaceFirst("(?:Sensor:|SensorName=|sensor=)\\s*", "");
                int delim = raw.indexOf("  ");
                if (delim > 0) raw = raw.substring(0, delim).trim();

                String rate = "";
                Matcher mUs = Pattern.compile("samplingPeriod[Uu]s[=:]\\s*(\\d+)").matcher(t);
                if (mUs.find()) {
                    long us = Long.parseLong(mUs.group(1));
                    if (us > 0) rate = "@" + (1_000_000L / us) + "Hz";
                } else {
                    Matcher mHz = Pattern.compile("rate[=:]\\s*(\\d+)\\s*[Hh]z").matcher(t);
                    if (mHz.find()) rate = "@" + mHz.group(1) + "Hz";
                }
                String label = classifySensor(raw) + (rate.isEmpty() ? "" : " " + rate);
                if (!found.contains(label)) found.add(label);
            }
            if (t.contains("GNSS") || t.contains("Gnss") || t.contains("GPS"))
                if (!found.contains("GPS")) found.add("GPS");
        }
        if (relevant && relevantPkg != null && !found.isEmpty()) {
            for (String s : found) addFinding(result, relevantPkg, new Finding(Category.SENSOR, s));
        }

        for (String pkg : knownApps.keySet()) {
            if (result.containsKey(pkg)) continue;
            try {
                String bsOut = shellManager.runShellCommandAndGetFullOutput(
                        "dumpsys batterystats " + pkg);
                if (bsOut == null) continue;
                Pattern srPat = Pattern.compile(
                        "Sensor\\s+(?:#)?(\\d+)[^:]*:\\s*(.*)", Pattern.CASE_INSENSITIVE);
                boolean inPkg = false;
                for (String line : bsOut.split("\n")) {
                    if (line.contains(pkg)) inPkg = true;
                    if (!inPkg) continue;
                    Matcher m = srPat.matcher(line.trim());
                    if (!m.find()) continue;
                    String dur = "";
                    Matcher mDur = Pattern.compile("(\\d+)ms").matcher(m.group(2));
                    if (mDur.find()) dur = " (" + formatDuration(Long.parseLong(mDur.group(1))) + ")";
                    addFinding(result, pkg, new Finding(Category.SENSOR,
                            sensorHandleToName(Integer.parseInt(m.group(1))) + dur));
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private Map<String, List<Finding>> collectLocation(Map<String, AppLoad> knownApps) {
        Map<String, List<Finding>> result = new LinkedHashMap<>();
        String out = shellManager.runShellCommandAndGetFullOutput("dumpsys location");
        if (out == null) return result;

        Pattern reqPat = Pattern.compile("LocationRequest\\[([^]]+)]");
        Pattern ivPat  = Pattern.compile("interval=(\\d+)");
        Pattern fgPat  = Pattern.compile("foreground=(true|false)");
        Pattern accPat = Pattern.compile(
                "(PRIORITY_HIGH_ACCURACY|HIGH_ACCURACY|PRIORITY_BALANCED|BALANCED" +
                "|PRIORITY_LOW_POWER|LOW_POWER|PRIORITY_NO_POWER|NO_POWER|PASSIVE)",
                Pattern.CASE_INSENSITIVE);
        Pattern gpsPat = Pattern.compile("activeGps(?:TimeMs)?=(\\d+)");

        for (String pkg : knownApps.keySet()) {
            int     reqCount = 0;
            boolean hasFg    = false, hasBg = false;
            String  bestAcc  = null;
            long    minIvMs  = Long.MAX_VALUE;
            long    activeGps = 0;
            boolean inBlock  = false;

            for (String line : out.split("\n")) {
                String t = line.trim();
                boolean hasPkg = t.contains(pkg);

                if (reqPat.matcher(t).find() && (hasPkg || t.startsWith("LocationRequest"))) {
                    if (hasPkg) { inBlock = true; reqCount++; }
                    else if (inBlock) { inBlock = false; }
                    Matcher mA = accPat.matcher(t);
                    if (mA.find()) bestAcc = mergeAccuracy(bestAcc, normalizeAccuracy(mA.group(1)));
                    Matcher mI = ivPat.matcher(t);
                    if (mI.find()) { long iv = Long.parseLong(mI.group(1)); if (iv>0&&iv<minIvMs) minIvMs=iv; }
                    continue;
                }
                if (inBlock && (t.isEmpty() || (!hasPkg && t.startsWith("LocationRequest"))))
                    inBlock = false;
                if (!inBlock && !hasPkg) continue;

                Matcher mF = fgPat.matcher(t);
                if (mF.find()) { if ("true".equalsIgnoreCase(mF.group(1))) hasFg=true; else hasBg=true; }
                Matcher mG = gpsPat.matcher(t);
                if (mG.find()) activeGps += Long.parseLong(mG.group(1));
            }

            if (reqCount == 0) continue;

            StringBuilder detail = new StringBuilder(reqCount + " request" + (reqCount > 1 ? "s" : ""));
            if (bestAcc != null)      detail.append(" · ").append(bestAcc);
            detail.append(" · ").append(hasFg && hasBg ? "fg+bg" : hasFg ? "foreground" : "background");
            if (minIvMs != Long.MAX_VALUE) detail.append(", каждые ").append(formatInterval(minIvMs));
            if (activeGps > 0)        detail.append(" · GPS ").append(formatDuration(activeGps));

            addFinding(result, pkg, new Finding(Category.LOCATION, detail.toString()));
        }
        return result;
    }

    private String buildFgsDetail(String svc, String typeStr, String chan, boolean killable) {
        StringBuilder sb = new StringBuilder();
        if (svc != null && !svc.isEmpty()) sb.append(svc);
        if (typeStr != null) {
            String typeName = parseForegroundServiceType(typeStr);
            if (!typeName.isEmpty() && !"NONE".equals(typeName))
                sb.append(sb.length() > 0 ? " · " : "").append(typeName);
        }
        if (chan != null && !chan.isEmpty())
            sb.append(sb.length() > 0 ? " · ch:" : "ch:").append(chan);
        sb.append(sb.length() > 0 ? " · " : "").append(killable ? "killable" : "protected");
        return sb.toString();
    }

    private String parseForegroundServiceType(String raw) {
        try {
            int mask = Integer.parseInt(raw);
            if (mask == 0) return "NONE";
            Object[][] bits = {
                    {0x001,"DATA_SYNC"},{0x002,"MEDIA_PLAYBACK"},{0x004,"PHONE_CALL"},
                    {0x008,"LOCATION"},{0x010,"CONNECTED_DEVICE"},{0x020,"MEDIA_PROJECTION"},
                    {0x040,"CAMERA"},{0x080,"MICROPHONE"},{0x100,"HEALTH"},
                    {0x200,"REMOTE_MESSAGING"},{0x400,"SYSTEM_EXEMPTED"},{0x800,"SHORT_SERVICE"},
                    {0x1000,"MEDIA_PROCESSING"}
            };
            StringBuilder sb = new StringBuilder();
            for (Object[] b : bits) if ((mask & (int) b[0]) != 0) {
                if (sb.length() > 0) sb.append("|");
                sb.append((String) b[1]);
            }
            return sb.length() > 0 ? sb.toString() : raw;
        } catch (NumberFormatException ignored) { return raw; }
    }

    private String classifySensor(String raw) {
        String n = raw.toLowerCase();
        if (n.contains("accelero"))                              return "Accelerometer";
        if (n.contains("gyro"))                                  return "Gyroscope";
        if (n.contains("magnet"))                                return "Magnetometer";
        if (n.contains("barometer") || n.contains("pressure"))  return "Barometer";
        if (n.contains("proximity"))                             return "Proximity";
        if (n.contains("light"))                                 return "Light";
        if (n.contains("gravity"))                               return "Gravity";
        if (n.contains("rotation"))                              return "Rotation";
        if (n.contains("step") || n.contains("pedometer"))      return "Pedometer";
        if (n.contains("heart") || n.contains("pulse"))         return "HeartRate";
        if (n.contains("gnss") || n.contains("gps"))            return "GPS";
        if (n.contains("temperature"))                           return "Temperature";
        if (n.contains("humidity"))                              return "Humidity";
        return raw.length() > 24 ? raw.substring(0, 24) : raw;
    }

    private String sensorHandleToName(int h) {
        switch (h) {
            case 1:  return "Accelerometer";   case 2:  return "Magnetometer";
            case 3:  return "Orientation";     case 4:  return "Gyroscope";
            case 5:  return "Light";           case 6:  return "Pressure";
            case 8:  return "Proximity";       case 9:  return "Gravity";
            case 10: return "Linear Accel";    case 11: return "Rotation Vector";
            case 14: return "Uncal Magneto";   case 15: return "Game Rotation";
            case 16: return "Uncal Gyro";      case 17: return "Step Detector";
            case 18: return "Step Counter";    case 19: return "Geo Rotation";
            case 21: return "Tilt Detector";   case 24: return "Pickup Gesture";
            case 28: return "Stationary";      case 29: return "Motion Detect";
            case 30: return "Heart Beat";      case 34: return "OffBody Detect";
            case 35: return "Uncal Accel";
            default: return "Sensor#" + h;
        }
    }

    private String mergeAccuracy(String current, String candidate) {
        if (current == null) return candidate;
        String[] order = {"HIGH_ACCURACY","BALANCED","LOW_POWER","NO_POWER","PASSIVE"};
        int ci = indexOf(order, current), ni = indexOf(order, candidate);
        return ci <= ni ? current : candidate;
    }

    private String normalizeAccuracy(String raw) {
        String u = raw.toUpperCase();
        if (u.contains("HIGH"))    return "HIGH_ACCURACY";
        if (u.contains("BALANCED")) return "BALANCED";
        if (u.contains("LOW"))     return "LOW_POWER";
        if (u.contains("NO_POWER")) return "NO_POWER";
        return "PASSIVE";
    }

    private int indexOf(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
        return arr.length;
    }

    private String resolveUid(String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            return String.valueOf(pm.getApplicationInfo(pkg, 0).uid);
        } catch (Exception ignored) { return null; }
    }

    private void addFinding(Map<String, List<Finding>> map, String pkg, Finding finding) {
        if (!map.containsKey(pkg)) map.put(pkg, new ArrayList<>());
        map.get(pkg).add(finding);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024)     return bytes + " B";
        if (bytes < 1048576)  return (bytes / 1024) + " KB";
        return (bytes / 1048576) + " MB";
    }

    private String formatDuration(long ms) {
        if (ms < 1000)           return ms + "ms";
        if (ms < 60_000)         return (ms / 1000) + "s";
        if (ms < 3_600_000)      return (ms / 60_000) + "m " + ((ms % 60_000) / 1000) + "s";
        return (ms / 3_600_000) + "h " + ((ms % 3_600_000) / 60_000) + "m";
    }

    private String formatInterval(long ms) {
        if (ms < 1000)      return ms + "ms";
        if (ms < 60_000)    return (ms / 1000) + "s";
        if (ms < 3_600_000) return (ms / 60_000) + "min";
        return (ms / 3_600_000) + "h";
    }
}
