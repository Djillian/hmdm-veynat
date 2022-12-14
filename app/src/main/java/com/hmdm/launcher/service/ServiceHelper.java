package com.hmdm.launcher.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.ConfigUpdater;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.pro.service.CheckForegroundAppAccessibilityService;
import com.hmdm.launcher.pro.service.CheckForegroundApplicationService;
import com.hmdm.launcher.util.RemoteLogger;

import org.eclipse.paho.android.service.MqttAndroidClient;

import java.net.URL;

import static android.content.Context.MODE_PRIVATE;

public class ServiceHelper {
    public static void startServices(Context context) {
        // Start Push service
        String pushOptions = null;
        int keepaliveTime = Const.DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC;
        SettingsHelper settingsHelper = SettingsHelper.getInstance(context);
        if (settingsHelper != null && settingsHelper.getConfig() != null) {
            pushOptions = settingsHelper.getConfig().getPushOptions();
            Integer newKeepaliveTime = settingsHelper.getConfig().getKeepaliveTime();
            if (newKeepaliveTime != null && newKeepaliveTime >= 30) {
                keepaliveTime = newKeepaliveTime;
            }
        }
        if (BuildConfig.MQTT_SERVICE_FOREGROUND && BuildConfig.ENABLE_PUSH && pushOptions != null &&
                (pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_WORKER)
                        || pushOptions.equals(ServerConfig.PUSH_OPTIONS_MQTT_ALARM))) {
            try {
                URL url = new URL(settingsHelper.getBaseUrl());
                // Broadcast receivers are not allowed to bind to services
                // Therefore we start a service, and it binds to itself using
                // PushNotificationMqttWrapper.getInstance().connect()
                Intent serviceStartIntent = new Intent();
                serviceStartIntent.setClassName(context, MqttAndroidClient.SERVICE_NAME);
                serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_START_AT_BOOT, true);
                serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_DOMAIN, url.getHost());
                serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_KEEPALIVE_TIME, keepaliveTime);
                serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_PUSH_OPTIONS, pushOptions);
                serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_DEVICE_ID, settingsHelper.getDeviceId());
                Object service = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        context.startForegroundService(serviceStartIntent) :
                        context.startService(serviceStartIntent);
                Log.i(Const.LOG_TAG, "Starting Push service from BootReceiver");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Start required services here instead of MainActivity (because it's not running)
        // Notice: some devices do not allow starting background services from boot receiver
        // java.lang.IllegalStateException
        // Not allowed to start service Intent { cmp=com.hmdm.launcher/.service.StatusControlService }: app is in background
        // Let's just ignore these exceptions for now
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE);
        // Foreground apps checks are not available in a free version: services are the stubs
        if (preferences.getInt(Const.PREFERENCES_USAGE_STATISTICS, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            try {
                context.startService(new Intent(context, CheckForegroundApplicationService.class));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (preferences.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_OFF) == Const.PREFERENCES_ON) {
            try {
                context.startService(new Intent(context, CheckForegroundAppAccessibilityService.class));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            context.startService(new Intent(context, StatusControlService.class));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Send pending logs to server
        RemoteLogger.sendLogsToServer(context);

        ConfigUpdater.forceConfigUpdate(context);
    }
}
