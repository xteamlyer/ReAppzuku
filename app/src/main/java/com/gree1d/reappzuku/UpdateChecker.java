package com.gree1d.reappzuku;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;

import io.github.godsarmy.mlmarkdown.MlKitMarkdownTranslator;
import io.github.godsarmy.mlmarkdown.api.TranslationCallback;
import io.github.godsarmy.mlmarkdown.api.TranslationErrorCode;
import io.github.godsarmy.mlmarkdown.api.TranslationException;
import io.noties.markwon.Markwon;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateChecker {

    private static final String TAG = "UpdateChecker";

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/gree1d/ReAppzuku/releases/latest";
    private static final String RELEASES_URL =
            "https://github.com/gree1d/ReAppzuku/releases";

    private static final String CHANNEL_ID  = "reappzuku_updates";
    private static final int    NOTIF_ID    = 9001;

    private static final String PREFS_NAME        = "update_checker_prefs";
    private static final String KEY_LAST_CHECK_MS = "last_check_ms";
    private static final long   CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L;

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS    = 8_000;

    public static void checkForUpdatesAuto(Context context) {
        if (!isAppInForeground(context)) {
            Log.d(TAG, "Auto-check skipped: app is in background");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0);
        if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
            Log.d(TAG, "Auto-check skipped: within throttle window");
            return;
        }

        if (!isConnected(context)) {
            Log.d(TAG, "Auto-check skipped: no internet");
            return;
        }

        prefs.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply();

        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(() -> {
            ReleaseInfo info = fetchLatestRelease();
            if (info == null) return;

            String currentVersion = getAppVersion(context);
            if (isNewer(info.tagName, currentVersion)) {
                Log.i(TAG, "Auto-check: new version found: " + info.tagName);
                postUpdateNotification(context, info);
            } else {
                Log.d(TAG, "Auto-check: already up to date (" + currentVersion + ")");
            }
        });
    }

    public static void checkForUpdatesManual(Context context) {
        if (!isConnected(context)) {
            showToast(context, "No internet");
            return;
        }

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());

        exec.execute(() -> {
            ReleaseInfo info = fetchLatestRelease();
            String currentVersion = getAppVersion(context);

            main.post(() -> {
                if (info == null) {
                    showToast(context, context.getString(R.string.update_check_failed));
                    return;
                }
                if (isNewer(info.tagName, currentVersion)) {
                    showUpdateDialog(context, info);
                } else {
                    showToast(context, context.getString(R.string.update_up_to_date, currentVersion));
                }
            });
        });
    }


    private static void showUpdateDialog(Context context, ReleaseInfo info) {
        String originalMd = info.changelog.isEmpty()
                ? "Version " + info.tagName + " is available."
                : "Version " + info.tagName + " is available.\n\n" + stripGitHubAlerts(info.changelog);

        String targetLangTag = resolveTargetLanguageTag();

        boolean[] showingTranslation = {false};
        String[] cachedTranslation = {null};

        Markwon markwon = Markwon.create(context);
        TextView messageView = new TextView(context);
        int px16 = dp(context, 16);
        int px8  = dp(context, 8);
        messageView.setPadding(px16, px8, px16, px8);
        messageView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        renderMarkdown(markwon, messageView, originalMd);

        android.widget.LinearLayout container = new android.widget.LinearLayout(context);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);

        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setVisibility(View.GONE);
        android.widget.LinearLayout.LayoutParams pbParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        pbParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        pbParams.topMargin = dp(context, 8);
        pbParams.bottomMargin = dp(context, 4);
        container.addView(progressBar, pbParams);
        container.addView(messageView);

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(container);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.update_dialog_title))
                .setView(scrollView)
                .setPositiveButton(context.getString(R.string.update_dialog_download), (d, w) -> {
                    d.dismiss();
                    try {
                        context.startActivity(new Intent(
                                Intent.ACTION_VIEW, Uri.parse(info.releasePageUrl)));
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to open release page URL", e);
                    }
                })
                .setNegativeButton(context.getString(R.string.update_dialog_close), null);

        if (targetLangTag != null) {
            builder.setNeutralButton(context.getString(R.string.update_dialog_translate), null);
        }

        AlertDialog dialog = builder.create();
        applyDialogAccentColors(context, dialog);
        dialog.show();

        if (targetLangTag != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (!showingTranslation[0]) {
                    if (cachedTranslation[0] != null) {
                        renderMarkdown(markwon, messageView, cachedTranslation[0]);
                        showingTranslation[0] = true;
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                                .setText(context.getString(R.string.update_dialog_show_original));
                    } else {
                        checkModelAndTranslate(
                                context, dialog, markwon, messageView, progressBar,
                                originalMd, targetLangTag,
                                showingTranslation, cachedTranslation);
                    }
                } else {
                    renderMarkdown(markwon, messageView, originalMd);
                    showingTranslation[0] = false;
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                            .setText(context.getString(R.string.update_dialog_translate));
                }
            });

            isModelDownloaded(targetLangTag, isDownloaded -> {
                if (isDownloaded && dialog.isShowing()) {
                    startTranslation(context, dialog, markwon, messageView, progressBar,
                            originalMd, targetLangTag, showingTranslation, cachedTranslation,
                            /* autoApply= */ true);
                }
            });
        }
    }

    private static void checkModelAndTranslate(
            Context context,
            AlertDialog parentDialog,
            Markwon markwon,
            TextView messageView,
            ProgressBar progressBar,
            String originalMd,
            String targetLangTag,
            boolean[] showingTranslation,
            String[] cachedTranslation) {

        isModelDownloaded(targetLangTag, isDownloaded -> {
            if (!parentDialog.isShowing()) return;
            if (isDownloaded) {
                startTranslation(context, parentDialog, markwon, messageView, progressBar,
                        originalMd, targetLangTag, showingTranslation, cachedTranslation,
                        /* autoApply= */ false);
            } else {
                showDownloadConfirmDialog(context, parentDialog, markwon, messageView, progressBar,
                        originalMd, targetLangTag, showingTranslation, cachedTranslation);
            }
        });
    }

    private static void showDownloadConfirmDialog(
            Context context,
            AlertDialog parentDialog,
            Markwon markwon,
            TextView messageView,
            ProgressBar progressBar,
            String originalMd,
            String targetLangTag,
            boolean[] showingTranslation,
            String[] cachedTranslation) {

        String langName = getLanguageDisplayName(targetLangTag, context);
        String message  = context.getString(
                R.string.update_translate_download_message, langName);

        AlertDialog confirmDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.update_translate_download_title))
                .setMessage(message)
                .setPositiveButton(context.getString(R.string.update_translate_download_ok), (d, w) -> {
                    d.dismiss();
                    downloadModelAndTranslate(context, parentDialog, markwon, messageView,
                            progressBar, originalMd, targetLangTag,
                            showingTranslation, cachedTranslation);
                })
                .setNegativeButton(context.getString(R.string.dialog_cancel), null)
                .create();

        applyDialogAccentColors(context, confirmDialog);
        confirmDialog.show();
    }

    private static void downloadModelAndTranslate(
            Context context,
            AlertDialog parentDialog,
            Markwon markwon,
            TextView messageView,
            ProgressBar progressBar,
            String originalMd,
            String targetLangTag,
            boolean[] showingTranslation,
            String[] cachedTranslation) {

        TranslateRemoteModel model = new TranslateRemoteModel.Builder(targetLangTag).build();
        DownloadConditions conditions = new DownloadConditions.Builder().build();

        new Handler(Looper.getMainLooper()).post(() -> progressBar.setVisibility(View.VISIBLE));

        RemoteModelManager.getInstance().download(model, conditions)
                .addOnSuccessListener(unused -> {
                    Log.i(TAG, "ML Kit model downloaded: " + targetLangTag);
                    startTranslation(context, parentDialog, markwon, messageView, progressBar,
                            originalMd, targetLangTag, showingTranslation, cachedTranslation,
                            /* autoApply= */ false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Model download failed: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(() -> {
                        progressBar.setVisibility(View.GONE);
                        showToast(context, context.getString(R.string.update_translate_download_failed));
                    });
                });
    }

    private static void startTranslation(
            Context context,
            AlertDialog parentDialog,
            Markwon markwon,
            TextView messageView,
            ProgressBar progressBar,
            String originalMd,
            String targetLangTag,
            boolean[] showingTranslation,
            String[] cachedTranslation,
            boolean autoApply) {

        new Handler(Looper.getMainLooper()).post(() -> progressBar.setVisibility(View.VISIBLE));

        MlKitMarkdownTranslator translator = new MlKitMarkdownTranslator();

        translator.translateMarkdown(originalMd, "en", targetLangTag, new TranslationCallback() {
            @Override
            public void onSuccess(String translatedMarkdown) {
                cachedTranslation[0] = translatedMarkdown;
                translator.close();

                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (!parentDialog.isShowing()) return;

                    if (autoApply || !showingTranslation[0]) {
                        renderMarkdown(markwon, messageView, translatedMarkdown);
                        showingTranslation[0] = true;
                        parentDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                                .setText(context.getString(R.string.update_dialog_show_original));
                    }
                });
            }

            @Override
            public void onFailure(Exception error) {
                translator.close();
                Log.e(TAG, "Translation failed", error);

                new Handler(Looper.getMainLooper()).post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (!parentDialog.isShowing()) return;

                    if (error instanceof TranslationException
                            && ((TranslationException) error).getCode()
                            == TranslationErrorCode.MODEL_NOT_DOWNLOADED) {
                        showToast(context,
                                context.getString(R.string.update_translate_model_missing));
                    } else {
                        showToast(context,
                                context.getString(R.string.update_translate_failed));
                    }
                });
            }
        });
    }


    private interface ModelCheckCallback {
        void onResult(boolean isDownloaded);
    }

    private static void isModelDownloaded(String langTag, ModelCheckCallback callback) {
        TranslateRemoteModel model = new TranslateRemoteModel.Builder(langTag).build();
        RemoteModelManager.getInstance().isModelDownloaded(model)
                .addOnSuccessListener(isDownloaded ->
                        new Handler(Looper.getMainLooper()).post(() ->
                                callback.onResult(isDownloaded)))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "isModelDownloaded check failed: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(() -> callback.onResult(false));
                });
    }

    private static String resolveTargetLanguageTag() {
        String lang = Locale.getDefault().getLanguage();
        if ("en".equals(lang)) return null;

        String tag = TranslateLanguage.fromLanguageTag(lang);
        if (tag == null) {
            Log.d(TAG, "ML Kit does not support locale language: " + lang);
        }
        return tag; 
    }

    private static String getLanguageDisplayName(String langTag, Context context) {
        try {
            Locale locale = Locale.forLanguageTag(langTag);
            String name = locale.getDisplayLanguage(Locale.getDefault());
            if (name == null || name.isEmpty() || name.equals(langTag)) {
                name = locale.getDisplayLanguage(Locale.ENGLISH);
            }
            return name != null && !name.isEmpty() ? name : langTag;
        } catch (Exception e) {
            return langTag;
        }
    }

    private static void renderMarkdown(Markwon markwon, TextView view, String md) {
        try {
            markwon.setMarkdown(view, md);
        } catch (Exception e) {
            Log.w(TAG, "Markwon rendering failed, using plain text", e);
            view.setText(md);
        }
    }

    private static void applyDialogAccentColors(Context context, AlertDialog dialog) {
        if (!(context instanceof android.app.Activity)) return;

        SharedPreferences prefs = context.getSharedPreferences(
                "app_preferences", Context.MODE_PRIVATE);

        int accent = prefs.getInt("accent", 0 );
        int btnColor;
        if (accent == 20 ) {
            int onColor = prefs.getInt("accent_on_color", 1 );
            btnColor = (onColor == 2 ) ? Color.BLACK : Color.WHITE;
        } else {
            btnColor = ContextCompat.getColor(context, R.color.dialog_button_text);
        }

        for (int which : new int[]{
                AlertDialog.BUTTON_POSITIVE,
                AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button btn = dialog.getButton(which);
            if (btn != null) btn.setTextColor(btnColor);
        }
    }

    private static int dp(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }


    private static boolean isAppInForeground(Context context) {
        android.app.ActivityManager am =
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        for (android.app.ActivityManager.RunningAppProcessInfo proc :
                am.getRunningAppProcesses()) {
            if (proc.processName.equals(context.getPackageName())) {
                return proc.importance ==
                        android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
            }
        }
        return false;
    }

    private static ReleaseInfo fetchLatestRelease() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(GITHUB_API_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "GitHub API returned HTTP " + code);
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject json    = new JSONObject(sb.toString());
            String tagName     = json.optString("tag_name", "").replaceFirst("^v", "");
            String body        = json.optString("body", "");
            String htmlUrl     = json.optString("html_url", RELEASES_URL);

            String downloadUrl = htmlUrl;
            JSONArray assets   = json.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "");
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url", htmlUrl);
                        break;
                    }
                }
            }

            return new ReleaseInfo(tagName, body.trim(), downloadUrl, htmlUrl);

        } catch (UnknownHostException e) {
            Log.w(TAG, "No route to GitHub (DNS failed): " + e.getMessage());
            return null;
        } catch (SocketTimeoutException e) {
            Log.w(TAG, "GitHub API timed out: " + e.getMessage());
            return null;
        } catch (IOException e) {
            Log.w(TAG, "Network I/O error fetching release: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error fetching release info", e);
            return null;
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    static boolean isNewer(String remote, String local) {
        if (remote == null || remote.isEmpty()) return false;
        try {
            int[] r = parseVersion(remote.replaceFirst("^v", ""));
            int[] l = parseVersion(local.replaceFirst("^v", ""));
            for (int i = 0; i < Math.max(r.length, l.length); i++) {
                int rv = i < r.length ? r[i] : 0;
                int lv = i < l.length ? l[i] : 0;
                if (rv > lv) return true;
                if (rv < lv) return false;
            }
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Version parse failed: remote=" + remote + " local=" + local);
            return false;
        }
    }

    private static int[] parseVersion(String v) {
        String[] parts = v.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            nums[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
        }
        return nums;
    }

    private static String getAppVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.0.0";
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isConnected(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } else {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        }
    }

    private static void showToast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(context, message,
                        android.widget.Toast.LENGTH_SHORT).show());
    }

    private static void postUpdateNotification(Context context, ReleaseInfo info) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "App Updates",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Notifications about new ReAppzuku releases");
            nm.createNotificationChannel(ch);
        }

        Intent openIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl));
        PendingIntent pi = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shappky)
                .setContentTitle("ReAppzuku " + info.tagName + " available")
                .setContentText("Tap to download the latest release")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(info.changelog.isEmpty()
                                ? "Tap to download the latest release"
                                : info.changelog))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted, skipping update notification");
                return;
            }
        }

        nm.notify(NOTIF_ID, builder.build());
    }

    private static String stripGitHubAlerts(String markdown) {
        if (markdown == null) return "";
        return markdown
                .replaceAll("(?m)^> \\[!(NOTE|WARNING|TIP|IMPORTANT|CAUTION)][ \\t]*\\r?\\n?", "")
                .replaceAll("(?m)^> ?", "")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
    }

    private static class ReleaseInfo {
        final String tagName;
        final String changelog;
        final String downloadUrl;
        final String releasePageUrl;

        ReleaseInfo(String tagName, String changelog,
                    String downloadUrl, String releasePageUrl) {
            this.tagName        = tagName;
            this.changelog      = changelog;
            this.downloadUrl    = downloadUrl;
            this.releasePageUrl = releasePageUrl;
        }
    }
}
