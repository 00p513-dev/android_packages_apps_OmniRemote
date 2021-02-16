/*
 *  Copyright (C) 2021 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
;import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static org.omnirom.omniremote.Utils.TAG;

public class BootReceiver extends BroadcastReceiver {

    private void scheduleCheckUpdates(Context context) {
        if (Utils.DEBUG) Log.d(TAG, "scheduleCheckUpdates");
        ComponentName serviceComponent = new ComponentName(context, AutoStartService.class);
        JobInfo.Builder builder = new JobInfo.Builder(0, serviceComponent);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        // TODO make configurable start delay
        builder.setMinimumLatency(10000);
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(builder.build());
    }

    private void cancelCheckForUpdates(Context context) {
        if (Utils.DEBUG) Log.d(TAG, "cancelCheckForUpdates");
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancelAll();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            if (context != null) {
                if (Utils.isFirstStartDone(context)){
                    if (Utils.DEBUG) Log.d(TAG, "onReceive " + intent.getAction());
                    scheduleCheckUpdates(context);
                }
            }
        }
    }
}