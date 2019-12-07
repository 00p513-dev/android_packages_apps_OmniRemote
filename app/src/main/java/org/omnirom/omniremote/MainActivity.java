package org.omnirom.omniremote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.omniremote.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = Utils.TAG;
    private Handler mHandler = new Handler();
    private List<String> mParameters = new ArrayList<>();
    private String mStartPort;
    private String mStartPassword;

    private Runnable mWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            if (Utils.isRunning(MainActivity.this)) {
                mHandler.postDelayed(mWatchdogRunnable, 5000);
            }
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "onReceive " + intent.getAction());
            if (intent.getAction().equals(VNCServerService.ACTION_ERROR)) {
                if (intent.hasExtra(VNCServerService.EXTRA_ERROR)) {
                    String extraError = intent.getStringExtra(VNCServerService.EXTRA_ERROR);
                    if (extraError.equals(VNCServerService.EXTRA_ERROR_START_FAILED)) {
                        findViewById(R.id.start_button_float).setEnabled(true);
                        showProgress(false);
                        setStatusMessage(getResources().getString(R.string.start_failed));
                    }
                    if (extraError.equals(VNCServerService.EXTRA_ERROR_STOP_FAILED)) {
                        findViewById(R.id.start_button_float).setEnabled(true);
                        showProgress(false);
                        setStatusMessage(getResources().getString(R.string.stop_failed));
                    }
                }
            }
            if (intent.getAction().equals(VNCServerService.ACTION_STATUS)) {
                if (intent.hasExtra(VNCServerService.EXTRA_STATUS)) {
                    String extraStatus = intent.getStringExtra(VNCServerService.EXTRA_STATUS);
                    if (extraStatus.equals(VNCServerService.EXTRA_STATUS_STARTED)) {
                        findViewById(R.id.start_button_float).setEnabled(true);
                        showProgress(false);
                        updateStatus();
                        startWatchdog();
                    }
                    if (extraStatus.equals(VNCServerService.EXTRA_STATUS_STOPPED)) {
                        findViewById(R.id.start_button_float).setEnabled(true);
                        showProgress(false);
                        updateStatus();
                        stopWatchDog();
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().setElevation(0);

        findViewById(R.id.start_button_float).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Utils.isRunning(MainActivity.this)) {
                    setStatusMessage(getResources().getString(R.string.stopping));
                    findViewById(R.id.start_button_float).setEnabled(false);
                    showProgress(true);

                    Intent start = new Intent(MainActivity.this, VNCServerService.class);
                    start.setAction(VNCServerService.ACTION_STOP);
                    startService(start);
                } else {
                    startServer();
                }
            }
        });

        findViewById(R.id.more_params_info).

                setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(MainActivity.this, HelpActivity.class));
                    }
                });

        findViewById(R.id.cmd_line_info).

                setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        createParamterList();
                        String cmdline = Utils.getStartPath(MainActivity.this).getAbsolutePath() + "\n" + getParameterString("\n");
                        AlertDialog.Builder a = new AlertDialog.Builder(MainActivity.this);
                        a.setMessage(cmdline);
                        a.setTitle(R.string.cmd_line_dialog_title);
                        a.setPositiveButton(android.R.string.ok, null);
                        a.create();
                        a.show();
                    }
                });

        restorePreferences();
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(VNCServerService.ACTION_ERROR);
        filter.addAction(VNCServerService.ACTION_STATUS);
        registerReceiver(mReceiver, filter);

        updateStatus();
        if (Utils.isRunning(this)) {
            startWatchdog();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopWatchDog();
        savePreferences();
        try {
            unregisterReceiver(mReceiver);
        } catch (Exception e) {
            // ignore
        }
    }

    private void startServer() {
        if (Utils.isRunning(this)) {
            return;
        }
        if (!Utils.isConnected()) {
            setStatusMessage(getResources().getString(R.string.not_connected));
            return;
        }

        mStartPort = null;
        mStartPassword = null;
        createParamterList();

        String port = ((EditText) findViewById(R.id.port_edit)).getText().toString();
        if (!TextUtils.isEmpty(port)) {
            mStartPort = port;
        }
        String password = ((EditText) findViewById(R.id.password_edit)).getText().toString();
        if (!TextUtils.isEmpty(password)) {
            mStartPassword = password;
        }

        savePreferences();

        setStatusMessage(getResources().getString(R.string.starting));
        findViewById(R.id.start_button_float).setEnabled(false);
        showProgress(true);

        Intent start = new Intent(this, VNCServerService.class);
        start.setAction(VNCServerService.ACTION_START);
        start.putExtra("parameter", mParameters.toArray(new String[mParameters.size()]));
        start.putExtra("password", password);
        startService(start);
    }

    private String getPort() {
        if (!TextUtils.isEmpty(mStartPort)) {
            return mStartPort;
        }
        return "5900";
    }

    private String getPassword() {
        if (!TextUtils.isEmpty(mStartPassword)) {
            return mStartPassword;
        }
        return "";
    }

    private void updateButton() {
        if (Utils.isRunning(this)) {
            ((ImageView) findViewById(R.id.start_button_float)).setBackgroundTintList(
                    getColorStateList(R.color.power_on_color));
        } else {
            ((ImageView) findViewById(R.id.start_button_float)).setBackgroundTintList(
                    getColorStateList(R.color.power_off_color));
        }
    }

    private void setStatusMessage(String msg) {
        if (!TextUtils.isEmpty(msg)) {
            ((TextView) findViewById(R.id.status_text)).setText(msg);
            ((TextView) findViewById(R.id.pid_text)).setText("");
        } else {
            if (Utils.isRunning(this)) {
                ((TextView) findViewById(R.id.status_text)).setText(getResources().getText(R.string.status_running));
                ((TextView) findViewById(R.id.pid_text)).setText(String.valueOf(Utils.getRunningPid(this)));
            } else {
                ((TextView) findViewById(R.id.status_text)).setText(getResources().getText(R.string.status_stopped));
                ((TextView) findViewById(R.id.pid_text)).setText("");
            }
        }
    }

    private void setConnectedMessage() {
        if (Utils.isRunning(this)) {
            ((TextView) findViewById(R.id.interface_text)).setText(Utils.getIPAddress());
            ((TextView) findViewById(R.id.port_text)).setText(getPort());
            ((TextView) findViewById(R.id.password_text)).setText(getPassword());
        } else {
            ((TextView) findViewById(R.id.interface_text)).setText("");
            ((TextView) findViewById(R.id.port_text)).setText("");
            ((TextView) findViewById(R.id.password_text)).setText("");
        }
    }

    private String getParameterString(String delim) {
        return TextUtils.join(delim, mParameters);
    }

    private void savePreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("port", ((TextView) findViewById(R.id.port_edit)).getText().toString());
        editor.putString("password", ((TextView) findViewById(R.id.password_edit)).getText().toString());
        editor.putString("disconnect_time", ((TextView) findViewById(R.id.disconnect_time_edit)).getText().toString());
        editor.putString("more", ((TextView) findViewById(R.id.more_params_edit)).getText().toString().trim());
        editor.putString("idle_time", ((TextView) findViewById(R.id.idle_time_edit)).getText().toString());
        editor.putString("frame_rate", ((TextView) findViewById(R.id.frame_rate_edit)).getText().toString());
        editor.commit();
    }

    private void restorePreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ((TextView) findViewById(R.id.port_edit)).setText(prefs.getString("port", ""));
        ((TextView) findViewById(R.id.password_edit)).setText(prefs.getString("password", ""));
        ((TextView) findViewById(R.id.disconnect_time_edit)).setText(prefs.getString("disconnect_time", ""));
        ((TextView) findViewById(R.id.more_params_edit)).setText(prefs.getString("more", ""));
        ((TextView) findViewById(R.id.idle_time_edit)).setText(prefs.getString("idle_time", ""));
        ((TextView) findViewById(R.id.frame_rate_edit)).setText(prefs.getString("frame_rate", ""));
    }

    private void updateStatus() {
        Log.i(TAG, "updateStatus");
        if (!Utils.isConnected()) {
            setStatusMessage(getResources().getString(R.string.not_connected));
        } else {
            setStatusMessage(null);
        }
        setConnectedMessage();
        updateButton();
    }

    private void startWatchdog() {
        Log.i(TAG, "startWatchdog");
        mHandler.postDelayed(mWatchdogRunnable, 5000);
    }

    private void stopWatchDog() {
        Log.i(TAG, "stopWatchDog");
        mHandler.removeCallbacks(mWatchdogRunnable);
    }

    private void showProgress(boolean show) {
        if (show) {
            findViewById(R.id.progress).setVisibility(View.VISIBLE);
            findViewById(R.id.progress_overlay).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.progress).setVisibility(View.GONE);
            findViewById(R.id.progress_overlay).setVisibility(View.GONE);
        }
    }

    private void createParamterList() {
        mParameters.clear();

        String port = ((EditText) findViewById(R.id.port_edit)).getText().toString();
        if (!TextUtils.isEmpty(port)) {
            mParameters.add("-rfbport=" + port);
        }
        String password = ((EditText) findViewById(R.id.password_edit)).getText().toString();
        if (!TextUtils.isEmpty(password)) {
            mParameters.add("-SecurityTypes=VncAuth");
            mParameters.add("-PasswordFile=" + Utils.getPasswordPath(this).getAbsolutePath());
        } else {
            mParameters.add("-SecurityTypes=None");
        }
        String disconnectTimeout = ((EditText) findViewById(R.id.disconnect_time_edit)).getText().toString();
        if (!TextUtils.isEmpty(disconnectTimeout)) {
            mParameters.add("-MaxDisconnectionTime=" + disconnectTimeout);
        }
        String idleTimeout = ((EditText) findViewById(R.id.idle_time_edit)).getText().toString();
        if (!TextUtils.isEmpty(idleTimeout)) {
            mParameters.add("-MaxIdleTime=" + idleTimeout);
        }
        String frameRate = ((EditText) findViewById(R.id.frame_rate_edit)).getText().toString();
        if (!TextUtils.isEmpty(frameRate)) {
            mParameters.add("-FrameRate=" + frameRate);
        }
        String moreParams = ((EditText) findViewById(R.id.more_params_edit)).getText().toString().trim();
        if (!TextUtils.isEmpty(moreParams)) {
            String[] params = moreParams.split(" ");
            if (params.length != 0) {
                mParameters.addAll(Arrays.asList(params));
            }
        }
    }
}
