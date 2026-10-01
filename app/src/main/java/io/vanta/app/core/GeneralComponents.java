package io.vanta.app.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.Spinner;

import io.vanta.app.MainActivity;
import io.vanta.app.R;
import io.vanta.app.container.Container;
import io.vanta.app.container.ContainerManager;
import io.vanta.app.container.DXWrappers;
import io.vanta.app.container.GraphicsDrivers;
import io.vanta.app.contentdialog.ContentDialog;
import io.vanta.app.xenvironment.RootFS;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public abstract class GeneralComponents {
    public enum InstallMode {DOWNLOAD, FILE, BOTH}
    private static final String INSTALLABLE_COMPONENTS_URL = "https://raw.githubusercontent.com/brunodev85/winlator/main/installable_components/%s";

    public enum Type {
        BOX64, TURNIP, DXVK, VKD3D, WINED3D, SOUNDFONT, ADRENOTOOLS_DRIVER;

        private String lowerName() {
            return name().toLowerCase(Locale.ENGLISH);
        }

        private String title() {
            switch (this) {
                case BOX64:
                    return "Box64";
                case TURNIP:
                    return "Turnip";
                case DXVK:
                    return "DXVK";
                case VKD3D:
                    return "VKD3D";
                case WINED3D:
                    return "WineD3D";
                case SOUNDFONT:
                    return "SoundFont";
                case ADRENOTOOLS_DRIVER:
                    return "Adrenotools Driver";
            }

            return "";
        }

        private String assetFolder() {
            switch (this) {
                case BOX64:
                    return "box64";
                case TURNIP:
                    return "graphics_driver";
                case WINED3D:
                case DXVK:
                case VKD3D:
                    return "dxwrapper";
                case SOUNDFONT:
                    return "soundfont";
            }

            return "";
        }

        private File getSource(Context context, String identifier) {
            File componentDir = getComponentDir(this, context);
            switch (this) {
                case SOUNDFONT:
                    return new File(componentDir, identifier+".sf2");
                case ADRENOTOOLS_DRIVER:
                    return new File(componentDir, identifier);
                default:
                    return new File(componentDir, lowerName()+"-"+identifier+".tzst");
            }
        }

        public File getDestination(Context context) {
            File rootDir = RootFS.find(context).getRootDir();
            switch (this) {
                case DXVK:
                case VKD3D:
                case WINED3D:
                    return new File(rootDir, RootFS.WINEPREFIX+"/drive_c/windows");
                case SOUNDFONT:
                    File destination = new File(context.getCacheDir(), "soundfont");
                    if (!destination.isDirectory()) destination.mkdirs();
                    return destination;
                default:
                    return rootDir;
            }
        }

        private InstallMode getInstallMode() {
            InstallMode installMode;
            if (this == Type.SOUNDFONT || this == ADRENOTOOLS_DRIVER) {
                installMode = InstallMode.FILE;
            }
            else if (this == Type.WINED3D || this == Type.DXVK || this == Type.VKD3D) {
                installMode = InstallMode.BOTH;
            }
            else installMode = InstallMode.DOWNLOAD;
            return installMode;
        }

        private boolean isVersioned() {
            return this == BOX64 || this == TURNIP || this == DXVK || this == VKD3D || this == WINED3D;
        }
    }

    public static ArrayList<String> getBuiltinComponentNames(Type type) {
        String[] items = new String[0];

        switch (type) {
            case BOX64:
                items = new String[]{DefaultVersion.BOX64};
                break;
            case TURNIP:
                items = new String[]{DefaultVersion.TURNIP};
                break;
            case DXVK:
                items = new String[]{DefaultVersion.MINOR_DXVK, DefaultVersion.MAJOR_DXVK};
                break;
            case VKD3D:
                items = new String[]{DefaultVersion.VKD3D};
                break;
            case WINED3D:
                items = new String[]{DefaultVersion.WINED3D};
                break;
            case SOUNDFONT:
                items = new String[]{DefaultVersion.SOUNDFONT};
                break;
            case ADRENOTOOLS_DRIVER:
                items = new String[]{"System"};
                break;
        }

        return new ArrayList<>(Arrays.asList(items));
    }

    public static File getComponentDir(Type type, Context context) {
        File file = new File(context.getFilesDir(), "/installed_components/"+type.lowerName());
        if (!file.isDirectory()) file.mkdirs();
        return file;
    }

    public static ArrayList<String> getInstalledComponentNames(Type type, Context context) {
        File componentDir = getComponentDir(type, context);
        ArrayList<String> result = new ArrayList<>();

        String[] names = componentDir.list((dir, name) -> name.startsWith(type.lowerName()+"-") && name.endsWith(".tzst"));
        if (names != null) {
            for (String name : names) result.add(parseDisplayText(type, name));
        }
        return result;
    }

    public static ArrayList<String> getAvailableComponentNames(Type type, Context context) {
        ArrayList<String> result = getBuiltinComponentNames(type);
        for (String name : getInstalledComponentNames(type, context)) {
            if (!result.contains(name)) result.add(name);
        }
        return result;
    }

    public static boolean isBuiltinComponent(Type type, String identifier) {
        for (String builtinComponentName : getBuiltinComponentNames(type)) {
            if (builtinComponentName.equalsIgnoreCase(identifier)) return true;
        }
        return false;
    }

    public static String getDefinitivePath(Type type, Context context, String identifier) {
        if (identifier.isEmpty()) return null;
        if (type == Type.SOUNDFONT && isBuiltinComponent(type, identifier)) {
            File destination = type.getDestination(context);
            FileUtils.clear(destination);

            String filename = identifier+".sf2";
            destination = new File(destination, filename);
            FileUtils.copy(context, type.assetFolder()+"/"+filename, destination);
            return destination.getPath();
        }
        else if (type == Type.ADRENOTOOLS_DRIVER) {
            if (isBuiltinComponent(type, identifier)) return null;
            File source = type.getSource(context, identifier);
            File[] manifestFiles = source.listFiles((file, name) -> name.endsWith(".json"));
            if (manifestFiles != null) {
                try {
                    JSONObject manifestJSONObject = new JSONObject(FileUtils.readString(manifestFiles[0]));
                    String libraryName = manifestJSONObject.optString("libraryName", "");
                    File libraryFile = new File(source, libraryName);
                    return libraryFile.isFile() ? libraryFile.getPath() : null;
                }
                catch (JSONException e) {
                    return null;
                }
            }
        }

        return type.getSource(context, identifier).getPath();
    }

    public static boolean extractFile(Type type, Context context, String identifier, String defaultVersion) {
        return extractFile(type, context, identifier, defaultVersion, null);
    }

    public static boolean extractFile(Type type, Context context, String identifier, String defaultVersion, TarCompressorUtils.OnExtractFileListener onExtractFileListener) {
        File destination = type.getDestination(context);
        String selectedVersion = identifier != null && !identifier.isEmpty() ? identifier : defaultVersion;

        if (isBuiltinComponent(type, selectedVersion)) {
            String sourcePath = type.assetFolder()+"/"+type.lowerName()+"-"+selectedVersion+".tzst";
            return TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, sourcePath, destination, onExtractFileListener);
        }
        else {
            File componentDir = getComponentDir(type, context);
            File source = new File(componentDir, type.lowerName()+"-"+selectedVersion+".tzst");
            if (source.isFile()) {
                return TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, source, destination, onExtractFileListener);
            }
            String sourcePath = type.assetFolder()+"/"+type.lowerName()+"-"+defaultVersion+".tzst";
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, sourcePath, destination, onExtractFileListener);
            return false;
        }
    }

    private static String parseDisplayText(Type type, String filename) {
        return filename.replace(type.lowerName()+"-", "").replace(".tzst", "").replace(".sf2", "");
    }

    private static void downloadComponentFile(final Type type, final String filename, final Spinner spinner, final String defaultItem, final Runnable onComponentsChanged) {
        final Activity activity = (Activity)spinner.getContext();
        File destination = new File(getComponentDir(type, activity), filename);
        String identifier = parseDisplayText(type, filename);
        if (destination.isFile() && isComponentInUse(type, identifier, activity)) {
            AppUtils.showToast(activity, R.string.component_in_use);
            return;
        }
        File candidate = new File(destination.getPath()+".download");
        if (candidate.exists() && !FileUtils.delete(candidate)) {
            AppUtils.showToast(activity, R.string.component_removal_failed);
            return;
        }
        HttpUtils.download(activity, String.format(INSTALLABLE_COMPONENTS_URL, type.lowerName()+"/"+filename), candidate, (success) -> {
            boolean packageDownloaded = success && candidate.isFile() && candidate.length() > 0;
            if (packageDownloaded && replaceComponentArchive(candidate, destination)) {
                loadSpinner(type, spinner, identifier, defaultItem);
                if (onComponentsChanged != null) onComponentsChanged.run();
            }
            else {
                FileUtils.delete(candidate);
                AppUtils.showToast(activity, success ? R.string.unable_to_install_component : R.string.a_network_error_occurred);
            }
        });
    }

    private static boolean installFromPackagedFile(Context context, TarCompressorUtils.Type compressedType, final Type type, File originFile, String identifier, JSONArray filesJSONArray) throws JSONException {
        File componentDir = getComponentDir(type, context);
        File tempDir = new File(componentDir, type.lowerName()+"-"+identifier);
        if (tempDir.isDirectory()) FileUtils.delete(tempDir);
        if (!tempDir.mkdirs()) return false;

        Map<String, File> targetFiles = new HashMap<>();
        boolean hasSystem32 = false;
        boolean hasSyswow64 = false;
        for (int i = 0; i < filesJSONArray.length(); i++) {
            JSONObject fileJSONObject = filesJSONArray.getJSONObject(i);
            String target = fileJSONObject.getString("target");
            String source = fileJSONObject.getString("source");
            String lowerTarget = target.toLowerCase(Locale.ENGLISH);
            String directory = lowerTarget.contains("system32") ? "system32" :
                    (lowerTarget.contains("syswow64") ? "syswow64" : null);
            if (directory == null || source.isEmpty()) continue;

            File targetFile = new File(tempDir, directory+"/"+FileUtils.getName(target));
            File parent = targetFile.getParentFile();
            if (parent == null || !parent.isDirectory() && !parent.mkdirs()) {
                FileUtils.delete(tempDir);
                return false;
            }
            targetFiles.put(source, targetFile);
            hasSystem32 |= directory.equals("system32");
            hasSyswow64 |= directory.equals("syswow64");
        }

        if (targetFiles.isEmpty() || !hasSystem32 || !hasSyswow64 ||
                !TarCompressorUtils.extract(compressedType, originFile, tempDir, (sourceFile, size) -> {
                    for (Map.Entry<String, File> target : targetFiles.entrySet()) {
                        if (sourceFile.getPath().endsWith(target.getKey())) return target.getValue();
                    }
                    return null;
                })) {
            FileUtils.delete(tempDir);
            return false;
        }

        for (File targetFile : targetFiles.values()) {
            if (!targetFile.isFile() || targetFile.length() == 0) {
                FileUtils.delete(tempDir);
                return false;
            }
        }

        String filename = type.lowerName()+"-"+identifier+".tzst";
        File destination = new File(componentDir, filename);
        File candidate = new File(componentDir, filename+".tmp");
        FileUtils.delete(candidate);
        TarCompressorUtils.compress(TarCompressorUtils.Type.ZSTD, new File(tempDir, "."), candidate, MainActivity.CONTAINER_PATTERN_COMPRESSION_LEVEL);
        if (!candidate.isFile() || candidate.length() == 0) {
            FileUtils.delete(tempDir);
            FileUtils.delete(candidate);
            return false;
        }

        if (destination.exists() && isComponentInUse(type, identifier, context)) {
            FileUtils.delete(tempDir);
            FileUtils.delete(candidate);
            return false;
        }
        if (!replaceComponentArchive(candidate, destination)) {
            FileUtils.delete(tempDir);
            FileUtils.delete(candidate);
            return false;
        }
        FileUtils.delete(tempDir);
        return true;
    }

    private static boolean replaceComponentArchive(File candidate, File destination) {
        File backup = new File(destination.getPath()+".backup");
        if (backup.exists() && !FileUtils.delete(backup)) return false;

        boolean hadDestination = destination.exists();
        if (hadDestination && !destination.renameTo(backup)) return false;
        if (candidate.renameTo(destination)) {
            if (hadDestination) FileUtils.delete(backup);
            return true;
        }

        if (hadDestination) backup.renameTo(destination);
        return false;
    }

    private static void openFileForInstall(final MainActivity activity, final Type type, final Spinner spinner, final String defaultItem, final Runnable onComponentsChanged) {
        activity.setOpenFileCallback((uri) -> {
            String path = FileUtils.getFilePathFromUri(uri);
            if (path == null) {
                AppUtils.showToast(activity, R.string.unable_to_open_component_package);
                return;
            }

            try {
                File source = new File(path);
                switch (type) {
                    case SOUNDFONT: {
                        String filename = FileUtils.getName(path);
                        File destination = new File(getComponentDir(type, activity), filename);
                        if (destination.isFile()) FileUtils.delete(destination);
                        if (FileUtils.copy(source, destination)) {
                            loadSpinner(type, spinner, parseDisplayText(type, filename), defaultItem);
                            if (onComponentsChanged != null) onComponentsChanged.run();
                        }
                        break;
                    }
                    case ADRENOTOOLS_DRIVER: {
                        byte[] manifestData = ZipUtils.read(source, "*.json");
                        if (manifestData != null) {
                            JSONObject manifestJSONObject = new JSONObject(new String(manifestData));
                            String filename = manifestJSONObject.optString("name", manifestJSONObject.optString("libraryName", ""));
                            File destination = new File(getComponentDir(type, activity), filename);
                            if (destination.isDirectory()) FileUtils.delete(destination);
                            destination.mkdirs();
                            if (ZipUtils.extract(source, destination)) {
                                loadSpinner(type, spinner, filename, defaultItem);
                                if (onComponentsChanged != null) onComponentsChanged.run();
                            }
                        }
                        break;
                    }
                    default: {
                        TarCompressorUtils.Type compressedType = TarCompressorUtils.Type.ZSTD;
                        byte[] manifestData = TarCompressorUtils.read(compressedType, source, "*.json");
                        if (manifestData == null) manifestData = TarCompressorUtils.read(compressedType = TarCompressorUtils.Type.XZ, source, "*.json");
                        if (manifestData != null) {
                            JSONObject manifestJSONObject = new JSONObject(new String(manifestData));
                            String contentType = manifestJSONObject.optString("type", "").toUpperCase(Locale.ENGLISH);
                            String identifier = StringUtils.parseIdentifier(manifestJSONObject.optString("versionName", ""));
                            JSONArray filesJSONArray = manifestJSONObject.optJSONArray("files");

                            if (contentType.equals(type.name()) && isSafeIdentifier(identifier) && filesJSONArray != null &&
                                    installFromPackagedFile(activity, compressedType, type, source, identifier, filesJSONArray)) {
                                loadSpinner(type, spinner, identifier, defaultItem);
                                if (onComponentsChanged != null) onComponentsChanged.run();
                            }
                            else AppUtils.showToast(activity, R.string.unable_to_install_component);
                        }
                        else AppUtils.showToast(activity, R.string.unable_to_install_component);
                        break;
                    }
                }
            }
            catch (JSONException e) {
                AppUtils.showToast(activity, R.string.unable_to_install_component);
            }
        });

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        activity.startActivityForResult(intent, MainActivity.OPEN_FILE_REQUEST_CODE);
    }

    private static void showDownloadableListDialog(Type type, final Spinner spinner, final String defaultItem, final Runnable onComponentsChanged) {
        final Activity activity = (Activity)spinner.getContext();
        final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        preloaderDialog.show(R.string.loading);
        HttpUtils.download(String.format(INSTALLABLE_COMPONENTS_URL, type.lowerName()+"/index.txt"), (content) -> activity.runOnUiThread(() -> {
            preloaderDialog.close();
            if (content != null) {
                if (content.isEmpty()) {
                    AppUtils.showToast(activity, R.string.there_are_no_items_to_download);
                    return;
                }
                final String[] filenames = content.split("\n");
                final String[] items = filenames.clone();
                for (int i = 0; i < items.length; i++) {
                    items[i] = type.title()+" "+parseDisplayText(type, items[i]);
                }

                ContentDialog.showSelectionList(activity, R.string.install_component, items, false, (positions) -> {
                    if (!positions.isEmpty()) downloadComponentFile(type, filenames[positions.get(0)], spinner, defaultItem, onComponentsChanged);
                });
            }
            else AppUtils.showToast(activity, R.string.a_network_error_occurred);
        }));
    }

    public static void initViews(final Type type, View toolbox, final Spinner spinner, final String selectedItem, final String defaultItem) {
        initViews(type, toolbox, spinner, selectedItem, defaultItem, null);
    }

    public static void initViews(final Type type, View toolbox, final Spinner spinner, final String selectedItem, final String defaultItem, final Runnable onComponentsChanged) {
        final Context context = spinner.getContext();
        toolbox.findViewWithTag("install").setOnClickListener((v) -> {
            InstallMode installMode = type.getInstallMode();
            switch (installMode) {
                case DOWNLOAD:
                    showDownloadableListDialog(type, spinner, defaultItem, onComponentsChanged);
                    break;
                case FILE:
                    openFileForInstall((MainActivity)context, type, spinner, defaultItem, onComponentsChanged);
                    break;
                case BOTH:
                    PopupMenu popupMenu = new PopupMenu(context, v);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) popupMenu.setForceShowIcon(true);
                    popupMenu.inflate(R.menu.open_file_popup_menu);
                    popupMenu.setOnMenuItemClickListener((menuItem) -> {
                        int itemId = menuItem.getItemId();
                        if (itemId == R.id.menu_item_open_file) {
                            openFileForInstall((MainActivity)context, type, spinner, defaultItem, onComponentsChanged);
                        }
                        else if (itemId == R.id.menu_item_download_file) {
                            showDownloadableListDialog(type, spinner, defaultItem, onComponentsChanged);
                        }
                        return true;
                    });
                    popupMenu.show();
                    break;
            }
        });

        toolbox.findViewWithTag("remove").setOnClickListener((v) -> {
            String identifier = spinner.getSelectedItem().toString();

            if (isBuiltinComponent(type, identifier)) {
                AppUtils.showToast(context, R.string.you_cannot_remove_this_component_version);
                return;
            }

            File source = type.getSource(context, identifier);
            if (source.exists()) {
                if (isComponentInUse(type, identifier, context)) {
                    AppUtils.showToast(context, R.string.component_in_use);
                    return;
                }
                ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_component_version, () -> {
                    if (!FileUtils.delete(source)) {
                        AppUtils.showToast(context, R.string.component_removal_failed);
                        return;
                    }
                    loadSpinner(type, spinner, selectedItem, defaultItem);
                    if (onComponentsChanged != null) onComponentsChanged.run();
                });
            }
        });

        loadSpinner(type, spinner, selectedItem, defaultItem);
    }

    private static boolean isComponentInUse(Type type, String identifier, Context context) {
        if (type == Type.BOX64) {
            android.content.SharedPreferences preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
            if (identifier.equals(preferences.getString("box64_version", DefaultVersion.BOX64)) ||
                    identifier.equals(preferences.getString("current_box64_version", ""))) return true;
        }

        ContainerManager containerManager = new ContainerManager(context);
        for (Container container : containerManager.getContainers()) {
            if (type == Type.BOX64) {
                String selectedVersion = container.getBox64Version();
                if (selectedVersion.isEmpty()) {
                    selectedVersion = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                            .getString("box64_version", DefaultVersion.BOX64);
                }
                if (identifier.equals(selectedVersion)) return true;
            }
            else if (type == Type.DXVK || type == Type.VKD3D || type == Type.WINED3D) {
                KeyValueSet[] configs = DXWrappers.parseConfigs(container.getDXWrapper(), container.getDXWrapperConfig());
                int index = type == Type.VKD3D ? 1 : 0;
                String wrapper = type == Type.DXVK ? DXWrappers.DXVK :
                        (type == Type.VKD3D ? DXWrappers.VKD3D : DXWrappers.WINED3D);
                if (type == Type.VKD3D) {
                    if (identifier.equals(configs[index].get("version", DefaultVersion.VKD3D))) return true;
                }
                else if (container.getDXWrapper().equals(wrapper)) {
                    String[] drivers = GraphicsDrivers.parseIdentifiers(container.getGraphicsDriver());
                    String defaultVersion = type == Type.DXVK ? DefaultVersion.DXVK(drivers[0]) : DefaultVersion.WINED3D;
                    if (identifier.equals(configs[index].get("version", defaultVersion))) return true;
                }
            }
            else if (type == Type.TURNIP) {
                String[] drivers = GraphicsDrivers.parseIdentifiers(container.getGraphicsDriver());
                KeyValueSet[] configs = GraphicsDrivers.parseConfigs(container.getGraphicsDriver(), container.getGraphicsDriverConfig());
                if (drivers[0].equals(GraphicsDrivers.TURNIP) &&
                        identifier.equals(configs[0].get("version", DefaultVersion.TURNIP))) return true;
            }
        }
        return false;
    }

    private static boolean isSafeIdentifier(String identifier) {
        return identifier.matches("[a-zA-Z0-9][a-zA-Z0-9._+-]*");
    }

    public static void loadVersionSpinner(Type type, Spinner spinner, String selectedItem, String defaultItem) {
        loadSpinner(type, spinner, selectedItem, defaultItem);
    }

    private static void loadSpinner(Type type, Spinner spinner, String selectedItem, String defaultItem) {
        ArrayList<String> items = getAvailableComponentNames(type, spinner.getContext());

        if (type.isVersioned()) {
            items.sort((o1, o2) -> Integer.compare(GPUHelper.vkMakeVersion(o1), GPUHelper.vkMakeVersion(o2)));
        }

        spinner.setAdapter(new ArrayAdapter<>(spinner.getContext(), android.R.layout.simple_spinner_dropdown_item, items));

        if (selectedItem == null || selectedItem.isEmpty() || !AppUtils.setSpinnerSelectionFromValue(spinner, selectedItem)) {
            AppUtils.setSpinnerSelectionFromValue(spinner, defaultItem);
        }
    }
}