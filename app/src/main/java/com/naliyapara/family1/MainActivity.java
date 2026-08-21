package com.naliyapara.family1;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String WEBSITE_URL = "http://naliyapara.co.in";
    private static final String APP_NAME = "નળીયાપરા પરિવાર";
    private static final String WEBSITE_HOST = "naliyapara.co.in";
    private static final String REGISTER_URL =
            "https://kfeaejmlshjliesrirku.supabase.co/functions/v1/register-notification-device";
    private static final String PREFS = "naliyapara";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final int REQUEST_NOTIFICATIONS = 1001;

    private WebView webView;
    private ProgressBar progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();

        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.progress);

        configureWebView();
        requestNotificationPermission();
        setupFirebaseNotifications();

        if (savedInstanceState == null) {
            webView.loadUrl(WEBSITE_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }
        });
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) return true;

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();

        // Keep the Naliyapara website inside the app.
        if (("http".equals(scheme) || "https".equals(scheme))
                && (host.equals(WEBSITE_HOST) || host.endsWith("." + WEBSITE_HOST))) {
            return false;
        }

        // YouTube: open the installed YouTube app when Android can resolve it.
        if (host.equals("youtube.com") || host.equals("www.youtube.com")
                || host.equals("m.youtube.com") || host.equals("youtu.be")
                || host.endsWith(".youtube.com")) {
            Intent youtubeIntent = new Intent(Intent.ACTION_VIEW, uri);
            youtubeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            youtubeIntent.setPackage("com.google.android.youtube");
            try {
                startActivity(youtubeIntent);
                return true;
            } catch (ActivityNotFoundException ignored) {
                // Fall through to the normal Android app/browser resolver.
            }
        }

        // All other external links: Android opens the matching installed app,
        // or the browser when no matching app is installed.
        Intent external = new Intent(Intent.ACTION_VIEW, uri);
        external.addCategory(Intent.CATEGORY_BROWSABLE);
        external.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(external);
        } catch (ActivityNotFoundException ignored) {
            // Nothing can handle this URL.
        }
        return true;
    }

    private void setupFirebaseNotifications() {
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                .addOnCompleteListener(task -> {
                    // Topic subscription is intentionally best-effort.
                });

        registerThisDevice();
    }

    private void registerThisDevice() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String installationId = prefs.getString(KEY_INSTALLATION_ID, null);
        if (installationId == null) {
            installationId = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_INSTALLATION_ID, installationId).apply();
        }

        final String id = installationId;
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(REGISTER_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");

                JSONObject body = new JSONObject();
                body.put("installationId", id);
                body.put("platform", "android");
                body.put("appVersion", "1.0");

                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }

                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    // Registered successfully.
                } else {
                    readResponse(connection.getErrorStream());
                }
            } catch (Exception ignored) {
                // Device registration must never prevent the website from opening.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private String readResponse(InputStream input) {
        if (input == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            return result.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS
            );
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "naliyapara_notifications",
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(getString(R.string.notification_channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
