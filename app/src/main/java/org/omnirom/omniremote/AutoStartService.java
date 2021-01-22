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
        if (Utils.isConnected() && !Utils.isRunning(getApplicationContext())) {
            if (Utils.DEBUG) Log.d(TAG, "onStartJob");
            Intent start = Utils.getStartServerConfig(getApplicationContext());
            startServiceAsUser(start, UserHandle.CURRENT);
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
