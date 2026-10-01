package io.vanta.app.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class GraphicsRuntimePackageStore {
    public static final String MANIFEST_FILENAME = "vanta-graphics-runtime.json";
    private static final String ABI = "arm64-v8a";
    private static final long MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;
    private static final long MAX_PACKAGE_BYTES = 512L * 1024 * 1024;
    private static final long MAX_SINGLE_FILE_BYTES = 256L * 1024 * 1024;
    private static final int MAX_FILE_COUNT = 64;
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,95}");
    private static final Pattern SHA256 = Pattern.compile("[a-fA-F0-9]{64}");

    private GraphicsRuntimePackageStore() {}

    public static List<GraphicsRuntimePackage> getInstalledPackages(Context context) {
        ArrayList<GraphicsRuntimePackage> packages = new ArrayList<>();
        File root = getStoreDirectory(context);
        File[] directories = root.listFiles(File::isDirectory);
        if (directories == null) return packages;
        for (File directory : directories) {
            try {
                GraphicsRuntimePackage runtimePackage = readInstalledPackage(directory);
                if (runtimePackage != null) packages.add(runtimePackage);
            }
            catch (IOException | JSONException e) {
                android.util.Log.w("VantaGraphicsPackages", "Ignoring invalid installed package " + directory.getName(), e);
            }
        }
        return packages;
    }

    public static GraphicsRuntimePackage install(Context context, File archive)
            throws IOException, JSONException {
        return install(context, archive, null);
    }

    public static GraphicsRuntimePackage install(Context context, File archive,
                                                 GraphicsRuntimePackage.Kind expectedKind)
            throws IOException, JSONException {
        if (archive == null || !archive.isFile() || archive.length() <= 0 ||
                archive.length() > MAX_ARCHIVE_BYTES) {
            throw new IOException("The graphics package archive is missing, empty, or too large.");
        }

        File store = getStoreDirectory(context);
        File staging = new File(store, ".staging-" + UUID.randomUUID());
        if (!staging.mkdir()) throw new IOException("Unable to create package staging directory.");

        try (ZipFile zip = new ZipFile(archive)) {
            Map<String, ZipEntry> entries = indexArchive(zip);
            ZipEntry manifestEntry = entries.get(MANIFEST_FILENAME);
            if (manifestEntry == null || manifestEntry.getSize() < 0 || manifestEntry.getSize() > 256 * 1024) {
                throw new IOException("The archive has no valid Vanta graphics-runtime manifest.");
            }
            JSONObject manifest = new JSONObject(new String(readEntry(zip, manifestEntry, 256 * 1024), java.nio.charset.StandardCharsets.UTF_8));
            ValidatedManifest validated = validateManifest(manifest, entries);
            if (expectedKind != null && validated.kind != expectedKind) {
                throw new IOException("This archive is a " + validated.kind.name().toLowerCase(Locale.ROOT).replace('_', ' ') +
                        ", not a " + expectedKind.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".");
            }

            long totalSize = 0;
            int fileCount = 0;
            for (String path : validated.files.keySet()) {
                ZipEntry entry = entries.get(path);
                if (entry == null || entry.isDirectory() || entry.getSize() < 0 ||
                        entry.getSize() > MAX_SINGLE_FILE_BYTES) {
                    throw new IOException("Missing or invalid package file: " + path);
                }
                totalSize += entry.getSize();
                if (++fileCount > MAX_FILE_COUNT || totalSize > MAX_PACKAGE_BYTES) {
                    throw new IOException("The graphics package exceeds the permitted size or file count.");
                }
                File output = resolveChild(staging, path);
                File parent = output.getParentFile();
                if (parent == null || !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Unable to create a package directory.");
                }
                String expectedHash = validated.files.get(path);
                String actualHash = copyEntry(zip, entry, output, MAX_SINGLE_FILE_BYTES);
                if (!actualHash.equalsIgnoreCase(expectedHash)) {
                    throw new IOException("SHA-256 verification failed for " + path);
                }
                if (path.equals(validated.libraryPath) || path.startsWith("lib/")) {
                    if (!isAarch64Elf(output)) throw new IOException("Package library is not an ARM64 ELF: " + path);
                }
            }

            ZipEntry licenseEntry = entries.get(validated.licenseFile);
            if (licenseEntry == null || licenseEntry.getSize() < 0 || licenseEntry.getSize() > 1024 * 1024) {
                throw new IOException("The package license text is missing or invalid.");
            }
            File licenseFile = resolveChild(staging, validated.licenseFile);
            File licenseParent = licenseFile.getParentFile();
            if (licenseParent == null || !licenseParent.isDirectory() && !licenseParent.mkdirs()) {
                throw new IOException("Unable to create the package license directory.");
            }
            copyEntry(zip, licenseEntry, licenseFile, 1024 * 1024);
            totalSize += licenseEntry.getSize();
            if (++fileCount > MAX_FILE_COUNT || totalSize > MAX_PACKAGE_BYTES) {
                throw new IOException("The graphics package exceeds the permitted size or file count.");
            }
            if (!licenseFile.isFile() || licenseFile.length() == 0 || licenseFile.length() > 1024 * 1024) {
                throw new IOException("The package license text is missing or invalid.");
            }

            for (String name : entries.keySet()) {
                if (entries.get(name).isDirectory()) continue;
                if (!name.equals(MANIFEST_FILENAME) && !name.equals(validated.licenseFile) && !validated.files.containsKey(name)) {
                    throw new IOException("Unexpected executable or unlisted package data: " + name);
                }
            }

            writeBytes(new File(staging, MANIFEST_FILENAME), readEntry(zip, manifestEntry, 256 * 1024));
            File destination = new File(store, validated.id + "-" + validated.version);
            if (destination.exists()) throw new IOException("This graphics package version is already installed.");
            if (!staging.renameTo(destination)) throw new IOException("Unable to finalize graphics package installation.");
            GraphicsRuntimePackage installed = readInstalledPackage(destination);
            if (installed == null) {
                if (!FileUtils.delete(destination)) {
                    throw new IOException("The installed package failed integrity validation and could not be removed.");
                }
                throw new IOException("The installed graphics package failed final integrity validation.");
            }
            return installed;
        }
        catch (JSONException | IOException | RuntimeException e) {
            FileUtils.delete(staging);
            if (e instanceof JSONException) throw (JSONException)e;
            if (e instanceof IOException) throw (IOException)e;
            throw new IOException("The graphics package could not be installed.", e);
        }
    }

    public static GraphicsRuntimePackage findInstalledPackage(Context context, String selectionId) {
        if (selectionId == null || !selectionId.startsWith("vanta-runtime:")) return null;
        for (GraphicsRuntimePackage runtimePackage : getInstalledPackages(context)) {
            if (runtimePackage.getSelectionId().equals(selectionId)) return runtimePackage;
        }
        return null;
    }

    public static boolean isInUse(Context context, GraphicsRuntimePackage runtimePackage) {
        if (runtimePackage == null) return false;
        io.vanta.app.container.ContainerManager manager =
                new io.vanta.app.container.ContainerManager(context);
        String selectionId = runtimePackage.getSelectionId();
        for (io.vanta.app.container.Container container : manager.getContainers()) {
            io.vanta.app.core.KeyValueSet[] configs = io.vanta.app.container.GraphicsDrivers.parseConfigs(
                    container.getGraphicsDriver(), container.getGraphicsDriverConfig());
            if (selectionId.equals(configs[0].get("adrenotoolsDriver", "System"))) return true;
        }
        return false;
    }

    public static boolean remove(Context context, GraphicsRuntimePackage runtimePackage) {
        if (runtimePackage == null || isInUse(context, runtimePackage)) return false;
        File root = getStoreDirectory(context);
        try {
            if (!runtimePackage.installDirectory.getCanonicalFile().getParentFile().equals(root.getCanonicalFile())) return false;
        }
        catch (IOException e) {
            return false;
        }
        return FileUtils.delete(runtimePackage.installDirectory);
    }

    private static File getStoreDirectory(Context context) {
        File root = new File(context.getFilesDir(), "graphics-runtimes");
        if (!root.isDirectory()) root.mkdirs();
        return root;
    }

    private static Map<String, ZipEntry> indexArchive(ZipFile zip) throws IOException {
        Map<String, ZipEntry> entries = new HashMap<>();
        Set<String> normalizedNames = new HashSet<>();
        java.util.Enumeration<? extends ZipEntry> enumeration = zip.entries();
        int count = 0;
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            String name = entry.getName();
            validateRelativePath(name);
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!normalizedNames.add(normalized)) throw new IOException("Duplicate archive path: " + name);
            if (++count > MAX_FILE_COUNT * 2) throw new IOException("The graphics archive contains too many entries.");
            entries.put(name, entry);
        }
        return entries;
    }

    private static ValidatedManifest validateManifest(JSONObject manifest, Map<String, ZipEntry> entries)
            throws IOException, JSONException {
        if (manifest.getInt("schemaVersion") != 1) throw new IOException("Unsupported graphics package schema.");
        String id = requireIdentifier(manifest, "id");
        String version = requireIdentifier(manifest, "version");
        String name = requireText(manifest, "name");
        GraphicsRuntimePackage.Kind kind;
        try {
            kind = GraphicsRuntimePackage.Kind.valueOf(manifest.getString("kind").toUpperCase(Locale.ROOT).replace('-', '_'));
        }
        catch (IllegalArgumentException e) {
            throw new IOException("Unsupported graphics package kind.", e);
        }
        String upstream = requireHttps(manifest, "upstream");
        String revision = requireText(manifest, "revision");
        String license = requireText(manifest, "license");
        String abi = requireText(manifest, "abi");
        if (!ABI.equals(abi)) throw new IOException("Package ABI does not match this Vanta build: " + abi);
        String loader = requireText(manifest, "loader").toLowerCase(Locale.ROOT);
        if (!loader.equals("adrenotools") && !loader.equals("android-vulkan-loader")) {
            throw new IOException("Unsupported graphics package loader.");
        }
        if (kind == GraphicsRuntimePackage.Kind.VULKAN_DRIVER && !loader.equals("adrenotools")) {
            throw new IOException("Vulkan driver packages must target the Vanta adrenotools loading path.");
        }
        String licenseFile = requireArchivePath(manifest, "licenseFile");
        if (!entries.containsKey(licenseFile) || entries.get(licenseFile).isDirectory()) {
            throw new IOException("The package license file is absent.");
        }
        String libraryPath = requireArchivePath(manifest, "libraryPath");
        if (libraryPath.equals(licenseFile)) throw new IOException("The package license cannot be its Vulkan library.");
        if (!libraryPath.startsWith("lib/" + ABI + "/") || !libraryPath.endsWith(".so")) {
            throw new IOException("The primary library must be an ARM64 shared library.");
        }
        if (kind == GraphicsRuntimePackage.Kind.VULKAN_DRIVER &&
                !new File(libraryPath).getName().startsWith("libvulkan")) {
            throw new IOException("A Vulkan driver package must provide a Vulkan ICD library.");
        }

        Map<String, String> files = new HashMap<>();
        JSONArray libraryEntries = manifest.getJSONArray("libraries");
        if (libraryEntries.length() == 0 || libraryEntries.length() > MAX_FILE_COUNT) {
            throw new IOException("The package library list is empty or too large.");
        }
        for (int i = 0; i < libraryEntries.length(); i++) {
            JSONObject library = libraryEntries.getJSONObject(i);
            String path = requireArchivePath(library, "path");
            String hash = requireText(library, "sha256");
            if (!SHA256.matcher(hash).matches() || !path.startsWith("lib/" + ABI + "/") ||
                    !path.endsWith(".so") || files.put(path, hash.toLowerCase(Locale.ROOT)) != null) {
                throw new IOException("Invalid or duplicate package library metadata.");
            }
        }
        if (files.containsKey(licenseFile)) throw new IOException("The license path cannot also be a shared library.");
        String primaryHash = requireText(manifest, "sha256");
        if (!SHA256.matcher(primaryHash).matches() || !primaryHash.equalsIgnoreCase(files.get(libraryPath))) {
            throw new IOException("The primary library SHA-256 does not match its package file entry.");
        }
        for (String path : files.keySet()) {
            if (!entries.containsKey(path) || entries.get(path).isDirectory()) {
                throw new IOException("The archive is missing " + path);
            }
        }

        Set<String> families = readStringSet(manifest.optJSONArray("supportedGpuFamilies"));
        Set<String> models = readStringSet(manifest.optJSONArray("supportedGpuModels"));
        String kernel = requireText(manifest, "kernelRequirement");
        String kbase = requireText(manifest, "kbaseRequirement");
        String schedulerText = requireText(manifest, "schedulerRequirement").toUpperCase(Locale.ROOT).replace('-', '_');
        GraphicsRuntimePackage.Scheduler scheduler;
        try {
            scheduler = GraphicsRuntimePackage.Scheduler.valueOf(schedulerText);
        }
        catch (IllegalArgumentException e) {
            throw new IOException("Unsupported GPU scheduler requirement.", e);
        }
        String vulkanVersion = requireText(manifest, "vulkanVersion");
        boolean experimental = manifest.getBoolean("experimental");
        String limitations = requireText(manifest, "limitations");
        return new ValidatedManifest(id, name, version, kind, upstream, revision, license, abi,
                primaryHash.toLowerCase(Locale.ROOT), loader, libraryPath, licenseFile, families, models,
                kernel, kbase, scheduler, vulkanVersion, experimental, limitations, files);
    }

    private static GraphicsRuntimePackage readInstalledPackage(File directory) throws IOException, JSONException {
        File manifestFile = new File(directory, MANIFEST_FILENAME);
        if (!manifestFile.isFile() || manifestFile.length() > 256 * 1024) return null;
        byte[] bytes = readFile(manifestFile, 256 * 1024);
        JSONObject manifest = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        String libraryPath = requireArchivePath(manifest, "libraryPath");
        String libraryHash = requireText(manifest, "sha256").toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(libraryHash).matches()) return null;
        JSONArray libraries = manifest.getJSONArray("libraries");
        for (int i = 0; i < libraries.length(); i++) {
            JSONObject entry = libraries.getJSONObject(i);
            String path = requireArchivePath(entry, "path");
            String hash = requireText(entry, "sha256").toLowerCase(Locale.ROOT);
            File library = resolveChild(directory, path);
            if (!SHA256.matcher(hash).matches() || !library.isFile() ||
                    !sha256(readFile(library, MAX_SINGLE_FILE_BYTES)).equals(hash) ||
                    !isAarch64Elf(library)) return null;
        }
        File library = resolveChild(directory, libraryPath);
        if (!library.isFile() || !sha256(readFile(library, MAX_SINGLE_FILE_BYTES)).equals(libraryHash)) return null;
        File license = resolveChild(directory, requireArchivePath(manifest, "licenseFile"));
        if (!license.isFile() || license.length() == 0 || license.length() > 1024 * 1024) return null;
        ValidatedManifest validated = validateManifestForInstalled(manifest, directory);
        return validated.toPackage(directory);
    }

    private static ValidatedManifest validateManifestForInstalled(JSONObject manifest, File directory)
            throws IOException, JSONException {
        Map<String, ZipEntry> entries = new HashMap<>();
        for (String path : collectManifestPaths(manifest)) {
            File file = resolveChild(directory, path);
            if (!file.isFile()) throw new IOException("Installed package file missing: " + path);
            entries.put(path, new ZipEntry(path));
        }
        entries.put(MANIFEST_FILENAME, new ZipEntry(MANIFEST_FILENAME));
        return validateManifest(manifest, entries);
    }

    private static Set<String> collectManifestPaths(JSONObject manifest) throws JSONException, IOException {
        Set<String> paths = new HashSet<>();
        JSONArray libraries = manifest.getJSONArray("libraries");
        for (int i = 0; i < libraries.length(); i++) paths.add(requireArchivePath(libraries.getJSONObject(i), "path"));
        paths.add(requireArchivePath(manifest, "licenseFile"));
        return paths;
    }

    private static Set<String> readStringSet(JSONArray array) throws JSONException, IOException {
        Set<String> values = new HashSet<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            String value = array.getString(i).trim().toLowerCase(Locale.ROOT);
            if (!value.matches("[a-z0-9][a-z0-9._+-]{0,63}")) throw new IOException("Invalid GPU compatibility metadata.");
            values.add(value);
        }
        return values;
    }

    private static byte[] readEntry(ZipFile zip, ZipEntry entry, long maxBytes) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return readLimited(input, maxBytes);
        }
    }

    private static byte[] readLimited(InputStream input, long maxBytes) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("Package metadata exceeds its size limit.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String copyEntry(ZipFile zip, ZipEntry entry, File destination, long maxBytes) throws IOException {
        MessageDigest digest = newSha256();
        long total = 0;
        try (InputStream input = zip.getInputStream(entry);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IOException("Package file exceeds its size limit.");
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        }
        return hex(digest.digest());
    }

    private static File resolveChild(File root, String path) throws IOException {
        validateRelativePath(path);
        File child = new File(root, path);
        if (!child.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) {
            throw new IOException("Package path escapes its installation directory.");
        }
        return child;
    }

    private static void validateRelativePath(String path) throws IOException {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.startsWith("\\") ||
                path.contains("\\") || path.contains(":")) throw new IOException("Invalid archive path.");
        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty() && i == segments.length - 1 && path.endsWith("/")) continue;
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) throw new IOException("Invalid archive path.");
        }
    }

    private static String requireArchivePath(JSONObject object, String key) throws JSONException, IOException {
        String path = requireText(object, key);
        validateRelativePath(path);
        return path;
    }

    private static String requireIdentifier(JSONObject object, String key) throws JSONException, IOException {
        String value = requireText(object, key);
        if (!IDENTIFIER.matcher(value).matches()) throw new IOException("Invalid package " + key + ".");
        return value;
    }

    private static String requireText(JSONObject object, String key) throws JSONException, IOException {
        String value = object.getString(key).trim();
        if (value.isEmpty() || value.length() > 512) throw new IOException("Missing or invalid package metadata: " + key);
        return value;
    }

    private static String requireHttps(JSONObject object, String key) throws JSONException, IOException {
        String value = requireText(object, key);
        if (!value.startsWith("https://") || value.contains(" ") || value.length() > 2048) {
            throw new IOException("Package upstream must be an HTTPS source URL.");
        }
        return value;
    }

    private static boolean isAarch64Elf(File file) throws IOException {
        byte[] header;
        try (InputStream input = new FileInputStream(file)) {
            header = new byte[20];
            int total = 0;
            while (total < header.length) {
                int read = input.read(header, total, header.length - total);
                if (read < 0) return false;
                total += read;
            }
        }
        int machine = (header[18] & 0xff) | ((header[19] & 0xff) << 8);
        return header[0] == 0x7f && header[1] == 'E' && header[2] == 'L' && header[3] == 'F' &&
                header[4] == 2 && header[5] == 1 && machine == 183;
    }

    private static byte[] readFile(File file, long maxBytes) throws IOException {
        if (file.length() > maxBytes) throw new IOException("Package file exceeds its size limit.");
        try (InputStream input = new FileInputStream(file)) {
            return readLimited(input, maxBytes);
        }
    }

    private static void writeBytes(File destination, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(destination)) {
            output.write(bytes);
            output.getFD().sync();
        }
    }

    private static String sha256(byte[] bytes) {
        return hex(newSha256().digest(bytes));
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static final class ValidatedManifest {
        final String id, name, version;
        final GraphicsRuntimePackage.Kind kind;
        final String upstream, revision, license, abi, sha256, loader, libraryPath, licenseFile;
        final Set<String> gpuFamilies, gpuModels;
        final String kernelRequirement, kbaseRequirement;
        final GraphicsRuntimePackage.Scheduler scheduler;
        final String vulkanVersion;
        final boolean experimental;
        final String limitations;
        final Map<String, String> files;

        ValidatedManifest(String id, String name, String version, GraphicsRuntimePackage.Kind kind,
                          String upstream, String revision, String license, String abi, String sha256,
                          String loader, String libraryPath, String licenseFile, Set<String> gpuFamilies, Set<String> gpuModels,
                          String kernelRequirement, String kbaseRequirement, GraphicsRuntimePackage.Scheduler scheduler,
                          String vulkanVersion, boolean experimental, String limitations, Map<String, String> files) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.kind = kind;
            this.upstream = upstream;
            this.revision = revision;
            this.license = license;
            this.abi = abi;
            this.sha256 = sha256;
            this.loader = loader;
            this.libraryPath = libraryPath;
            this.licenseFile = licenseFile;
            this.gpuFamilies = gpuFamilies;
            this.gpuModels = gpuModels;
            this.kernelRequirement = kernelRequirement;
            this.kbaseRequirement = kbaseRequirement;
            this.scheduler = scheduler;
            this.vulkanVersion = vulkanVersion;
            this.experimental = experimental;
            this.limitations = limitations;
            this.files = files;
        }

        GraphicsRuntimePackage toPackage(File directory) {
            return new GraphicsRuntimePackage(id, name, version, kind, upstream, revision, license,
                    abi, sha256, loader, libraryPath, gpuFamilies, gpuModels, kernelRequirement,
                    kbaseRequirement, scheduler, vulkanVersion, experimental, limitations, directory);
        }
    }
}
