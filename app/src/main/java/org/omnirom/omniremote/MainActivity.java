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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = Utils.TAG;
    private Handler mHandler = new Handler();
    private List<String> mParameters = new ArrayList<>();
    private String mStartPort;
    private String mStartPassword;
    private AlertDialog mAboutDialog;

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
        getActionBar().setElevation(0);
        getActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);

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
            ((ImageView) findViewById(R.id.start_button_float)).setBackgroundResource(R.drawable.power_on);
        } else {
            ((ImageView) findViewById(R.id.start_button_float)).setBackgroundResource(R.drawable.power_off);
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
            ((TextView) findViewById(R.id.interface_text)).setText(Utils.getIPAddress() + " : " + getPort());
        } else {
            ((TextView) findViewById(R.id.interface_text)).setText("");
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
        editor.putString("more", ((TextView) findViewById(R.id.more_params_edit)).getText().toString().trim());
        editor.commit();
    }

    private void restorePreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        ((TextView) findViewById(R.id.port_edit)).setText(prefs.getString("port", ""));
        ((TextView) findViewById(R.id.password_edit)).setText(prefs.getString("password", ""));
        ((TextView) findViewById(R.id.more_params_edit)).setText(prefs.getString("more", ""));
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
        String moreParams = ((EditText) findViewById(R.id.more_params_edit)).getText().toString().trim();
        if (!TextUtils.isEmpty(moreParams)) {
            String[] params = moreParams.split(" ");
            if (params.length != 0) {
                mParameters.addAll(Arrays.asList(params));
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.menu_item_about:
                showAboutDialog();
                break;
            default:
                break;
        }
        return true;
    }

    private void showAboutDialog() {
        if (mAboutDialog != null) {
            mAboutDialog.dismiss();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setPositiveButton(android.R.string.ok, null)
                .setTitle(R.string.menu_item_about)
                .setView(createDialogView())
                .setPositiveButton(android.R.string.ok, null);

        mAboutDialog = builder.create();
        mAboutDialog.show();
    }

    private View createDialogView() {
        final LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater
                .inflate(R.layout.about_dialog, null);

        view.findViewById(R.id.tigervnc_link).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse("https://tigervnc.org/"));
                startActivity(i);
            }
        });
        return view;
    }
}
