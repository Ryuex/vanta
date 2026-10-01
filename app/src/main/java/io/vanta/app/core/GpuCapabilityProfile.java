package io.vanta.app.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class GpuCapabilityProfile {
    public enum Family {UNKNOWN, MIDGARD, BIFROST, VALHALL}
    public enum DriverType {UNKNOWN, ARM_PROPRIETARY, MESA_PANVK, OTHER}

    public final String vendor;
    public final String model;
    public final Family family;
    public final String generation;
    public final DriverType driverType;
    public final String driverName;
    public final String driverInfo;
    public final int driverId;
    public final int vendorId;
    public final int deviceId;
    public final int vulkanApiVersion;
    public final int vulkanDriverVersion;
    public final long deviceLocalMemoryBytes;
    public final String androidRelease;
    public final int androidApiLevel;
    public final String abi;
    public final String probeScope;
    public final String kernelRelease;
    public final String gpuKernelInterface;
    public final Boolean textureCompressionBC;
    public final Boolean textureCompressionETC2;
    public final Boolean textureCompressionASTCLdr;
    public final Set<String> extensions;
    public final Map<String, Integer> textureFormats;
    public final boolean mali;

    GpuCapabilityProfile(String vendor, String model, Family family, String generation,
                         DriverType driverType, String driverName, String driverInfo, int driverId,
                         int vendorId, int deviceId, int vulkanApiVersion, int vulkanDriverVersion,
                         long deviceLocalMemoryBytes, String androidRelease, int androidApiLevel, String abi,
                         String kernelRelease, String probeScope,
                         Boolean textureCompressionBC, Boolean textureCompressionETC2,
                         Boolean textureCompressionASTCLdr, Set<String> extensions,
                         Map<String, Integer> textureFormats, boolean mali) {
        this.vendor = vendor;
        this.model = model;
        this.family = family;
        this.generation = generation;
        this.driverType = driverType;
        this.driverName = driverName;
        this.driverInfo = driverInfo;
        this.driverId = driverId;
        this.vendorId = vendorId;
        this.deviceId = deviceId;
        this.vulkanApiVersion = vulkanApiVersion;
        this.vulkanDriverVersion = vulkanDriverVersion;
        this.deviceLocalMemoryBytes = deviceLocalMemoryBytes;
        this.androidRelease = androidRelease;
        this.androidApiLevel = androidApiLevel;
        this.abi = abi;
        this.kernelRelease = kernelRelease;
        this.probeScope = probeScope;
        this.gpuKernelInterface = "Unverified (no reliable rootless probe)";
        this.textureCompressionBC = textureCompressionBC;
        this.textureCompressionETC2 = textureCompressionETC2;
        this.textureCompressionASTCLdr = textureCompressionASTCLdr;
        this.extensions = Collections.unmodifiableSet(new LinkedHashSet<>(extensions));
        this.textureFormats = Collections.unmodifiableMap(new LinkedHashMap<>(textureFormats));
        this.mali = mali;
    }

    public String toDiagnosticText() {
        String unavailable = "Unavailable";
        StringBuilder report = new StringBuilder();
        report.append("GPU vendor: ").append(valueOrUnavailable(vendor, unavailable)).append('\n');
        report.append("GPU model: ").append(valueOrUnavailable(model, unavailable)).append('\n');
        report.append("GPU family: ").append(family == Family.UNKNOWN ? unavailable : family.name()).append('\n');
        report.append("GPU generation: ").append(valueOrUnavailable(generation, unavailable)).append('\n');
        report.append(probeScope).append('\n');
        report.append("System Vulkan driver type: ").append(driverType.name()).append('\n');
        report.append("System Vulkan driver: ").append(valueOrUnavailable(driverName, unavailable)).append('\n');
        report.append("System Vulkan driver info: ").append(valueOrUnavailable(driverInfo, unavailable)).append('\n');
        report.append("Vulkan API: ").append(formatVulkanVersion(vulkanApiVersion)).append('\n');
        report.append("Vulkan driver version (raw): ").append(vulkanDriverVersion > 0 ? vulkanDriverVersion : unavailable).append('\n');
        report.append("Vendor/device ID: ").append(vendorId).append('/').append(deviceId).append('\n');
        report.append("Device-local memory: ").append(deviceLocalMemoryBytes > 0 ? deviceLocalMemoryBytes+" bytes" : unavailable).append('\n');
        report.append("Texture compression: BC=").append(valueOrUnavailable(textureCompressionBC))
                .append(", ETC2=").append(valueOrUnavailable(textureCompressionETC2))
                .append(", ASTC LDR=").append(valueOrUnavailable(textureCompressionASTCLdr)).append('\n');
        report.append("Format feature masks: ").append(textureFormats.isEmpty() ? unavailable : textureFormats).append('\n');
        report.append("Vulkan extensions: ").append(extensions.isEmpty() ? unavailable : extensions).append('\n');
        report.append("Android: ").append(androidRelease).append(" (API ").append(androidApiLevel).append(")\n");
        report.append("Kernel release: ").append(valueOrUnavailable(kernelRelease, unavailable)).append('\n');
        report.append("kbase / Job Manager / CSF: ").append(gpuKernelInterface).append('\n');
        report.append("ABI: ").append(abi);
        return report.toString();
    }

    public static String formatVulkanVersion(int version) {
        if (version <= 0) return "Unavailable";
        return GPUHelper.vkVersionMajor(version)+"."+GPUHelper.vkVersionMinor(version)+"."+GPUHelper.vkVersionPatch(version);
    }

    private static String valueOrUnavailable(String value, String unavailable) {
        return value == null || value.isEmpty() ? unavailable : value;
    }

    private static String valueOrUnavailable(Boolean value) {
        return value == null ? "Unavailable" : value.toString();
    }
}
