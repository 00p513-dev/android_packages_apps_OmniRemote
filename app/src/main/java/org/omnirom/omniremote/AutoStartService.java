package org.omnirom.omniremote;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

import static org.omnirom.omniremote.Utils.TAG;

public class AutoStartService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        if (Utils.DEBUG) Log.d(TAG, "AutoStartService onStartJob");
        if (Utils.isConnected() && Utils.isAutoStart(getApplicationContext()) && !Utils.isRunning(getApplicationContext())) {
            Intent start = Utils.getStartServerConfig(getApplicationContext());
            startServiceAsUser(start, UserHandle.CURRENT);
        }
        // update widget state
        WidgetHelper.updateWidgets(getApplicationContext());
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
