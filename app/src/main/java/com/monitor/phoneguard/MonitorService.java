package com.monitor.phoneguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MonitorService extends Service {
    private static final String FIREBASE_URL = "https://phonewebmonitor-7afcb-default-rtdb.europe-west1.firebasedatabase.app/";
    private static final String DISCORD_WEBHOOK = "https://discord.com/api/webhooks/1554869370906476625/vVI7RBnpLBvckC9N-wcCl7r_k_62FsoWN-HPTrE7fJxcuUk8Icv5ZILMvhDNPtWQSbi9";

    private Handler handler = new Handler(Looper.getMainLooper());
    private String deviceNum = "00";
    private float currentTemp = 0.0f;
    private int currentBatt = 100;
    private boolean isUsbPlugged = true;

    private long lastDangerAlertTime = 0;
    private long lastBattAlertTime = 0;
    private long lastDisconnectAlertTime = 0;

    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);

            if (scale > 0) currentBatt = (int) ((level / (float) scale) * 100);
            currentTemp = tempRaw / 10.0f;

            boolean currentlyPlugged = (plugged == BatteryManager.BATTERY_PLUGGED_USB || plugged == BatteryManager.BATTERY_PLUGGED_AC);
            if (isUsbPlugged && !currentlyPlugged) {
                long now = System.currentTimeMillis();
                if (now - lastDisconnectAlertTime > 180000) {
                    lastDisconnectAlertTime = now;
                    sendDiscordAlert("\uD83D\uDD0C **[\uD3F0\uBCF4\uB4DC USB \uBD84\uB9AC/\uC778\uC2DD \uD574\uC81C \uAC10\uC9C0]**\n\u2022 \uAE30\uAE30: **" + deviceNum + "\uBC88 \uD3F0**\n\u2022 \uC0C1\uD0DC: \uC804\uC6D0 \uACF5\uAE09 \uBC0F USB \uC5F0\uACB0 \uC911\uB2E8!");
                }
            }
            isUsbPlugged = currentlyPlugged;
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("device_num")) {
            deviceNum = intent.getStringExtra("device_num");
        } else {
            SharedPreferences prefs = getSharedPreferences("MonitorPrefs", Context.MODE_PRIVATE);
            deviceNum = prefs.getString("device_num", "00");
        }
        startForegroundChannel();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        handler.post(reportRunnable);
        return START_STICKY;
    }

    private Runnable reportRunnable = new Runnable() {
        @Override
        public void run() {
            long uptimeSeconds = SystemClock.elapsedRealtime() / 1000;
            int hours = (int) (uptimeSeconds / 3600);
            int mins = (int) ((uptimeSeconds % 3600) / 60);
            String uptimeStr = hours + "\uC2DC\uAC04 " + mins + "\uBD84";

            sendDataToFirebase(deviceNum, currentTemp, currentBatt, uptimeStr, isUsbPlugged);

            long now = System.currentTimeMillis();
            if (currentTemp >= 39.0f && (now - lastDangerAlertTime > 300000)) {
                lastDangerAlertTime = now;
                sendDiscordAlert("\uD83D\uDEA8 **[\uC2A4\uB9C8\uD2B8\uD3F0 \uACE0\uC628 \uC704\uD5D8] " + deviceNum + "\uBC88 \uAE30\uAE30**\n\u2022 \uD604\uC7AC \uC628\uB3C4: **" + currentTemp + "\u2103**\n\u2022 \uBC30\uD130\uB9AC: **" + currentBatt + "%**\n\u2022 \uAC00\uB3D9\uC2DC\uAC04: **" + uptimeStr + "**");
            }
            if (currentBatt < 50 && (now - lastBattAlertTime > 600000)) {
                lastBattAlertTime = now;
                sendDiscordAlert("\uD83E\uDEAB **[\uBC30\uD130\uB9AC \uC800\uD558 \uACBD\uACE0 (50% \uBBF8\uB man)] " + deviceNum + "\uBC88 \uAE30\uAE30**\n\u2022 \uC794\uC5EC \uBC30\uD130\uB9AC: **" + currentBatt + "%**\n\u2022 \uD604\uC7AC \uC628\uB3C4: " + currentTemp + "\u2103");
            }
            handler.postDelayed(this, 10000);
        }
    };

    private void sendDataToFirebase(String num, float temp, int batt, String uptime, boolean plugged) {
        new Thread(() -> {
            try {
                URL url = new URL(FIREBASE_URL + "devices/" + num + ".json");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                long ts = System.currentTimeMillis();
                String json = "{\"sort\":\"" + num + "\",\"temp\":" + temp + ",\"batt\":" + batt + ",\"uptime\":\"" + uptime + "\",\"plugged\":" + plugged + ",\"updatedAt\":" + ts + "}";
                try (OutputStream os = conn.getOutputStream()) { os.write(json.getBytes("UTF-8")); }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    private void sendDiscordAlert(String content) {
        new Thread(() -> {
            try {
                URL url = new URL(DISCORD_WEBHOOK);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String payload = "{\"content\": \"" + content.replace("\n", "\\n") + "\"}";
                try (OutputStream os = conn.getOutputStream()) { os.write(payload.getBytes("UTF-8")); }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    private void startForegroundChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("monitor_ch", "Phone Monitor", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, "monitor_ch")
                .setContentTitle(deviceNum + " Phone Monitoring")
                .setContentText("Active")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build();
            startForeground(1001, notification);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
