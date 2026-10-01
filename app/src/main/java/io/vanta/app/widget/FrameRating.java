package io.vanta.app.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.vanta.app.R;
import io.vanta.app.box64.Box64Utils;
import io.vanta.app.core.CPUStatus;
import io.vanta.app.core.StringUtils;
import io.vanta.app.core.ThermalGuard;

import java.util.Locale;

public class FrameRating extends FrameLayout implements Runnable {
    public enum Mode {DISABLED, SIMPLE, FULL}
    private long lastTime = 0;
    private short frameCount = 0;
    private float lastFPS = 0;
    private final LinearLayout fpsPanel;
    private final LinearLayout gpuPanel;
    private final LinearLayout ramPanel;
    private final LinearLayout cpuPanel;
    private final TextView completeTelemetry;
    private Mode mode = Mode.SIMPLE;
    private ActivityManager activityManager;
    private ActivityManager.MemoryInfo memoryInfo;
    private String cpuInfo = null;
    private byte tick = 0;
    private String resolution = "";
    private String fpsLimit = "";
    private String performancePolicy = "";
    private String gpuBackend = "";
    private String dxWrapper = "";
    private String wineVersion = "";
    private String box64Version = "";
    private String wrapperIdentity;
    private String wrapperBackend;
    private String maliProfile;
    private String thermalStatus;
    private String selectedVulkanDriver;
    private String loadedVulkanLibrary;

    public FrameRating(Context context) {
        this(context, null);
    }

    public FrameRating(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FrameRating(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        View view = LayoutInflater.from(context).inflate(R.layout.frame_rating, this, false);
        fpsPanel = view.findViewById(R.id.LLFPSPanel);
        gpuPanel = view.findViewById(R.id.LLGPUPanel);
        ramPanel = view.findViewById(R.id.LLRAMPanel);
        cpuPanel = view.findViewById(R.id.LLCPUPanel);
        completeTelemetry = view.findViewById(R.id.TVCompleteTelemetry);
        addView(view);
        setupPanels();
    }

    private void setupPanels() {
        switch (mode) {
            case DISABLED:
                fpsPanel.setVisibility(GONE);
                gpuPanel.setVisibility(GONE);
                ramPanel.setVisibility(GONE);
                cpuPanel.setVisibility(GONE);
                completeTelemetry.setVisibility(GONE);

                activityManager = null;
                memoryInfo = null;
                break;
            case SIMPLE:
                fpsPanel.setVisibility(VISIBLE);
                gpuPanel.setVisibility(GONE);
                ramPanel.setVisibility(GONE);
                cpuPanel.setVisibility(GONE);
                completeTelemetry.setVisibility(GONE);

                activityManager = null;
                memoryInfo = null;
                break;
            case FULL:
                fpsPanel.setVisibility(VISIBLE);
                gpuPanel.setVisibility(VISIBLE);
                ramPanel.setVisibility(VISIBLE);
                cpuPanel.setVisibility(VISIBLE);
                completeTelemetry.setVisibility(VISIBLE);

                Context context = getContext();
                activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                memoryInfo = new ActivityManager.MemoryInfo();
                break;
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        setupPanels();
    }

    public void setGPUInfo(String gpuInfo) {
        post(() -> ((TextView)gpuPanel.getChildAt(1)).setText(gpuInfo));
    }

    public void setCompleteTelemetry(String resolution, String fpsLimit, String performancePolicy,
                                     String gpuBackend, String dxWrapper, String wineVersion,
                                     String box64Version) {
        this.resolution = resolution;
        this.fpsLimit = fpsLimit;
        this.performancePolicy = performancePolicy;
        this.gpuBackend = gpuBackend;
        this.dxWrapper = dxWrapper;
        this.wineVersion = wineVersion;
        this.box64Version = box64Version;
    }

    public void setWrapperTelemetry(String wrapper, String backend, String profile) {
        wrapperIdentity = wrapper;
        wrapperBackend = backend;
        maliProfile = profile;
    }

    public void setVulkanDriverTelemetry(String selectedDriver, int loadStatus) {
        selectedVulkanDriver = selectedDriver;
        loadedVulkanLibrary = getResources().getString(loadStatus == 2 ?
                R.string.vulkan_library_custom_loaded : loadStatus == 1 ?
                R.string.vulkan_library_system_loaded : R.string.vulkan_library_unavailable);
        post(this::updateCompleteTelemetry);
    }

    public void setThermalStatus(ThermalGuard.State status) {
        thermalStatus = getResources().getString(ThermalGuard.getStateLabelResource(status));
        post(this::updateCompleteTelemetry);
    }

    public void setEffectiveFpsLimit(int limit) {
        fpsLimit = limit > 0 ? String.valueOf(limit) : getResources().getString(R.string.off);
        post(this::updateCompleteTelemetry);
    }

    public void reset() {
        frameCount = 0;
        lastTime = SystemClock.elapsedRealtime();
        lastFPS = 0;
        tick = 2;
    }

    public void update() {
        long time = SystemClock.elapsedRealtime();
        if (time >= lastTime + 500) {
            lastFPS = ((float)(frameCount * 1000) / (time - lastTime));
            post(this);
            lastTime = time;
            frameCount = 0;
        }

        frameCount++;
    }

    @Override
    public void run() {
        if (getVisibility() == GONE) setVisibility(View.VISIBLE);
        ((TextView)fpsPanel.getChildAt(1)).setText(String.format(Locale.ENGLISH, "%.1f", lastFPS));

        if (mode == Mode.FULL && ++tick >= 2) {
            tick = 0;
            activityManager.getMemoryInfo(memoryInfo);
            long usedMem = memoryInfo.totalMem - memoryInfo.availMem;
            String ramText = StringUtils.formatBytes(usedMem, false)+"/"+StringUtils.formatBytes(memoryInfo.totalMem);
            ((TextView)ramPanel.getChildAt(1)).setText(ramText);

            if (cpuInfo == null) {
                cpuInfo = "Box64 v"+ Box64Utils.extractBinVersion(cpuPanel.getContext());
            }

            short[] clockSpeeds = CPUStatus.getCurrentClockSpeeds();
            int maxClockSpeed = 0;
            for (short clockSpeed : clockSpeeds) maxClockSpeed = Math.max(maxClockSpeed, clockSpeed);
            ((TextView)cpuPanel.getChildAt(1)).setText(CPUStatus.formatClockSpeed(maxClockSpeed)+" | "+cpuInfo);
            updateCompleteTelemetry();
        }
    }

    private void updateCompleteTelemetry() {
        if (mode != Mode.FULL) return;
        float frameTime = lastFPS > 0 ? 1000.0f / lastFPS : 0;
        String data = getResources().getString(R.string.fps_complete_data, frameTime, resolution,
                fpsLimit, performancePolicy, gpuBackend, dxWrapper, wineVersion, box64Version,
                thermalStatus != null ? thermalStatus : getResources().getString(R.string.thermal_unavailable));
        if (wrapperIdentity != null || wrapperBackend != null || maliProfile != null) {
            data += "\n"+getResources().getString(R.string.fps_wrapper_data,
                    wrapperIdentity != null ? wrapperIdentity : "",
                    wrapperBackend != null ? wrapperBackend : "",
                    maliProfile != null ? maliProfile : "");
        }
        if (selectedVulkanDriver != null || loadedVulkanLibrary != null) {
            data += "\n"+getResources().getString(R.string.fps_vulkan_driver_data,
                    selectedVulkanDriver != null ? selectedVulkanDriver : "",
                    loadedVulkanLibrary != null ? loadedVulkanLibrary : "");
        }
        completeTelemetry.setText(data);
    }

}