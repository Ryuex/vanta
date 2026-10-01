package io.vanta.app.core;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Iterator;

import io.vanta.app.R;

/**
 * Central record of the container startup pipeline.
 *
 * Every stage transition and every failure is written to logcat and to a small
 * file inside the application data directory, so a container that fails to start
 * can be diagnosed afterwards instead of remaining in an endless loading state.
 *
 * The recorded state is also used to render the startup stages shown by the
 * preloader (Preparing runtime / Preparing container / Extracting rootfs /
 * Starting XServer / Starting Wine / Running).
 */
public abstract class StartupStatus {
    public static final String TAG = "VantaStartup";
    private static final int MAX_GUEST_LINES = 300;
    private static final Object lock = new Object();

    public interface Listener {
        void onStageChanged(int textResId);
    }

    private static volatile int stageResId = R.string.startup_preparing_runtime;
    private static volatile String stageName = "init";
    private static volatile boolean running = false;
    private static volatile String failureStage = null;
    private static volatile String failureMessage = null;
    private static volatile Listener listener;
    private static volatile File logFile;
    private static final ArrayDeque<String> guestLines = new ArrayDeque<>();

    private StartupStatus() {}

    /** Defines where the startup log is persisted. Defaults to logcat only. */
    public static void init(Context context) {
        logFile = new File(context.getFilesDir(), "startup.log");
    }

    public static void setListener(Listener newListener) {
        listener = newListener;
    }

    /** Moves the pipeline to the given stage, logging the transition. */
    public static void stage(String name, int textResId) {
        synchronized (lock) {
            stageName = name;
            stageResId = textResId;
        }
        log("stage: "+name);
        Listener currentListener = listener;
        if (currentListener != null) currentListener.onStageChanged(textResId);
    }

    /** Marks the guest session as successfully started. */
    public static void running() {
        running = true;
        log("stage: running");
    }

    public static boolean isRunning() {
        return running;
    }

    public static int getStageResId() {
        return stageResId;
    }

    public static String getStageName() {
        return stageName;
    }

    /**
     * Records a startup failure. The first failure wins, later ones are only logged,
     * so the originally reported stage is never overwritten.
     */
    public static void fail(String stage, Throwable throwable) {
        fail(stage, describe(throwable), throwable);
    }

    public static void fail(String stage, String message) {
        fail(stage, message, null);
    }

    private static void fail(String stage, String message, Throwable throwable) {
        synchronized (lock) {
            if (failureStage == null) {
                failureStage = stage;
                failureMessage = message;
            }
        }
        if (throwable != null) Log.e(TAG, "failed at stage: "+stage, throwable);
        else Log.e(TAG, "failed at stage "+stage+": "+message);
        appendFile("FAILED at stage "+stage+": "+message);
    }

    /** Records a non fatal diagnostic. Startup continues, nothing is shown to the user. */
    public static void warn(String message) {
        if (message == null) return;
        Log.w(TAG, "warning: "+message);
        appendFile("warning: "+message);
    }

    public static boolean hasFailed() {
        return failureStage != null;
    }

    public static String getFailureStage() {
        return failureStage;
    }

    public static String getFailureMessage() {
        return failureMessage;
    }

    /** Captures a line of output produced by the guest process (Box64/Wine). */
    public static void appendGuestLine(String line) {
        if (line == null) return;
        synchronized (lock) {
            guestLines.addLast(line);
            while (guestLines.size() > MAX_GUEST_LINES) guestLines.removeFirst();
        }
    }

    /** Human readable report used by the startup error dialog and the log file. */
    public static String buildReport() {
        StringBuilder builder = new StringBuilder();
        synchronized (lock) {
            builder.append("stage: ").append(failureStage != null ? failureStage : stageName).append('\n');
            if (failureMessage != null) builder.append("error: ").append(failureMessage).append('\n');
            if (!guestLines.isEmpty()) {
                builder.append("\nguest output:\n");
                Iterator<String> iterator = guestLines.iterator();
                while (iterator.hasNext()) builder.append(iterator.next()).append('\n');
            }
        }
        return builder.toString();
    }

    public static void reset() {
        synchronized (lock) {
            stageResId = R.string.startup_preparing_runtime;
            stageName = "init";
            running = false;
            failureStage = null;
            failureMessage = null;
            guestLines.clear();
        }
    }

    private static String describe(Throwable throwable) {
        if (throwable == null) return "unknown error";
        String message = throwable.getMessage();
        return throwable.getClass().getName()+(message != null && !message.isEmpty() ? ": "+message : "");
    }

    private static void log(String message) {
        Log.i(TAG, message);
        appendFile(message);
    }

    /**
     * Startup diagnostics must never depend on external storage permissions,
     * so the log always lives inside the application data directory.
     */
    private static void appendFile(String message) {
        File file = logFile;
        if (file == null) return;
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(System.currentTimeMillis()+" "+message+"\n");
        }
        catch (IOException e) {}
    }
}
