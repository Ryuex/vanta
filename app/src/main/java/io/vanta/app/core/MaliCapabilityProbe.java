package io.vanta.app.core;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MaliCapabilityProbe {
    private static final String TAG = "VantaGpuProbe";
    private static final int ARM_VENDOR_ID = 0x13B5;
    private static final int VK_DRIVER_ID_ARM_PROPRIETARY = 9;
    private static final int VK_DRIVER_ID_MESA_PANVK = 20;
    private static volatile GpuCapabilityProfile cachedProfile;

    private MaliCapabilityProbe() {}

    public static GpuCapabilityProfile probe(Context context) {
        GpuCapabilityProfile result = cachedProfile;
        if (result != null) return result;
        synchronized (MaliCapabilityProbe.class) {
            if (cachedProfile == null) cachedProfile = collect(context.getApplicationContext());
            return cachedProfile;
        }
    }

    private static GpuCapabilityProfile collect(Context context) {
        String glVendor = "";
        String glRenderer = "";
        try {
            glVendor = GPUHelper.glGetVendor(context);
            glRenderer = GPUHelper.glGetRenderer(context);
        }
        catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "OpenGL GPU identification unavailable", e);
        }

        String[] details = new String[0];
        try {
            details = GPUHelper.vkGetPhysicalDeviceDetails();
        }
        catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "Vulkan capability probe unavailable", e);
        }

        String model = value(details, 0);
        if (model.isEmpty()) model = glRenderer;
        int vendorId = integer(details, 1);
        int deviceId = integer(details, 2);
        int apiVersion = integer(details, 3);
        int driverVersion = integer(details, 4);
        String driverName = value(details, 5);
        String driverInfo = value(details, 6);
        int driverId = integer(details, 7);
        long localMemory = longValue(details, 8);
        Boolean bc = booleanValue(details, 9);
        Boolean etc2 = booleanValue(details, 10);
        Boolean astc = booleanValue(details, 11);
        int extensionCount = Math.min(integer(details, 12), Math.max(0, details.length - 13));
        Set<String> extensions = new LinkedHashSet<>();
        for (int i = 0; i < extensionCount; i++) extensions.add(details[13 + i]);
        Map<String, Integer> formats = new LinkedHashMap<>();
        for (int i = 13 + extensionCount; i < details.length; i++) {
            String[] parts = details[i].split(":", 2);
            if (parts.length == 2) {
                try {
                    formats.put(parts[0], Integer.parseInt(parts[1]));
                }
                catch (NumberFormatException e) {
                    Log.w(TAG, "Ignoring malformed Vulkan format capability: " + details[i]);
                }
            }
        }

        boolean maliByVulkan = vendorId == ARM_VENDOR_ID && containsIgnoreCase(model, "mali");
        boolean maliByOpenGL = containsIgnoreCase(glVendor, "arm") &&
                glRenderer.toLowerCase(Locale.ROOT).contains("mali");
        boolean mali = maliByVulkan || maliByOpenGL;
        String vendor = vendorName(vendorId, glVendor);
        GpuCapabilityProfile.Family family = mali ? identifyFamily(model) : GpuCapabilityProfile.Family.UNKNOWN;
        String generation = mali ? identifyGeneration(model) : "";
        GpuCapabilityProfile.DriverType driverType = identifyDriver(driverId, driverName, vendorId);

        String[] supportedAbis = Build.SUPPORTED_ABIS;
        String abi = supportedAbis.length > 0 ? supportedAbis[0] : "";
        return new GpuCapabilityProfile(vendor, model, family, generation, driverType, driverName,
                driverInfo, driverId, vendorId, deviceId, apiVersion, driverVersion, localMemory,
                Build.VERSION.RELEASE, Build.VERSION.SDK_INT, abi, System.getProperty("os.version", ""),
                context.getString(io.vanta.app.R.string.gpu_probe_scope),
                bc, etc2, astc, extensions,
                formats, mali);
    }

    private static GpuCapabilityProfile.Family identifyFamily(String model) {
        Matcher matcher = Pattern.compile("Mali[- ]([A-Z]?\\d+)", Pattern.CASE_INSENSITIVE).matcher(model);
        if (!matcher.find()) return GpuCapabilityProfile.Family.UNKNOWN;
        String gpu = matcher.group(1).toUpperCase(Locale.ROOT);
        if (gpu.matches("G(?:31|51|52|71|72|76)")) return GpuCapabilityProfile.Family.BIFROST;
        if (gpu.matches("G(?:1|57|68|77|78|310|610|615|715|720|725)")) return GpuCapabilityProfile.Family.VALHALL;
        if (gpu.matches("T\\d+")) return GpuCapabilityProfile.Family.MIDGARD;
        return GpuCapabilityProfile.Family.UNKNOWN;
    }

    private static String identifyGeneration(String model) {
        Matcher matcher = Pattern.compile("Mali[- ]([A-Z]?\\d+)", Pattern.CASE_INSENSITIVE).matcher(model);
        if (!matcher.find()) return "";
        String gpu = matcher.group(1).toUpperCase(Locale.ROOT);
        switch (gpu) {
            case "G31":
            case "G52":
            case "G72":
            case "G76":
                return "Bifrost v7";
            case "G51":
            case "G71":
                return "Bifrost v6";
            case "G57":
            case "G68":
                return "Valhall v9";
            case "G310":
            case "G610":
                return "Valhall v10";
            case "G615":
            case "G715":
                return "Valhall v11";
            case "G720":
                return "Valhall v12";
            case "G725":
                return "Valhall v13";
            case "G1":
                return "Valhall v14";
            default:
                return "";
        }
    }

    private static GpuCapabilityProfile.DriverType identifyDriver(int driverId, String name, int vendorId) {
        if (driverId == VK_DRIVER_ID_MESA_PANVK || containsIgnoreCase(name, "panvk")) {
            return GpuCapabilityProfile.DriverType.MESA_PANVK;
        }
        if (driverId == VK_DRIVER_ID_ARM_PROPRIETARY ||
                !name.isEmpty() && containsIgnoreCase(name, "arm") && containsIgnoreCase(name, "proprietary")) {
            return GpuCapabilityProfile.DriverType.ARM_PROPRIETARY;
        }
        if (!name.isEmpty() || driverId > 0) return GpuCapabilityProfile.DriverType.OTHER;
        return GpuCapabilityProfile.DriverType.UNKNOWN;
    }

    private static String vendorName(int vendorId, String glVendor) {
        switch (vendorId) {
            case ARM_VENDOR_ID: return "Arm";
            case 0x5143: return "Qualcomm";
            case 0x1002: return "AMD";
            case 0x10DE: return "NVIDIA";
            case 0x8086: return "Intel";
            default: return glVendor != null ? glVendor : "";
        }
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static String value(String[] values, int index) {
        return index < values.length && values[index] != null ? values[index] : "";
    }

    private static int integer(String[] values, int index) {
        try {
            return Integer.parseInt(value(values, index));
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long longValue(String[] values, int index) {
        try {
            return Long.parseLong(value(values, index));
        }
        catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Boolean booleanValue(String[] values, int index) {
        if (index >= values.length || values[index].isEmpty()) return null;
        return Boolean.parseBoolean(values[index]);
    }
}
