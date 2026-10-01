package io.vanta.app.core;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class GraphicsRuntimePackage {
    public enum Kind {VULKAN_DRIVER, VULKAN_WRAPPER}
    public enum Scheduler {ANY, JOB_MANAGER, CSF, UNKNOWN}

    public final String id;
    public final String name;
    public final String version;
    public final Kind kind;
    public final String upstream;
    public final String revision;
    public final String license;
    public final String abi;
    public final String loader;
    public final String sha256;
    public final String libraryPath;
    public final Set<String> gpuFamilies;
    public final Set<String> gpuModels;
    public final String kernelRequirement;
    public final String kbaseRequirement;
    public final Scheduler scheduler;
    public final String vulkanVersion;
    public final boolean experimental;
    public final String limitations;
    public final File installDirectory;

    GraphicsRuntimePackage(String id, String name, String version, Kind kind, String upstream,
                           String revision, String license, String abi, String sha256,
                           String loader,
                           String libraryPath, Set<String> gpuFamilies, Set<String> gpuModels,
                           String kernelRequirement, String kbaseRequirement, Scheduler scheduler,
                           String vulkanVersion, boolean experimental, String limitations,
                           File installDirectory) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.kind = kind;
        this.upstream = upstream;
        this.revision = revision;
        this.license = license;
        this.abi = abi;
        this.loader = loader;
        this.sha256 = sha256;
        this.libraryPath = libraryPath;
        this.gpuFamilies = Collections.unmodifiableSet(new LinkedHashSet<>(gpuFamilies));
        this.gpuModels = Collections.unmodifiableSet(new LinkedHashSet<>(gpuModels));
        this.kernelRequirement = kernelRequirement;
        this.kbaseRequirement = kbaseRequirement;
        this.scheduler = scheduler;
        this.vulkanVersion = vulkanVersion;
        this.experimental = experimental;
        this.limitations = limitations;
        this.installDirectory = installDirectory;
    }

    public File getLibraryFile() {
        return new File(installDirectory, libraryPath);
    }

    public String getSelectionId() {
        return "vanta-runtime:" + id + "/" + version;
    }

    public boolean isCompatibleWith(GpuCapabilityProfile profile, boolean allowExperimental) {
        if (profile == null || !abi.equals(profile.abi)) return false;
        if (!loader.equals("adrenotools")) return false;
        if (allowExperimental) return true;
        if (experimental || profile.model.isEmpty() || !gpuModelsContain(profile.model)) return false;
        if (scheduler != Scheduler.ANY) return false;
        if (!kernelRequirement.equalsIgnoreCase("any") || !kbaseRequirement.equalsIgnoreCase("any")) return false;
        return gpuFamilies.contains(profile.family.name().toLowerCase(java.util.Locale.ROOT)) ||
                gpuFamilies.contains(profile.generation.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
    }

    private boolean gpuModelsContain(String model) {
        for (String supportedModel : gpuModels) {
            if (supportedModel.equalsIgnoreCase(model)) return true;
        }
        return false;
    }
}
