# નળીયાપરા પરિવાર — Android App

Package: `com.naliyapara.family1`

This version:
1. Opens the Naliyapara website first.
2. Uses the Naliyapara logo as the app icon.
3. Keeps `naliyapara.co.in` inside the app.
4. Opens external links through Android's app/browser resolver (YouTube uses the installed YouTube app when available).
5. Subscribes to the Firebase Cloud Messaging topic `all_users`.
6. Registers the installation with the existing Supabase `register-notification-device` function.
7. Supports notifications when the app is in the background/closed, subject to Android notification permission and device settings.

The existing Supabase `send-fcm-notification` function sends to the `all_users` FCM topic.
