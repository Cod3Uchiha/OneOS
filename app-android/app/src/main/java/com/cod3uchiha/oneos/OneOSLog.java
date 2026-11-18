package com.cod3uchiha.oneos;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persistent, timestamped installer diagnostics stored in app-private files. */
public final class OneOSLog {
    private static final String TAG = "OneOS";
    private static File logFile;

    private OneOSLog() { }

    public static synchronized void init(File filesDir) {
        File dir = new File(filesDir, "logs");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Could not create private log directory: " + dir);
            return;
        }
        logFile = new File(dir, "oneos-install.log");
        write("INFO", "LOGGER", "Log session started; app-private diagnostics enabled.");
    }

    public static synchronized void info(String stage, String message) { write("INFO", stage, message); }
    public static synchronized void warn(String stage, String message) { write("WARN", stage, message); }
    public static synchronized void error(String stage, String message, Throwable error) {
        write("ERROR", stage, message + (error == null ? "" : " | " + Log.getStackTraceString(error)));
    }

    public static synchronized File getLogFile() { return logFile; }

    private static void write(String level, String stage, String message) {
        String time = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
        String line = time + " [" + level + "] [" + stage + "] " + message + "\n";
        if ("ERROR".equals(level)) Log.e(TAG, line); else if ("WARN".equals(level)) Log.w(TAG, line); else Log.i(TAG, line);
        if (logFile == null) return;
        try (FileOutputStream out = new FileOutputStream(logFile, true)) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (Exception e) {
            Log.e(TAG, "Unable to persist diagnostics", e);
        }
    }
}
