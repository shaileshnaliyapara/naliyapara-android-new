package com.naliyapara.family1;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class NaliyaparaMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "naliyapara_notifications";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String title = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getTitle()
                : null;
        String body = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getBody()
                : null;

        if (title == null || title.trim().isEmpty()) {
            title = "નળીયાપરા પરિવાર";
        }
        if (body == null || body.trim().isEmpty()) {
            body = "નવું Notification મળ્યું છે.";
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                flags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
        }
    }

    @Override
    public void onNewToken(String token) {
        // Keep this device subscribed after an FCM token refresh.
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic("all_users");
        // Navo token Supabase ma update karo (admin device ne request notification male te mate).
        DeviceRegistrar.register(this, token);
    }
}
