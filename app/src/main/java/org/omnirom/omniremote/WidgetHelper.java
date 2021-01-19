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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;

public class WidgetHelper {
    private static final String WIDGET_EXISTS_PREFIX = "widget_";

    public static void updateWidgets(Context context) {
        int[] appWidgetIds = findAppWidgetIds(context);
        updateWidgets(context, appWidgetIds);
    }

    public static void updateWidgets(Context context, int[] appWidgetIds) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int appWidgetId : appWidgetIds) {
            if (!getWidgetExistsPreference(context, appWidgetId)) {
                continue;
            }

            Bundle options = manager.getAppWidgetOptions(appWidgetId);
            int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            minWidth = Math.round(minWidth * context.getResources().getDisplayMetrics().density);
            updateWidget(context, manager, appWidgetId, minWidth);
        }
    }

    public static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId, int minWidth) {
        final boolean minimalSize = minWidth < context.getResources().getDimensionPixelSize(R.dimen.widget_width);
        final boolean serviceRunning = Utils.isRunning(context);
        final PendingIntent activity = getActivityIntent(context);

        RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_share);
        updateViews.setImageViewResource(R.id.share_badge_image, serviceRunning ? R.drawable.ic_widget_running : R.drawable.ic_widget_stopped);
        if (!Utils.isConnected()) {
            updateViews.setTextViewText(R.id.share_badge_status_ip, context.getResources().getString(R.string.not_connected));
        } else {
            if (serviceRunning) {
                if (minimalSize) {
                    updateViews.setTextViewText(R.id.share_badge_status_ip, context.getResources().getString(R.string.status_running));
                } else {
                    updateViews.setTextViewText(R.id.share_badge_status_ip, Utils.getConnectedStatusString(context));
                }
            } else {
                updateViews.setTextViewText(R.id.share_badge_status_ip, context.getResources().getString(R.string.status_stopped));
            }
            if (!Utils.isFirstStartDone(context)) {
                updateViews.setOnClickPendingIntent(R.id.share_badge_image, activity);
            } else {
                PendingIntent pendingIntent = getServiceIntent(context, serviceRunning);
                if (pendingIntent != null) {
                    updateViews.setOnClickPendingIntent(R.id.share_badge_image, pendingIntent);
                }
            }
        }
        updateViews.setOnClickPendingIntent(R.id.share_badge_status_ip, activity);
        manager.updateAppWidget(appWidgetId, updateViews);
    }

    private static PendingIntent getServiceIntent(Context context, boolean serviceRunning) {
        if (serviceRunning) {
            Intent stop = new Intent(context, VNCServerService.class);
            stop.setAction(VNCServerService.ACTION_STOP);
            return PendingIntent.getService(context,
                    0 /* no requestCode */, stop, PendingIntent.FLAG_UPDATE_CURRENT);
        } else if (Utils.isConnected()) {
            Intent start = Utils.getStartServerConfig(context);
            return PendingIntent.getService(context,
                    0 /* no requestCode */, start, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return null;
    }

    private static PendingIntent getActivityIntent(Context context) {
        Intent intent = new Intent();
        intent.setClass(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context,
                0 /* no requestCode */, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static int[] findAppWidgetIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, ShareWidgetProvider.class);
        return manager.getAppWidgetIds(widget);
    }

    public static boolean getWidgetExistsPreference(Context context, int appWidgetId) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String widgetExists = WIDGET_EXISTS_PREFIX.concat(Integer.toString(appWidgetId));

        return sharedPrefs.getBoolean(widgetExists, false);
    }

    public static void setWidgetExistsPreference(Context context, int[] appWidgetIds) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        for (int appWidgetId : appWidgetIds) {
            String widgetExists = WIDGET_EXISTS_PREFIX.concat(Integer.toString(appWidgetId));
            editor.putBoolean(widgetExists, true);
        }
        editor.commit();
    }
    public static void clearWidgetExistsPreference(Context context, int appWidgetId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().remove(WIDGET_EXISTS_PREFIX.concat(Integer.toString(appWidgetId))).commit();
    }

    public static void remapWidgetExistsPreference(Context context, int oldId, int newId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(WIDGET_EXISTS_PREFIX.concat(Integer.toString(newId)), true);
        prefs.edit().remove(WIDGET_EXISTS_PREFIX.concat(Integer.toString(oldId))).commit();
    }
}
