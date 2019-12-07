package org.omnirom.omniremote;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;

public class Utils {
    public static final String TAG = "OmniRemote";

    public static File getRootDir(Context context) {
        return new File("/system/bin/");
    }

    public static File getStateDir(Context context) {
        return context.getFilesDir();
    }

    public static File getStartPath(Context context) {
        return new File(getRootDir(context), "vncflinger");
    }

    public static File getPidPath(Context context) {
        return new File(getStateDir(context), "vncflinger.pid");
    }

    public static File getPasswordPath(Context context) {
        return new File(getStateDir(context), "vncflinger.auth");
    }

    public static File getVNCPasswordPath(Context context) {
        return new File(getRootDir(context), "vncpasswd");
    }

    public static int getRunningPid(Context context) {
        if (getPidPath(context).exists()) {
            try {
                BufferedReader pidFile = new BufferedReader(
                        new InputStreamReader(new FileInputStream(getPidPath(context))));
                String pidString = pidFile.readLine();
                pidFile.close();
                int pid = Integer.valueOf(pidString);
                return pid;
            } catch (IOException | NumberFormatException e) {
                Log.e(TAG, "getRunningPid", e);
            }
        }
        return -1;
    }

    public static boolean killRunning(Context context) throws Exception {
        if (getRunningPid(context) != -1) {
            Log.d(TAG, "kill " + getRunningPid(context));
            Process p = Runtime.getRuntime().exec("kill " +
                    getRunningPid(context));
            p.waitFor();
            return true;
        }
        return false;
    }

    public static boolean isConnected() {
        return getIPAddress() != null;
    }

    public static boolean isInstalled(Context context) {
        return getStartPath(context).exists();
    }

    public static boolean isRunning(Context context) {
        return getPidPath(context).exists();
    }

    public static String getIPAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (intf.getDisplayName().startsWith("wlan") || intf.getDisplayName().startsWith("rndis")) {
                    List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                    for (InetAddress addr : addrs) {
                        if (!addr.isLoopbackAddress()) {
                            String host = addr.getHostAddress();
                            if (host.equals("127.0.0.1")) {
                                continue;
                            }
                            boolean isIPv4 = host.indexOf(':') == -1;
                            boolean isIPv6 = !isIPv4;
                            if (isIPv4) {
                                return host;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getIPAddress", e);
        }
        return null;
    }

    private static boolean writePasswordFile(Context context, String password) throws Exception {
        File outFile = getPasswordPath(context);
        if (!outFile.getParentFile().exists()) {
            if (!outFile.getParentFile().mkdirs()) {
                Log.d(TAG, "mkdir failed " + outFile.getParent());
                return false;
            }
        } else if (outFile.exists()) {
            outFile.delete();
        }

        Log.i(TAG, "writePasswordFile " + getVNCPasswordPath(context).getAbsolutePath() +
                " -g " + password + " > " + outFile.getAbsolutePath());
        Process p = Runtime.getRuntime().exec(getVNCPasswordPath(context).getAbsolutePath() +
                        " -g " + password + " " + outFile.getAbsolutePath(),
                new String[]{}, getStartPath(context).getParentFile());
        int result = p.waitFor();
        return result == 0;
    }

    public static boolean startServer(Context context, String password,
                                      @NonNull List<String> parameter) throws Exception {
        if (isRunning(context)) {
            return false;
        }

        if (!isInstalled(context)) {
            return false;
        }

        if (!TextUtils.isEmpty(password)) {
            if (!Utils.writePasswordFile(context, password)) {
                return false;
            }
        }
        String paramterString = "";
        if (parameter.size() != 0) {
            paramterString = TextUtils.join(" ", parameter);
        }
        Log.d(TAG, "start " + getStartPath(context).getAbsolutePath()
                + " -pid " + getPidPath(context) + " " + paramterString);
        Runtime.getRuntime().exec(getStartPath(context).getAbsolutePath()
                        + " -pid " + getPidPath(context) + " " + paramterString,
                new String[]{}, getStartPath(context).getParentFile());
        return true;
    }

    public static boolean stopServer(final Context context) throws Exception {
        if (!isRunning(context)) {
            return false;
        }
        boolean killed = killRunning(context);

        if (getPidPath(context).exists()) {
            getPidPath(context).delete();
        }
        if (getPasswordPath(context).exists()) {
            getPasswordPath(context).delete();
        }
        return killed;
    }
}
