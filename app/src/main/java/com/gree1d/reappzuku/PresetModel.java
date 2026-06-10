package com.gree1d.reappzuku;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class PresetModel {

    public static final int PRESET_1 = 1;
    public static final int PRESET_2 = 2;

    public int presetNumber;
    public String name;

    public boolean autoKillEnabled;
    public boolean periodicKillEnabled;
    public int killInterval;
    public boolean killOnScreenOff;
    public boolean ramThresholdEnabled;
    public int ramThreshold;
    public int autoKillType;
    public int killMode;

    public Set<String> whitelistedApps;
    public Set<String> blacklistedApps;

    public int startHour;
    public int startMinute;
    public int endHour;
    public int endMinute;

    public PresetModel(int presetNumber) {
        this.presetNumber = presetNumber;
        this.name = "Preset " + presetNumber;
        this.whitelistedApps = new HashSet<>();
        this.blacklistedApps = new HashSet<>();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("presetNumber", presetNumber);
        obj.put("name", name);
        obj.put("autoKillEnabled", autoKillEnabled);
        obj.put("periodicKillEnabled", periodicKillEnabled);
        obj.put("killInterval", killInterval);
        obj.put("killOnScreenOff", killOnScreenOff);
        obj.put("ramThresholdEnabled", ramThresholdEnabled);
        obj.put("ramThreshold", ramThreshold);
        obj.put("autoKillType", autoKillType);
        obj.put("killMode", killMode);
        obj.put("startHour", startHour);
        obj.put("startMinute", startMinute);
        obj.put("endHour", endHour);
        obj.put("endMinute", endMinute);

        JSONArray whitelist = new JSONArray();
        for (String pkg : whitelistedApps) whitelist.put(pkg);
        obj.put("whitelistedApps", whitelist);

        JSONArray blacklist = new JSONArray();
        for (String pkg : blacklistedApps) blacklist.put(pkg);
        obj.put("blacklistedApps", blacklist);

        return obj;
    }

    public static PresetModel fromJson(JSONObject obj) throws JSONException {
        int number = obj.getInt("presetNumber");
        PresetModel model = new PresetModel(number);
        model.name = obj.optString("name", "Preset " + number);
        model.autoKillEnabled = obj.optBoolean("autoKillEnabled", false);
        model.periodicKillEnabled = obj.optBoolean("periodicKillEnabled", false);
        model.killInterval = obj.optInt("killInterval", 15);
        model.killOnScreenOff = obj.optBoolean("killOnScreenOff", false);
        model.ramThresholdEnabled = obj.optBoolean("ramThresholdEnabled", false);
        model.ramThreshold = obj.optInt("ramThreshold", 80);
        model.autoKillType = obj.optInt("autoKillType", 0);
        model.killMode = obj.optInt("killMode", 1);
        model.startHour = obj.optInt("startHour", 8);
        model.startMinute = obj.optInt("startMinute", 0);
        model.endHour = obj.optInt("endHour", 20);
        model.endMinute = obj.optInt("endMinute", 0);

        JSONArray whitelist = obj.optJSONArray("whitelistedApps");
        if (whitelist != null) {
            for (int i = 0; i < whitelist.length(); i++) {
                model.whitelistedApps.add(whitelist.getString(i));
            }
        }

        JSONArray blacklist = obj.optJSONArray("blacklistedApps");
        if (blacklist != null) {
            for (int i = 0; i < blacklist.length(); i++) {
                model.blacklistedApps.add(blacklist.getString(i));
            }
        }

        return model;
    }

    public int getStartTotalMinutes() {
        return startHour * 60 + startMinute;
    }

    public int getEndTotalMinutes() {
        return endHour * 60 + endMinute;
    }

    public boolean overlapsWithExcludingSelf(PresetModel other) {
        if (other == null) return false;
        int thisStart = getStartTotalMinutes();
        int thisEnd = getEndTotalMinutes();
        int otherStart = other.getStartTotalMinutes();
        int otherEnd = other.getEndTotalMinutes();

        if (thisEnd <= thisStart) {
            if (otherEnd <= otherStart) {
                return true;
            }
            return otherStart < thisEnd || otherStart >= thisStart || otherEnd > thisStart;
        }

        if (otherEnd <= otherStart) {
            return thisStart < otherEnd || thisStart >= otherStart || thisEnd > otherStart;
        }

        return thisStart < otherEnd && otherStart < thisEnd;
    }
}
