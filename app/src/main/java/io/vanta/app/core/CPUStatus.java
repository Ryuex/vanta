package io.vanta.app.core;

import java.util.Locale;

public abstract class CPUStatus {
    public static short[] getCurrentClockSpeeds() {
        int numProcessors = Runtime.getRuntime().availableProcessors();
        short[] clockSpeeds = new short[numProcessors];
        for (int i = 0; i < numProcessors; i++) {
            int currFreq = FileUtils.readInt("/sys/devices/system/cpu/cpu"+i+"/cpufreq/scaling_cur_freq");
            clockSpeeds[i] = (short)(currFreq / 1000);
        }
        return clockSpeeds;
    }

    public static short getMaxClockSpeed(int cpuIndex) {
        int maxFreq = FileUtils.readInt("/sys/devices/system/cpu/cpu"+cpuIndex+"/cpufreq/cpuinfo_max_freq");
        return (short)(maxFreq / 1000);
    }

    public static String formatClockSpeed(int clockSpeed) {
        return clockSpeed >= 1000 ? String.format(Locale.ENGLISH, "%.2f", clockSpeed / 1000.0f)+" GHz" : clockSpeed+" MHz";
    }
}