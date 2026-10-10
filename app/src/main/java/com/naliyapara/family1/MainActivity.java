package com.naliyapara.family1;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.ValueCallback;
import android.webkit.JavascriptInterface;
import android.util.Base64;
import android.webkit.WebChromeClient;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private static final int REQUEST_FILE_CHOOSER = 2001;
    private static final int REQUEST_CAMERA = 2002;
    private static final int REQUEST_DOWNLOAD_STORAGE = 2003;
    private String pendingDownloadUrl, pendingDownloadUserAgent, pendingDownloadDisposition, pendingDownloadMimeType;
    private final String downloadBridgeToken = UUID.randomUUID().toString();
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraOutputUri;
    private boolean pendingImageChooser;

    private WebView webView;
    private ProgressBar progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Android 15+ uses edge-to-edge by default for apps targeting API 35.
        // Keep the website content inside the safe area so it never sits behind
        // the camera/status bar at the top or the system navigation bar at the bottom.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        applySystemBarInsets();

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

    private void applySystemBarInsets() {
        View root = findViewById(R.id.rootContainer);
        if (root == null) return;

        final int initialLeft = root.getPaddingLeft();
        final int initialTop = root.getPaddingTop();
        final int initialRight = root.getPaddingRight();
        final int initialBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            WindowInsetsCompat safeInsets = insets;
            androidx.core.graphics.Insets bars = safeInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());

            view.setPadding(
                    initialLeft + bars.left,
                    initialTop + bars.top,
                    initialRight + bars.right,
                    initialBottom + bars.bottom
            );
            return insets;
        });

        ViewCompat.requestApplyInsets(root);
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

        webView.addJavascriptInterface(new DownloadBridge(), "NaliyaparaDownload");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams fileChooserParams) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                openFileChooser(fileChooserParams);
                return true;
            }
        });

        // Handle website downloads through Android Download Manager. Modern Android
        // uses the system Downloads collection, so broad storage access is not needed.
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingDownloadUrl = url;
                pendingDownloadUserAgent = userAgent;
                pendingDownloadDisposition = contentDisposition;
                pendingDownloadMimeType = mimeType;
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_DOWNLOAD_STORAGE);
                return;
            }
            startDownload(url, userAgent, contentDisposition, mimeType);
        });

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
                Uri pageUri = Uri.parse(url);
                String pageHost = pageUri.getHost() == null ? "" : pageUri.getHost().toLowerCase();
                if (pageHost.equals(WEBSITE_HOST) || pageHost.endsWith("." + WEBSITE_HOST)) {
                    injectBlobDownloadSupport(view);
                }
            }
        });
    }


    // WebView's DownloadListener does not receive blob: or data: URLs created by
    // JavaScript. Intercept those downloads on our own trusted website and save
    // them through MediaStore so Android 10+ needs no broad storage permission.
    private void injectBlobDownloadSupport(WebView view) {
        String token = downloadBridgeToken;
        String script = "(function(){"
                + "if(window.__naliyaparaDownloadHooked)return;"
                + "window.__naliyaparaDownloadHooked=true;"
                + "document.addEventListener('click',function(e){"
                + "var a=e.target&&e.target.closest?e.target.closest('a'):null;"
                + "if(!a)return;var href=a.href||'';"
                + "if(!href.startsWith('blob:')&&!href.startsWith('data:'))return;"
                + "e.preventDefault();e.stopImmediatePropagation();"
                + "var name=a.getAttribute('download')||'naliyapara-download';"
                + "fetch(href).then(function(r){return r.blob();}).then(function(blob){"
                + "var reader=new FileReader();reader.onloadend=function(){"
                + "var result=String(reader.result||'');var comma=result.indexOf(',');"
                + "if(comma<0){alert('ફાઇલ તૈયાર થઈ શકી નથી');return;}"
                + "window.NaliyaparaDownload.saveBase64File('" + token + "',name,"
                + "blob.type||'application/octet-stream',result.substring(comma+1));"
                + "};reader.readAsDataURL(blob);"
                + "}).catch(function(){alert('ફાઇલ ડાઉનલોડ થઈ શકી નથી');});"
                + "},true);})();";
        view.evaluateJavascript(script, null);
    }

    private class DownloadBridge {
        @JavascriptInterface
        public void saveBase64File(String token, String fileName, String mimeType, String encodedData) {
            if (token == null || !downloadBridgeToken.equals(token) || encodedData == null) return;
            try {
                byte[] bytes = Base64.decode(encodedData, Base64.DEFAULT);
                String safeName = fileName == null ? "naliyapara-download" : fileName;
                safeName = safeName.replaceAll("[\\\\/:*?\\"<>|]", "_").trim();
                if (safeName.isEmpty()) safeName = "naliyapara-download";
                String safeMime = mimeType == null || mimeType.trim().isEmpty()
                        ? "application/octet-stream" : mimeType;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                    values.put(MediaStore.Downloads.MIME_TYPE, safeMime);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    values.put(MediaStore.Downloads.IS_PENDING, 1);
                    Uri item = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (item == null) throw new IOException("Could not create Downloads item");
                    try (OutputStream output = getContentResolver().openOutputStream(item)) {
                        if (output == null) throw new IOException("Could not open Downloads item");
                        output.write(bytes);
                    }
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(item, done, null, null);
                } else {
                    File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!downloads.exists() && !downloads.mkdirs()) throw new IOException("Could not create Downloads folder");
                    File target = new File(downloads, safeName);
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        output.write(bytes);
                    }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "ફાઇલ Downloads ફોલ્ડરમાં સેવ થઈ ગઈ છે.", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "ફાઇલ સેવ થઈ શકી નથી. ફરી પ્રયાસ કરો.", Toast.LENGTH_LONG).show());
            }
        }
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            if (mimeType != null && !mimeType.isEmpty()) request.setMimeType(mimeType);
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(fileName);
            request.setDescription("નળીયાપરા પરિવાર: ફાઇલ ડાઉનલોડ થઈ રહી છે");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "ડાઉનલોડ શરૂ થયું છે. Downloads ફોલ્ડર તપાસો.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "ડાઉનલોડ સેવા ઉપલબ્ધ નથી.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "ફાઇલ ડાઉનલોડ થઈ શકી નથી.", Toast.LENGTH_LONG).show();
        }
    }

    private void openFileChooser(WebChromeClient.FileChooserParams params) {
        String[] acceptTypes = params.getAcceptTypes();
        boolean imageOnly = false;
        if (acceptTypes != null) {
            for (String type : acceptTypes) {
                if (type != null && type.toLowerCase().startsWith("image/")) {
                    imageOnly = true;
                    break;
                }
            }
        }

        Intent content = new Intent(Intent.ACTION_GET_CONTENT);
        content.addCategory(Intent.CATEGORY_OPENABLE);
        content.setType(imageOnly ? "image/*" : "*/*");
        if (acceptTypes != null && acceptTypes.length > 0 && !imageOnly) {
            content.putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes);
        }

        if (imageOnly) {
            // Opening the photo picker does not require broad storage permission.
            // Camera permission is not required just to choose an existing photo.
            openFileChooserForImage();
            return;
        }

        Intent chooser = Intent.createChooser(content, "File પસંદ કરો");
        try {
            startActivityForResult(chooser, REQUEST_FILE_CHOOSER);
        } catch (ActivityNotFoundException e) {
            if (filePathCallback != null) filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_DOWNLOAD_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && pendingDownloadUrl != null) {
                startDownload(pendingDownloadUrl, pendingDownloadUserAgent,
                        pendingDownloadDisposition, pendingDownloadMimeType);
            } else {
                Toast.makeText(this, "ફાઇલ ડાઉનલોડ કરવા સ્ટોરેજ પરવાનગી જરૂરી છે.",
                        Toast.LENGTH_LONG).show();
            }
            pendingDownloadUrl = null;
            pendingDownloadUserAgent = null;
            pendingDownloadDisposition = null;
            pendingDownloadMimeType = null;
        }
        if (requestCode == REQUEST_CAMERA && filePathCallback != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingImageChooser = false;
                openFileChooserForImage();
            } else {
                pendingImageChooser = false;
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
            }
        }
    }

    private void openFileChooserForImage() {
        Intent content = new Intent(Intent.ACTION_GET_CONTENT);
        content.addCategory(Intent.CATEGORY_OPENABLE);
        content.setType("image/*");

        Intent[] extraIntents = new Intent[0];
        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (camera.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try { photoFile = File.createTempFile("naliyapara_upload_", ".jpg", getCacheDir()); }
            catch (IOException ignored) { }
            if (photoFile != null) {
                cameraOutputUri = androidx.core.content.FileProvider.getUriForFile(
                        this, getPackageName() + ".fileprovider", photoFile);
                camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
                camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                extraIntents = new Intent[]{camera};
            }
        }
        Intent chooser = Intent.createChooser(content, "Photo પસંદ કરો");
        if (extraIntents.length > 0) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);
        try { startActivityForResult(chooser, REQUEST_FILE_CHOOSER); }
        catch (ActivityNotFoundException e) {
            if (filePathCallback != null) filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FILE_CHOOSER || filePathCallback == null) return;

        Uri[] results = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            } else if (cameraOutputUri != null) {
                results = new Uri[]{cameraOutputUri};
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
        cameraOutputUri = null;
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
