package io.vanta.app.contentdialog;

import android.content.Context;
import android.view.View;
import android.widget.Spinner;

import io.vanta.app.R;
import io.vanta.app.core.AppUtils;
import io.vanta.app.core.GPUHelper;
import io.vanta.app.core.GeneralComponents;
import io.vanta.app.core.KeyValueSet;
import io.vanta.app.core.StringUtils;
import io.vanta.app.widget.MultiSelectionComboBox;
import io.vanta.app.xenvironment.components.VortekRendererComponent;

public class VortekConfigDialog extends ContentDialog {
    public static final String DEFAULT_VK_MAX_VERSION = GPUHelper.vkVersionMajor(VortekRendererComponent.VK_MAX_VERSION)+"."+GPUHelper.vkVersionMinor(VortekRendererComponent.VK_MAX_VERSION);

    public VortekConfigDialog(final View anchor) {
        super(anchor.getContext(), R.layout.vortek_config_dialog);
        Context context = anchor.getContext();
        setIcon(R.drawable.icon_display_settings);
        setTitle("Vortek "+context.getString(R.string.configuration));

        final Spinner sAdrenotoolsDriver = findViewById(R.id.SAdrenotoolsDriver);
        final Spinner sVkMaxVersion = findViewById(R.id.SVkMaxVersion);
        final Spinner sMaxDeviceMemory = findViewById(R.id.SMaxDeviceMemory);
        final Spinner sImageCacheSize = findViewById(R.id.SImageCacheSize);
        final Spinner sResourceMemoryType = findViewById(R.id.SResourceMemoryType);
        final MultiSelectionComboBox mscbExposedExtensions = findViewById(R.id.MSCBExposedExtensions);

        final String[] deviceExtensions = GPUHelper.vkGetDeviceExtensions();
        mscbExposedExtensions.setPopupWindowWidth(360);
        mscbExposedExtensions.setDisplayText(context.getString(R.string.multiselection_combobox_display_text));
        mscbExposedExtensions.setItems(deviceExtensions);

        KeyValueSet config = new KeyValueSet(anchor.getTag());

        String exposedDeviceExtensionsVal = config.get("exposedDeviceExtensions", "all");
        if (exposedDeviceExtensionsVal.contains("|")) exposedDeviceExtensionsVal = "all";

        if (exposedDeviceExtensionsVal.equals("all")) {
            mscbExposedExtensions.setSelectedItems(deviceExtensions);
        }
        else if (!exposedDeviceExtensionsVal.isEmpty()) {
            String[] selectedItems = exposedDeviceExtensionsVal.split(":");
            mscbExposedExtensions.setSelectedItems(selectedItems.length > 1 ? selectedItems : deviceExtensions);
        }

        String adrenotoolsDriver = config.get("adrenotoolsDriver");
        GeneralComponents.initViews(GeneralComponents.Type.ADRENOTOOLS_DRIVER, findViewById(R.id.AdrenotoolsDriverToolbox), sAdrenotoolsDriver, adrenotoolsDriver, "System");

        AppUtils.setSpinnerSelectionFromValue(sVkMaxVersion, config.get("vkMaxVersion", DEFAULT_VK_MAX_VERSION));
        AppUtils.setSpinnerSelectionFromMemorySize(sMaxDeviceMemory, config.get("maxDeviceMemory", "0"));
        AppUtils.setSpinnerSelectionFromNumber(sImageCacheSize, config.get("imageCacheSize", String.valueOf(VortekRendererComponent.IMAGE_CACHE_SIZE)));
        sResourceMemoryType.setSelection(config.getInt("resourceMemoryType"));

        setOnConfirmCallback(() -> {
            KeyValueSet newConfig = new KeyValueSet();
            newConfig.put("adrenotoolsDriver", sAdrenotoolsDriver.getSelectedItem());
            newConfig.put("vkMaxVersion", StringUtils.parseNumber(sVkMaxVersion.getSelectedItem(), "0"));
            newConfig.put("maxDeviceMemory", StringUtils.parseMemorySize(sMaxDeviceMemory.getSelectedItem()));
            newConfig.put("imageCacheSize", StringUtils.parseNumber(sImageCacheSize.getSelectedItem()));
            newConfig.put("resourceMemoryType", sResourceMemoryType.getSelectedItemPosition());

            String[] selectedItems = mscbExposedExtensions.getSelectedItems();
            if (selectedItems.length > 0) {
                if (selectedItems.length == deviceExtensions.length) {
                    newConfig.put("exposedDeviceExtensions", "all");
                }
                else newConfig.put("exposedDeviceExtensions", String.join(":", selectedItems));
            }
            anchor.setTag(newConfig.toString());
        });
    }

    public static boolean isRequireRestart(String oldGraphicsDriverConfig, String newGraphicsDriverConfig) {
        if (!oldGraphicsDriverConfig.equals(newGraphicsDriverConfig)) {
            KeyValueSet oldConfig = new KeyValueSet(oldGraphicsDriverConfig);
            KeyValueSet newConfig = new KeyValueSet(newGraphicsDriverConfig);
            String oldDriver = oldConfig.get("adrenotoolsDriver");
            String newDriver = newConfig.get("adrenotoolsDriver");
            boolean driverChanged = !oldDriver.equals(newDriver);
            boolean advancedChanged = !oldConfig.get("maliAdvancedDriver", "false")
                    .equals(newConfig.get("maliAdvancedDriver", "false"));
            if (driverChanged && (!oldDriver.isEmpty() && !newDriver.isEmpty() ||
                    oldDriver.startsWith("vanta-runtime:") || newDriver.startsWith("vanta-runtime:"))) return true;
            return advancedChanged && newDriver.startsWith("vanta-runtime:");
        }
        else return false;
    }
}