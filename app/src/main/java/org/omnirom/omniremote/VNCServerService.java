/*
 *  Copyright (C) 2020 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omniremote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.UserHandle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.Nullable;

public class VNCServerService extends Service {
    private static final String TAG = Utils.TAG;
    public static final String ACTION_START = "org.omnirom.omniremote.ACTION_START";
    public static final String ACTION_STOP = "org.omnirom.omniremote.ACTION_STOP";
    public static final String ACTION_ERROR = "org.omnirom.omniremote.ACTION_ERROR";
    public static final String ACTION_STATUS = "org.omnirom.omniremote.ACTION_STATUS";
    public static final String EXTRA_ERROR_START_FAILED = "start_failed";
    public static final String EXTRA_ERROR_STOP_FAILED = "stop_failed";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_STATUS_STARTED = "started";
    public static final String EXTRA_STATUS_STOPPED = "stopped";

    private static final int NOTIFICATION_ID = 1;
    private static final int NOTIFICATION_STOP_ID = 2;
    private static final String NOTIFICATION_CHANNEL_ID = "vncserver";

    private FileObserver mFileObserver;

    private PowerManager.WakeLock mWakeLock;
    private PowerManager.WakeLock mScreenLock;
    private Handler mHandler = new Handler();
    private boolean mWorkInProgress;
    private boolean mServiceMode;

    // this is needed cause start server is asynchron
    // se we fire the start and need to wait until the
    // server is creating its pid file to issue that
    // it has been started. So there is a FileObserver
    // waiting for the pid file to arrive
    //
    // since we dont want to wait forever this watchdog
    // runs for max 5s after issuing the start and if
    // the pid file was not seen until then mark the
    // start as failed
    private Runnable mWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "You got bitten by the watch dog");
            mFileObserver.stopWatching();
            mWorkInProgress = false;
            if (!Utils.isRunning(VNCServerService.this)) {
                sendErrorBrodcast(EXTRA_ERROR_START_FAILED);
                if (mServiceMode) {
                    stopSelf();
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":wakelock");
        mScreenLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG + ":screenLock");
        createNotificationChannel();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        try {
            mScreenLock.release();
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand " + intent.getAction());
        try {
            mWakeLock.acquire();

            if (ACTION_START.equals(intent.getAction())) {
                // check upfront
                if (Utils.isRunning(this) || mWorkInProgress) {
                    return START_NOT_STICKY;
                }
                if (!Utils.isConnected()) {
                    sendErrorBrodcast(EXTRA_ERROR_START_FAILED);
                    stopSelf();
                    return START_NOT_STICKY;
                }
                String password = intent.getStringExtra("password");
                List<String> parameter = new ArrayList<>();
                if (intent.hasExtra("parameter")) {
                    String[] paramString = intent.getStringArrayExtra("parameter");
                    parameter.addAll(Arrays.asList(paramString));
                }
                mServiceMode = true;
                doStartServer(password, parameter);
            }

            if (ACTION_STOP.equals(intent.getAction())) {
                // check upfront
                if (!Utils.isRunning(this)) {
                    stopSelf();
                    return START_NOT_STICKY;
                }
                if (mWorkInProgress) {
                    return START_NOT_STICKY;
                }
                doStopServer();
                // always stop service
                stopSelf();
                return START_NOT_STICKY;
            }
        } finally {
            mWakeLock.release();
        }
        return START_STICKY;
    }

    private void sendErrorBrodcast(String error) {
        Intent errorIntent = new Intent(ACTION_ERROR);
        errorIntent.putExtra(EXTRA_ERROR, error);
        sendBroadcast(errorIntent);
        // widget needs to update status and is not a broadcast receiver
        WidgetHelper.updateWidgets(getApplicationContext());
    }

    private void sendStatusBrodcast(String status) {
        Intent errorIntent = new Intent(ACTION_STATUS);
        errorIntent.putExtra(EXTRA_STATUS, status);
        sendBroadcast(errorIntent);
        // widget needs to update status and is not a broadcast receiver
        WidgetHelper.updateWidgets(getApplicationContext());
    }

    private void doStopServer() {
        try {
            Log.i(TAG, "stopServer");

            mWorkInProgress = true;
            // always release does not matter what happens else
            Log.i(TAG, "Release screen lock");
            try {
                mScreenLock.release();
            } catch (Exception e) {
                // ignore
            }
            mHandler.removeCallbacks(mWatchdogRunnable);
            // stop is synchron - so no need for any handling of that here
            if (!Utils.stopServer(this)) {
                sendErrorBrodcast(EXTRA_ERROR_STOP_FAILED);
            } else {
                sendStatusBrodcast(EXTRA_STATUS_STOPPED);
            }
            getNotificationManager().cancel(NOTIFICATION_ID);
        } catch (Exception e) {
            sendErrorBrodcast(EXTRA_ERROR_STOP_FAILED);
        } finally {
            mWorkInProgress = false;
        }
    }

    private void doStartServer(String password, List<String> parameter) {
        Log.i(TAG, "startServer " + password + " " + parameter);

        mWorkInProgress = true;
        mHandler.removeCallbacks(mWatchdogRunnable);
        try {
            mFileObserver = new FileObserver(Utils.getStateDir(VNCServerService.this).getAbsolutePath()) {
                @Override
                public void onEvent(int i, @Nullable String s) {
                    if (i == FileObserver.CREATE && s.equals(
                            Utils.getPidPath(VNCServerService.this).getName())) {
                        Log.i(TAG, "Start catch by FileObserver " + s);
                        sendStatusBrodcast(EXTRA_STATUS_STARTED);
                        Log.i(TAG, "Aquire screen lock");
                        mScreenLock.acquire();
                        mFileObserver.stopWatching();
                        mHandler.removeCallbacks(mWatchdogRunnable);
                        mWorkInProgress = false;
                        createOngoingNotification();
                    }
                }
            };
            mFileObserver.startWatching();
            if (!Utils.startServer(VNCServerService.this, password, parameter)) {
                mFileObserver.stopWatching();
                sendErrorBrodcast(EXTRA_ERROR_START_FAILED);
                mWorkInProgress = false;
                if (mServiceMode) {
                    stopSelf();
                }
                return;
            }
            if (!Utils.isRunning(VNCServerService.this)) {
                // wait max 5s
                mHandler.postDelayed(mWatchdogRunnable, 5000);
            } else {
                // we where faster then the FileObserver
                mFileObserver.stopWatching();
                Log.i(TAG, "Start catch");
                sendStatusBrodcast(EXTRA_STATUS_STARTED);
                Log.i(TAG, "Aquire screen lock");
                mScreenLock.acquire();
                mWorkInProgress = false;
                createOngoingNotification();
            }
        } catch (Exception e) {
            mFileObserver.stopWatching();
            sendErrorBrodcast(EXTRA_ERROR_START_FAILED);
            mWorkInProgress = false;
            if (mServiceMode) {
                stopSelf();
            }
        }
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void createOngoingNotification() {
        getNotificationManager().cancel(NOTIFICATION_ID);

        Notification.Builder notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_ongoing_title))
                .setContentText(Utils.getConnectedStatusString(this))
                .setSmallIcon(R.drawable.ic_server_on)
                .setShowWhen(true);

        PendingIntent stopIntent = PendingIntent.getService(this, NOTIFICATION_STOP_ID,
                new Intent(this, VNCServerService.class)
                    .setAction(VNCServerService.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT);
        notification.addAction(R.drawable.ic_server_off, getResources().getString(R.string.stop_server),
                stopIntent);

        Intent shoActivityIntent = new Intent(this, MainActivity.class);
        shoActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notification.setContentIntent(PendingIntent.getActivity(this, shoActivityIntent.hashCode(),
                shoActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        getNotificationManager().notify(NOTIFICATION_ID, notification.build());
    }

    private void createNotificationChannel() {
        CharSequence name = getString(R.string.notification_channel_name);
        String description = getString(R.string.notification_channel_description);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setSound(null, // silent
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        channel.setDescription(description);
        channel.setBlockable(true);
        getNotificationManager().createNotificationChannel(channel);
    }
}
