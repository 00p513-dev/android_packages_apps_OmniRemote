/*
 *  Copyright (C) 2021 The OmniROM Project
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

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

public class ShareWidgetProvider extends AppWidgetProvider {
    private static final String TAG = Utils.TAG;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        // track which widgets were created, since there's a bug in the android system that lets
        // stale app widget ids stick around
        WidgetHelper.setWidgetExistsPreference(context, appWidgetIds);
        WidgetHelper.updateWidgets(context, appWidgetIds);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        minWidth = Math.round(minWidth * context.getResources().getDisplayMetrics().density);
        WidgetHelper.updateWidget(context, widgetManager, appWidgetId, minWidth);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        for (int id : appWidgetIds) {
            if (Utils.DEBUG)  Log.d(TAG, "clearWidgetExistsPreference " + id);
            WidgetHelper.clearWidgetExistsPreference(context, id);
        }
    }

    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        int i = 0;
        for (int oldWidgetId : oldWidgetIds) {
            if (Utils.DEBUG) Log.d(TAG, "remapWidgetExistsPreference " + oldWidgetId + " => " + newWidgetIds);
            WidgetHelper.remapWidgetExistsPreference(context, oldWidgetId, newWidgetIds[i]);
            i++;
        }
    }

    @Override
    public void onReceive(@NonNull final Context context, @NonNull Intent intent) {
        super.onReceive(context, intent);
    }
}
