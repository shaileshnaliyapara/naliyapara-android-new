package com.naliyapara.family1;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Device ni info + FCM token Supabase "device-register" function ne mokle chhe.
 * Website Admin Panel ma "Collected data" ma aa device dekhay chhe, ane admin e confirm karyu hoy to
 * navi request ni notification fakt aa device ne male chhe.
 * Badhu best-effort chhe - fail thay to pan website khulvama koi problem nahi.
 */
public final class DeviceRegistrar {

    private static final String REGISTER_URL =
            "https://kfeaejmlshjliesrirku.supabase.co/functions/v1/device-register";
    private static final String PREFS = "naliyapara";
    private static final String KEY_INSTALLATION_ID = "installation_id";

    private DeviceRegistrar() {}

    /** Pehla FCM token lai, pachhi register kare chhe (token na male to pan device info mokle chhe). */
    public static void registerWithCurrentToken(Context context) {
        final Context app = context.getApplicationContext();
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                String token = (task.isSuccessful() && task.getResult() != null) ? task.getResult() : null;
                register(app, token);
            });
        } catch (Exception e) {
            register(app, null);
        }
    }

    /** Background thread ma POST kare chhe. token null hoy to token mokalto nathi (server juno token rakhe chhe). */
    public static void register(Context context, String fcmToken) {
        final Context app = context.getApplicationContext();
        final String id = installationId(app);
        final String versionName = appVersion(app);
        final String token = fcmToken;

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject body = new JSONObject();
                body.put("installationId", id);
                body.put("platform", "android");
                body.put("appVersion", versionName);
                body.put("manufacturer", Build.MANUFACTURER == null ? "" : Build.MANUFACTURER);
                body.put("model", Build.MODEL == null ? "" : Build.MODEL);
                body.put("osVersion", "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
                body.put("deviceName", Build.DEVICE == null ? "" : Build.DEVICE);
                if (token != null && !token.trim().isEmpty()) body.put("fcmToken", token);

                connection = (HttpURLConnection) new URL(REGISTER_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");

                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
                connection.getResponseCode();
            } catch (Exception ignored) {
                // Device registration must never prevent the website from opening.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private static String installationId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_INSTALLATION_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply();
        }
        return id;
    }

    private static String appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
