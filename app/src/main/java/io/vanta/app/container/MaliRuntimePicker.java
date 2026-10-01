package io.vanta.app.container;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import io.vanta.app.R;
import io.vanta.app.core.GraphicsRuntimePackage;
import io.vanta.app.core.GraphicsRuntimePackageStore;
import io.vanta.app.core.GpuCapabilityProfile;
import io.vanta.app.core.KeyValueSet;
import io.vanta.app.core.MaliCapabilityProbe;

import java.util.ArrayList;
import java.util.List;

public final class MaliRuntimePicker {
    private static final String SYSTEM_DRIVER = "System";
    private static final String WRAPPER_STABLE = "stable";
    private static final String WRAPPER_AUTO = "auto";

    private final Context context;
    private final LinearLayout panel;
    private final GraphicsDriverPicker graphicsDriverPicker;
    private final KeyValueSet graphicsConfig;
    private final Spinner driverSpinner;
    private final Spinner wrapperSpinner;
    private final CheckBox advancedOverride;
    private final TextView status;
    private final TextView gpu;
    private GpuCapabilityProfile profile;
    private List<GraphicsRuntimePackage> packages = new ArrayList<>();
    private List<DriverOption> options = new ArrayList<>();
    private String selectedDriver;
    private boolean updating;

    public MaliRuntimePicker(LinearLayout panel, GraphicsDriverPicker graphicsDriverPicker,
                             String graphicsDriverConfig) {
        this.panel = panel;
        this.context = panel.getContext();
        this.graphicsDriverPicker = graphicsDriverPicker;
        this.graphicsConfig = GraphicsDrivers.parseConfigs(
                GraphicsDrivers.DEFAULT_VULKAN_DRIVER + "," + GraphicsDrivers.DEFAULT_OPENGL_DRIVER,
                graphicsDriverConfig)[0];
        this.driverSpinner = panel.findViewById(R.id.SVantaMaliDriver);
        this.wrapperSpinner = panel.findViewById(R.id.SVantaMaliWrapper);
        this.advancedOverride = panel.findViewById(R.id.CBMaliAdvancedDriver);
        this.status = panel.findViewById(R.id.TVVantaMaliRuntimeNote);
        this.gpu = panel.findViewById(R.id.TVVantaMaliGpu);

        selectedDriver = this.graphicsConfig.get("adrenotoolsDriver", SYSTEM_DRIVER);
        advancedOverride.setChecked(this.graphicsConfig.getBoolean("maliAdvancedDriver", false));
        setupWrapperSpinner();
        setupListeners();
        probeInBackground();
    }

    private void setupWrapperSpinner() {
        ArrayList<String> labels = new ArrayList<>();
        labels.add(context.getString(R.string.mali_wrapper_auto));
        labels.add(context.getString(R.string.mali_wrapper_stable));
        wrapperSpinner.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, labels));
        String selected = graphicsConfig.get("maliWrapper", WRAPPER_AUTO);
        wrapperSpinner.setSelection(WRAPPER_STABLE.equals(selected) ? 1 : 0, false);
        wrapperSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                graphicsDriverPicker.setVulkanConfigValue("maliWrapper",
                        position == 0 ? WRAPPER_AUTO : WRAPPER_STABLE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupListeners() {
        advancedOverride.setOnCheckedChangeListener((button, checked) -> {
            if (profile == null || updating) return;
            populateDriverSpinner();
            updateSelectedDriver();
        });
        driverSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!updating && position >= 0 && position < options.size()) {
                    selectedDriver = options.get(position).selectionId;
                    updateSelectedDriver();
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void probeInBackground() {
        Thread thread = new Thread(() -> {
            GpuCapabilityProfile detected = MaliCapabilityProbe.probe(context);
            List<GraphicsRuntimePackage> installed = GraphicsRuntimePackageStore.getInstalledPackages(context);
            panel.post(() -> {
                if (!detected.mali) {
                    panel.setVisibility(View.GONE);
                    return;
                }
                profile = detected;
                packages = installed;
                panel.setVisibility(View.VISIBLE);
                String model = detected.model.isEmpty() ?
                        context.getString(R.string.thermal_unavailable) : detected.model;
                String family = detected.generation.isEmpty() ?
                        detected.family.name() : detected.generation;
                gpu.setText(context.getString(R.string.mali_gpu_detected, model, family));
                status.setText(detected.model.isEmpty() ?
                        context.getString(R.string.mali_runtime_no_model) :
                        context.getString(R.string.mali_runtime_stable_note));
                populateDriverSpinner();
                graphicsDriverPicker.setVulkanConfigValue("maliWrapper",
                        graphicsConfig.get("maliWrapper", WRAPPER_AUTO));
                updateSelectedDriver();
            });
        }, "vanta-mali-container-probe");
        thread.setDaemon(true);
        thread.start();
    }

    private void populateDriverSpinner() {
        boolean allowExperimental = advancedOverride.isChecked();
        options = new ArrayList<>();
        options.add(new DriverOption(SYSTEM_DRIVER, context.getString(R.string.mali_driver_system)));

        boolean savedOptionFound = SYSTEM_DRIVER.equals(selectedDriver);
        for (GraphicsRuntimePackage runtimePackage : packages) {
            if (runtimePackage.kind != GraphicsRuntimePackage.Kind.VULKAN_DRIVER ||
                    !runtimePackage.loader.equals("adrenotools")) continue;
            boolean compatible = runtimePackage.isCompatibleWith(profile, false);
            boolean saved = runtimePackage.getSelectionId().equals(selectedDriver);
            if (!compatible && !allowExperimental && !saved) continue;
            int labelResource = compatible ? R.string.mali_driver_package :
                    saved && !allowExperimental ? R.string.mali_driver_current_unverified :
                            R.string.mali_driver_advanced_package;
            String label = context.getString(labelResource, runtimePackage.name, runtimePackage.version);
            options.add(new DriverOption(runtimePackage.getSelectionId(), label));
            savedOptionFound |= saved;
        }

        if (!savedOptionFound) {
            options.add(new DriverOption(selectedDriver,
                    context.getString(R.string.mali_driver_saved_missing, selectedDriver)));
        }

        ArrayList<String> labels = new ArrayList<>(options.size());
        int selectedPosition = 0;
        for (int i = 0; i < options.size(); i++) {
            labels.add(options.get(i).label);
            if (options.get(i).selectionId.equals(selectedDriver)) selectedPosition = i;
        }
        updating = true;
        driverSpinner.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, labels));
        driverSpinner.setSelection(selectedPosition, false);
        updating = false;
    }

    private void updateSelectedDriver() {
        DriverOption option = null;
        for (DriverOption candidate : options) {
            if (candidate.selectionId.equals(selectedDriver)) {
                option = candidate;
                break;
            }
        }
        if (option == null) return;
        boolean experimental = !SYSTEM_DRIVER.equals(selectedDriver) &&
                !isCompatible(selectedDriver);
        graphicsDriverPicker.setVulkanConfigValue("adrenotoolsDriver", selectedDriver);
        graphicsDriverPicker.setVulkanConfigValue("maliAdvancedDriver",
                Boolean.toString(experimental && advancedOverride.isChecked()));
        if (!SYSTEM_DRIVER.equals(selectedDriver)) {
            graphicsDriverPicker.setVulkanDriver(GraphicsDrivers.VORTEK);
        }
    }

    private boolean isCompatible(String selectionId) {
        for (GraphicsRuntimePackage runtimePackage : packages) {
            if (runtimePackage.getSelectionId().equals(selectionId)) {
                return runtimePackage.isCompatibleWith(profile, false);
            }
        }
        return false;
    }

    private static final class DriverOption {
        final String selectionId;
        final String label;

        DriverOption(String selectionId, String label) {
            this.selectionId = selectionId;
            this.label = label;
        }
    }
}
