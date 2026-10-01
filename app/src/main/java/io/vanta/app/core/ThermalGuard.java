package io.vanta.app.core;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import io.vanta.app.R;

public final class ThermalGuard {
    public enum State {
        UNAVAILABLE, NORMAL, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN;

        public boolean isAtLeast(State other) {
            return this != UNAVAILABLE && ordinal() >= other.ordinal();
        }
    }

    public interface Listener {
        void onThermalStateChanged(State state);
        void onThermalWarning(State state);
    }

    public static final class SessionSummary {
        public final State highestState;
        public final int severeEvents;
        public final boolean thermalFpsLimitApplied;
        public final int initialFpsTarget;
        public final int lowestTemporaryFpsTarget;

        private SessionSummary(State highestState, int severeEvents, boolean thermalFpsLimitApplied,
                               int initialFpsTarget, int lowestTemporaryFpsTarget) {
            this.highestState = highestState;
            this.severeEvents = severeEvents;
            this.thermalFpsLimitApplied = thermalFpsLimitApplied;
            this.initialFpsTarget = initialFpsTarget;
            this.lowestTemporaryFpsTarget = lowestTemporaryFpsTarget;
        }
    }

    private static final String TAG = "VantaThermalGuard";
    private static final long MODERATE_WARNING_DELAY_MS = 30_000L;
    private static final long SEVERE_WARNING_DELAY_MS = 5_000L;
    private static final long RECOVERY_RESET_DELAY_MS = 120_000L;

    private final Context appContext;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable warningTask = this::showSustainedWarning;
    private final Runnable recoveryTask = this::resetAfterRecovery;
    private final Runnable severePressureTask = this::markSeverePressureSustained;
    private PowerManager powerManager;
    private PowerManager.OnThermalStatusChangedListener thermalListener;
    private State state = State.UNAVAILABLE;
    private State highestState = State.UNAVAILABLE;
    private State lastWarnedState;
    private int severeEvents;
    private boolean started;
    private boolean thermalFpsLimitApplied;
    private boolean fpsTargetRecorded;
    private boolean severePressureSustained;
    private int initialFpsTarget;
    private int lowestTemporaryFpsTarget;

    public ThermalGuard(Context context, Listener listener) {
        appContext = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (started) return;
        started = true;
        if (!PreferenceManager.getDefaultSharedPreferences(appContext)
                .getBoolean("thermal_guard_enabled", true)) {
            state = State.UNAVAILABLE;
            Log.i(TAG, "session started; Thermal Guard disabled in settings");
            listener.onThermalStateChanged(state);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            state = State.UNAVAILABLE;
            Log.i(TAG, "session started; Android thermal status API unavailable on API " + Build.VERSION.SDK_INT);
            listener.onThermalStateChanged(state);
            return;
        }

        powerManager = (PowerManager)appContext.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            state = State.UNAVAILABLE;
            Log.w(TAG, "session started; PowerManager unavailable");
            listener.onThermalStateChanged(state);
            return;
        }

        state = fromAndroidStatus(powerManager.getCurrentThermalStatus());
        highestState = state;
        if (state.isAtLeast(State.SEVERE)) severeEvents = 1;
        Log.i(TAG, "session started; thermal status=" + state);
        listener.onThermalStateChanged(state);
        thermalListener = status -> handler.post(() -> updateState(fromAndroidStatus(status)));
        powerManager.addThermalStatusListener(thermalListener);
        scheduleWarning(state);
        if (state.isAtLeast(State.SEVERE)) {
            handler.postDelayed(severePressureTask, SEVERE_WARNING_DELAY_MS);
        }
    }

    public void stop() {
        if (!started) return;
        started = false;
        handler.removeCallbacks(warningTask);
        handler.removeCallbacks(recoveryTask);
        handler.removeCallbacks(severePressureTask);
        if (powerManager != null && thermalListener != null) {
            powerManager.removeThermalStatusListener(thermalListener);
        }
        thermalListener = null;
        powerManager = null;
        Log.i(TAG, "session stopped");
    }

    public State getState() {
        return state;
    }

    public static int getStateLabelResource(State state) {
        switch (state) {
            case NORMAL: return R.string.thermal_normal;
            case LIGHT: return R.string.thermal_light;
            case MODERATE: return R.string.thermal_moderate;
            case SEVERE: return R.string.thermal_severe;
            case CRITICAL: return R.string.thermal_critical;
            case EMERGENCY: return R.string.thermal_emergency;
            case SHUTDOWN: return R.string.thermal_shutdown;
            default: return R.string.thermal_unavailable;
        }
    }

    public boolean isAvailable() {
        return state != State.UNAVAILABLE;
    }

    public boolean hasSustainedSeverePressure() {
        return severePressureSustained;
    }

    public SessionSummary getSessionSummary() {
        return new SessionSummary(highestState, severeEvents, thermalFpsLimitApplied,
                initialFpsTarget, lowestTemporaryFpsTarget);
    }

    public void recordFpsLimit(int initialTarget, int temporaryTarget, boolean thermal) {
        if (!fpsTargetRecorded) {
            initialFpsTarget = initialTarget;
            fpsTargetRecorded = true;
        }
        if (temporaryTarget > 0 && (thermal || temporaryTarget != initialTarget) &&
                (lowestTemporaryFpsTarget == 0 || temporaryTarget < lowestTemporaryFpsTarget)) {
            lowestTemporaryFpsTarget = temporaryTarget;
        }
        if (thermal && temporaryTarget > 0) thermalFpsLimitApplied = true;
        if (thermal && temporaryTarget > 0) {
            Log.i(TAG, "temporary FPS limit selected: " + temporaryTarget + " (user target=" + initialTarget + ")");
        }
    }

    private void updateState(State newState) {
        if (!started || newState == state) return;
        State oldState = state;
        state = newState;
        if (!newState.isAtLeast(State.SEVERE)) {
            handler.removeCallbacks(severePressureTask);
            severePressureSustained = false;
        }
        else if (!oldState.isAtLeast(State.SEVERE)) {
            handler.removeCallbacks(severePressureTask);
            handler.postDelayed(severePressureTask, SEVERE_WARNING_DELAY_MS);
        }
        if (newState != State.UNAVAILABLE && newState.ordinal() > highestState.ordinal()) highestState = newState;
        if (!oldState.isAtLeast(State.SEVERE) && newState.isAtLeast(State.SEVERE)) severeEvents++;
        Log.i(TAG, "thermal status changed: " + oldState + " -> " + newState);
        listener.onThermalStateChanged(newState);

        if (newState == State.NORMAL || newState == State.LIGHT) {
            handler.removeCallbacks(warningTask);
            handler.removeCallbacks(recoveryTask);
            handler.postDelayed(recoveryTask, RECOVERY_RESET_DELAY_MS);
        }
        else {
            handler.removeCallbacks(recoveryTask);
            scheduleWarning(newState);
        }
    }

    private void markSeverePressureSustained() {
        if (!started || !state.isAtLeast(State.SEVERE) || severePressureSustained) return;
        severePressureSustained = true;
        Log.i(TAG, "severe thermal pressure sustained");
    }

    private void scheduleWarning(State targetState) {
        handler.removeCallbacks(warningTask);
        if (targetState.ordinal() <= State.LIGHT.ordinal()) return;
        if (lastWarnedState != null && targetState.ordinal() <= lastWarnedState.ordinal()) return;
        long delay = targetState.ordinal() >= State.CRITICAL.ordinal() ? 0L :
                targetState == State.SEVERE ? SEVERE_WARNING_DELAY_MS : MODERATE_WARNING_DELAY_MS;
        handler.postDelayed(warningTask, delay);
    }

    private void showSustainedWarning() {
        if (!started || state.ordinal() <= State.LIGHT.ordinal()) return;
        if (!PreferenceManager.getDefaultSharedPreferences(appContext)
                .getBoolean("thermal_guard_enabled", true) ||
                !PreferenceManager.getDefaultSharedPreferences(appContext)
                .getBoolean("thermal_warnings_enabled", true)) return;
        if (lastWarnedState != null && state.ordinal() <= lastWarnedState.ordinal()) return;
        lastWarnedState = state;
        Log.w(TAG, "thermal warning triggered: " + state);
        listener.onThermalWarning(state);
    }

    private void resetAfterRecovery() {
        if (!started || (state != State.NORMAL && state != State.LIGHT)) return;
        if (lastWarnedState != null) Log.i(TAG, "thermal recovery stable; warning hysteresis reset");
        lastWarnedState = null;
        severePressureSustained = false;
    }

    private static State fromAndroidStatus(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return State.NORMAL;
            case PowerManager.THERMAL_STATUS_LIGHT: return State.LIGHT;
            case PowerManager.THERMAL_STATUS_MODERATE: return State.MODERATE;
            case PowerManager.THERMAL_STATUS_SEVERE: return State.SEVERE;
            case PowerManager.THERMAL_STATUS_CRITICAL: return State.CRITICAL;
            case PowerManager.THERMAL_STATUS_EMERGENCY: return State.EMERGENCY;
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return State.SHUTDOWN;
            default: return State.UNAVAILABLE;
        }
    }
}
