package io.vanta.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import android.app.NotificationManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import io.vanta.app.box64.Box64EditPresetDialog;
import io.vanta.app.box64.Box64Preset;
import io.vanta.app.box64.Box64PresetManager;
import io.vanta.app.container.Container;
import io.vanta.app.container.ContainerManager;
import io.vanta.app.container.DXWrappers;
import io.vanta.app.container.GraphicsDrivers;
import io.vanta.app.contentdialog.ContentDialog;
import io.vanta.app.contentdialog.GamepadPlayerConfigDialog;
import io.vanta.app.contentdialog.SoundFontTestDialog;
import io.vanta.app.core.AppUtils;
import io.vanta.app.core.ArrayUtils;
import io.vanta.app.core.Callback;
import io.vanta.app.core.DefaultVersion;
import io.vanta.app.core.FileUtils;
import io.vanta.app.core.GeneralComponents;
import io.vanta.app.core.GpuCapabilityProfile;
import io.vanta.app.core.GraphicsRuntimePackage;
import io.vanta.app.core.GraphicsRuntimePackageStore;
import io.vanta.app.core.KeyValueSet;
import io.vanta.app.core.LocaleHelper;
import io.vanta.app.core.MaliCapabilityProbe;
import io.vanta.app.core.PreloaderDialog;
import io.vanta.app.core.StringUtils;
import io.vanta.app.core.UnitUtils;
import io.vanta.app.core.WineInfo;
import io.vanta.app.core.WineInstaller;
import io.vanta.app.services.NotificationUtils;
import io.vanta.app.widget.ColorPickerView;
import io.vanta.app.widget.LogView;
import io.vanta.app.widget.SeekBar;
import io.vanta.app.winhandler.GamepadHandler;
import io.vanta.app.xenvironment.RootFS;
import io.vanta.app.xenvironment.RootFSInstaller;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class SettingsFragment extends Fragment {
    public static final String SECTION_ARGUMENT = "settings_section";
    public static final String SECTION_RUNTIME_MANAGER = "runtime_manager";
    public static final String SECTION_BOX64 = "box64";
    public static final String DEFAULT_WINE_DEBUG_CHANNELS = "warn,err,fixme";
    public static final byte APP_THEME_LIGHT = 0;
    public static final byte APP_THEME_DARK = 1;
    private Callback<Uri> selectWineFileCallback;
    private PreloaderDialog preloaderDialog;
    private SharedPreferences preferences;
    private boolean midiDeviceCallbackRegistered = false;
    private final AtomicLong graphicsPackageRefreshId = new AtomicLong();

    public static SettingsFragment newInstance(String section) {
        SettingsFragment fragment = new SettingsFragment();
        Bundle arguments = new Bundle();
        arguments.putString(SECTION_ARGUMENT, section);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String section = getArguments() != null ? getArguments().getString(SECTION_ARGUMENT) : null;
        int title = SECTION_RUNTIME_MANAGER.equals(section) ? R.string.runtime_manager :
                SECTION_BOX64.equals(section) ? R.string.box64_settings : R.string.settings;
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(title);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            try {
                if (selectWineFileCallback != null && data != null) selectWineFileCallback.call(data.getData());
            }
            catch (Exception e) {
                AppUtils.showToast(getContext(), R.string.unable_to_import_profile);
            }
            selectWineFileCallback = null;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.settings_fragment, container, false);
        final Context context = getContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        final Spinner sSoundFont = view.findViewById(R.id.SSoundFont);
        String soundfont = preferences.getString("soundfont", null);
        GeneralComponents.initViews(GeneralComponents.Type.SOUNDFONT, view.findViewById(R.id.SoundFontToolbox), sSoundFont, soundfont, DefaultVersion.SOUNDFONT);
        view.findViewById(R.id.BTSoundFontTest).setOnClickListener((v) -> (new SoundFontTestDialog(context, sSoundFont.getSelectedItem().toString())).show());

        final Spinner sMIDIInputDevice = view.findViewById(R.id.SMIDIInputDevice);
        String midiInputDevice = preferences.getString("midi_input_device", "auto");
        loadMIDIInputDeviceSpinner(sMIDIInputDevice, midiInputDevice);

        final Spinner sBox64Version = view.findViewById(R.id.SBox64Version);
        String box64Version = preferences.getString("box64_version", null);
        GeneralComponents.initViews(GeneralComponents.Type.BOX64, view.findViewById(R.id.Box64Toolbox), sBox64Version, box64Version, DefaultVersion.BOX64);

        final Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        loadBox64PresetSpinner(view, sBox64Preset);

        final Spinner sDXVKManagerVersion = view.findViewById(R.id.SDXVKManagerVersion);
        Runnable refreshDXVKVersions = () -> showDXVKManagerVersions(view);
        GeneralComponents.initViews(GeneralComponents.Type.DXVK, view.findViewById(R.id.DXVKManagerToolbox),
                sDXVKManagerVersion, null, DefaultVersion.MAJOR_DXVK, refreshDXVKVersions);
        refreshDXVKVersions.run();

        final Spinner sTurnipManagerVersion = view.findViewById(R.id.STurnipManagerVersion);
        Runnable refreshTurnipVersions = () -> showTurnipManagerVersions(view);
        GeneralComponents.initViews(GeneralComponents.Type.TURNIP, view.findViewById(R.id.TurnipManagerToolbox),
                sTurnipManagerVersion, null, DefaultVersion.TURNIP, refreshTurnipVersions);
        refreshTurnipVersions.run();
        showGraphicsDriverBackends(view);

        final RadioGroup rgAppTheme = view.findViewById(R.id.RGAppTheme);
        final int oldAppThemeId = preferences.getInt("app_theme", APP_THEME_DARK) == APP_THEME_LIGHT ? R.id.RBLight : R.id.RBDark;
        rgAppTheme.check(oldAppThemeId);

        final CheckBox cbMoveCursorToTouchpoint = view.findViewById(R.id.CBMoveCursorToTouchpoint);
        cbMoveCursorToTouchpoint.setChecked(preferences.getBoolean("move_cursor_to_touchpoint", false));

        final CheckBox cbCapturePointerOnExternalMouse = view.findViewById(R.id.CBCapturePointerOnExternalMouse);
        cbCapturePointerOnExternalMouse.setChecked(preferences.getBoolean("capture_pointer_on_external_mouse", true));

        final CheckBox cbOpenAndroidBrowserFromWine = view.findViewById(R.id.CBOpenAndroidBrowserFromWine);
        cbOpenAndroidBrowserFromWine.setChecked(preferences.getBoolean("open_android_browser_from_wine", true));

        final CheckBox cbUseAndroidClipboardOnWine = view.findViewById(R.id.CBUseAndroidClipboardOnWine);
        cbUseAndroidClipboardOnWine.setChecked(preferences.getBoolean("use_android_clipboard_on_wine", false));

        final CheckBox cbEnableBackgroundWakelock = view.findViewById(R.id.CBEnableBackgroundWakelock);
        cbEnableBackgroundWakelock.setChecked(preferences.getBoolean("enable_background_wakelock", false));

        final CheckBox cbEnableBackgroundProtection = view.findViewById(R.id.CBEnableBackgroundProtection);
        cbEnableBackgroundProtection.setChecked(preferences.getBoolean("enable_background_protection", false));
        cbEnableBackgroundProtection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            cbEnableBackgroundWakelock.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked) cbEnableBackgroundWakelock.setChecked(false);

            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // We force a temporary notification channel so that the notification permission request window appears on APIs 33+
                    String tempId = "permission_trigger";
                    NotificationUtils.getInstance(getContext().getApplicationContext()).createNotificationChannel(context, tempId, "Permission Trigger", NotificationManager.IMPORTANCE_LOW);
//                    NotificationUtils.getInstance(getContext().getApplicationContext()).createNotificationChannel(); // Create the foreground notification channel.

                    // And delete the channel after 1 second.
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                        if (nm != null) nm.deleteNotificationChannel(tempId);
                    }, 1000);
                }
            }
        });
        cbEnableBackgroundWakelock.setVisibility(cbEnableBackgroundProtection.isChecked() ? View.VISIBLE : View.GONE);

        final CheckBox cbSaveMemOnRunFromSteam = view.findViewById(R.id.CBSaveMemOnRunFromSteam);
        cbSaveMemOnRunFromSteam.setChecked(preferences.getBoolean("save_mem_on_run_from_steam", true));

        final CheckBox cbThermalGuardEnabled = view.findViewById(R.id.CBThermalGuardEnabled);
        final CheckBox cbThermalWarningsEnabled = view.findViewById(R.id.CBThermalWarningsEnabled);
        final CheckBox cbThermalSuggestEco = view.findViewById(R.id.CBThermalSuggestEco);
        final CheckBox cbThermalAutoAdjust = view.findViewById(R.id.CBThermalAutoAdjust);
        final Spinner sThermalPolicy = view.findViewById(R.id.SThermalPerformancePolicy);
        cbThermalGuardEnabled.setChecked(preferences.getBoolean("thermal_guard_enabled", true));
        cbThermalWarningsEnabled.setChecked(preferences.getBoolean("thermal_warnings_enabled", true));
        cbThermalSuggestEco.setChecked(preferences.getBoolean("thermal_suggest_eco", true));
        cbThermalAutoAdjust.setChecked(preferences.getBoolean("thermal_auto_adjust", false));
        String thermalPolicy = preferences.getString("thermal_performance_policy", "balanced");
        sThermalPolicy.setSelection(thermalPolicy.equals("eco") ? 0 :
                thermalPolicy.equals("performance") ? 2 : thermalPolicy.equals("unlimited") ? 3 : 1);
        Runnable updateThermalSettingsAvailability = () -> {
            boolean enabled = cbThermalGuardEnabled.isChecked();
            cbThermalWarningsEnabled.setEnabled(enabled);
            cbThermalSuggestEco.setEnabled(enabled && cbThermalWarningsEnabled.isChecked());
            cbThermalAutoAdjust.setEnabled(enabled);
            sThermalPolicy.setEnabled(enabled);
        };
        cbThermalGuardEnabled.setOnCheckedChangeListener((button, checked) -> updateThermalSettingsAvailability.run());
        cbThermalWarningsEnabled.setOnCheckedChangeListener((button, checked) -> updateThermalSettingsAvailability.run());
        updateThermalSettingsAvailability.run();

        final CheckBox cbEnableWineDebug = view.findViewById(R.id.CBEnableWineDebug);
        cbEnableWineDebug.setChecked(preferences.getBoolean("enable_wine_debug", false));

        final ArrayList<String> wineDebugChannels = new ArrayList<>(Arrays.asList(preferences.getString("wine_debug_channels", DEFAULT_WINE_DEBUG_CHANNELS).split(",")));
        loadWineDebugChannels(view, wineDebugChannels);

        final Spinner sBox64Logs = view.findViewById(R.id.SBox64Logs);
        sBox64Logs.setSelection(preferences.getInt("box64_logs", 0));

        final CheckBox cbSaveLogsToFile = view.findViewById(R.id.CBSaveLogsToFile);
        cbSaveLogsToFile.setChecked(preferences.getBoolean("save_logs_to_file", false));

        final EditText etLogFile = view.findViewById(R.id.ETLogFile);
        final String defaultLogPath = LogView.getLogFile().getPath();
        etLogFile.setText(preferences.getString("log_file", defaultLogPath));
        etLogFile.setVisibility(cbSaveLogsToFile.isChecked() ? View.VISIBLE : View.GONE);
        cbSaveLogsToFile.setOnCheckedChangeListener((buttonView, isChecked) -> etLogFile.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        final SeekBar sbCursorSpeed = view.findViewById(R.id.SBCursorSpeed);
        sbCursorSpeed.setValue(preferences.getFloat("cursor_speed", 1.0f) * 100);

        final SeekBar sbCursorSize = view.findViewById(R.id.SBCursorSize);
        sbCursorSize.setValue(preferences.getFloat("cursor_scale", 1.0f) * 100);

        final ColorPickerView cpvCursorColor = view.findViewById(R.id.CPVCursorColor);
        cpvCursorColor.setPalette(0xffffff, 0x000000, 0x651fff, 0xffea00, 0xff9100, 0xf50057, 0x00b0ff, 0x1de9b6);
        cpvCursorColor.setColor(preferences.getInt("cursor_color", 0xffffff));

        final Spinner sGamepadModel = view.findViewById(R.id.SGamepadModel);
        loadGamepadModelSpinner(sGamepadModel);

        final Spinner sWineVersion = view.findViewById(R.id.SWineVersion);
        loadWineVersionSpinner(view, sWineVersion);

        final Spinner sLanguage = view.findViewById(R.id.SLanguage);
        sLanguage.setSelection(LocaleHelper.getLocaleIndex(context));
        final int oldLCIndex = sLanguage.getSelectedItemPosition();

        view.findViewById(R.id.BTReinstallSystemFiles).setOnClickListener((v) -> {
            ContentDialog.confirm(context, R.string.do_you_want_to_reinstall_system_files, () -> RootFSInstaller.install((MainActivity)getActivity()));
        });

        loadGamepadPlayerConfigs(view);

        view.findViewById(R.id.LLWineInstallation).setVisibility(View.VISIBLE);

        view.findViewById(R.id.BTConfirm).setOnClickListener((v) -> {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("soundfont", sSoundFont.getSelectedItem().toString());
            editor.putString("box64_version", StringUtils.parseIdentifier(sBox64Version.getSelectedItem()));
            editor.putString("box64_preset", Box64PresetManager.getSpinnerSelectedId(sBox64Preset));
            editor.putBoolean("move_cursor_to_touchpoint", cbMoveCursorToTouchpoint.isChecked());
            editor.putBoolean("capture_pointer_on_external_mouse", cbCapturePointerOnExternalMouse.isChecked());
            editor.putFloat("cursor_speed", sbCursorSpeed.getValue() / 100.0f);
            editor.putFloat("cursor_scale", sbCursorSize.getValue() / 100.0f);
            editor.putInt("cursor_color", cpvCursorColor.getColor());
            editor.putBoolean("enable_wine_debug", cbEnableWineDebug.isChecked());
            editor.putInt("box64_logs", sBox64Logs.getSelectedItemPosition());
            editor.putBoolean("save_logs_to_file", cbSaveLogsToFile.isChecked());
            editor.putBoolean("open_android_browser_from_wine", cbOpenAndroidBrowserFromWine.isChecked());
            editor.putBoolean("use_android_clipboard_on_wine", cbUseAndroidClipboardOnWine.isChecked());
            editor.putBoolean("enable_background_protection", cbEnableBackgroundProtection.isChecked());
            editor.putBoolean("enable_background_wakelock", cbEnableBackgroundWakelock.isChecked());
            editor.putBoolean("save_mem_on_run_from_steam", cbSaveMemOnRunFromSteam.isChecked());
            editor.putBoolean("thermal_guard_enabled", cbThermalGuardEnabled.isChecked());
            editor.putBoolean("thermal_warnings_enabled", cbThermalWarningsEnabled.isChecked());
            editor.putBoolean("thermal_suggest_eco", cbThermalSuggestEco.isChecked());
            editor.putBoolean("thermal_auto_adjust", cbThermalAutoAdjust.isChecked());
            String[] thermalPolicyIds = {"eco", "balanced", "performance", "unlimited"};
            editor.putString("thermal_performance_policy", thermalPolicyIds[sThermalPolicy.getSelectedItemPosition()]);
            putGamepadPlayerConfigs(view, editor);

            GamepadHandler.GamepadModel gamepadModel = (GamepadHandler.GamepadModel)sGamepadModel.getAdapter().getItem(sGamepadModel.getSelectedItemPosition());
            if (gamepadModel.vendorId > 1 && gamepadModel.productId > 1) {
                editor.putString("gamepad_model", gamepadModel.identifier());
            }
            else editor.remove("gamepad_model");

            int newAppThemeId = rgAppTheme.getCheckedRadioButtonId();
            editor.putInt("app_theme", newAppThemeId == R.id.RBLight ? APP_THEME_LIGHT : APP_THEME_DARK);

            int newLCIndex = sLanguage.getSelectedItemPosition();
            editor.putInt("lc_index", newLCIndex);
            boolean restartApp = oldLCIndex != newLCIndex || oldAppThemeId != newAppThemeId;

            int midiInputDevicePosition = sMIDIInputDevice.getSelectedItemPosition();
            editor.putString("midi_input_device", midiInputDevicePosition == 0 ? "none" :
                                                 (midiInputDevicePosition == 1 ? "auto" : sMIDIInputDevice.getSelectedItem().toString()));

            String logPath = etLogFile.getText().toString().trim();
            if (!logPath.equals(defaultLogPath) && !logPath.isEmpty()) {
                editor.putString("log_file", logPath);
            }
            else editor.remove("log_file");

            if (!wineDebugChannels.isEmpty()) {
                editor.putString("wine_debug_channels", String.join(",", wineDebugChannels));
            }
            else if (preferences.contains("wine_debug_channels")) editor.remove("wine_debug_channels");

            if (editor.commit()) {
                if (!restartApp) {
                    NavigationView navigationView = getActivity().findViewById(R.id.NavigationView);
                    navigationView.setCheckedItem(R.id.menu_item_containers);
                    FragmentManager fragmentManager = getParentFragmentManager();
                    fragmentManager.beginTransaction()
                        .replace(R.id.FLFragmentContainer, new ContainersFragment())
                        .commit();
                }
                else AppUtils.restartActivity(getActivity());
            }
        });

        String section = getArguments() != null ? getArguments().getString(SECTION_ARGUMENT) : null;
        if (SECTION_RUNTIME_MANAGER.equals(section)) {
            return createRuntimeManagerView(inflater, view);
        }

        hideManagedSection(view.findViewById(R.id.GraphicsDriversManagerSection));
        hideManagedSection(view.findViewById(R.id.DXVKManagerSection));
        hideManagedSection(view.findViewById(R.id.GraphicsWrapperManagerSection));
        view.findViewById(R.id.LLWineInstallation).setVisibility(View.GONE);
        int targetId = SECTION_BOX64.equals(section) ? R.id.Box64SettingsSection : View.NO_ID;
        if (targetId != View.NO_ID) {
            View target = view.findViewById(targetId);
            View scrollView = view.findViewById(R.id.SettingsScrollView);
            view.post(() -> {
                int targetTop = 0;
                View current = target;
                while (current != scrollView && current.getParent() instanceof View) {
                    targetTop += current.getTop();
                    current = (View)current.getParent();
                }
                ((android.widget.ScrollView)scrollView).smoothScrollTo(0, targetTop);
            });
        }
        return view;
    }

    private void hideManagedSection(View section) {
        section.setVisibility(View.GONE);
        ViewGroup parent = (ViewGroup)section.getParent();
        int index = parent.indexOfChild(section);
        if (index > 0 && parent.getChildAt(index - 1) instanceof TextView) {
            parent.getChildAt(index - 1).setVisibility(View.GONE);
        }
    }

    private View createRuntimeManagerView(LayoutInflater inflater, View settingsView) {
        View runtimeView = inflater.inflate(R.layout.runtime_manager_fragment, null, false);
        LinearLayout content = runtimeView.findViewById(R.id.LLRuntimeCategoryContent);
        View wine = settingsView.findViewById(R.id.LLWineInstallation);
        View dxvk = settingsView.findViewById(R.id.DXVKManagerSection);
        View drivers = settingsView.findViewById(R.id.GraphicsDriversManagerSection);
        View wrappersHeading = settingsView.findViewById(R.id.TVGraphicsWrapperManagerHeading);
        View wrappers = settingsView.findViewById(R.id.GraphicsWrapperManagerSection);

        ((ViewGroup)wine.getParent()).removeView(wine);
        ((ViewGroup)dxvk.getParent()).removeView(dxvk);
        ((ViewGroup)drivers.getParent()).removeView(drivers);
        ((ViewGroup)wrappersHeading.getParent()).removeView(wrappersHeading);
        ((ViewGroup)wrappers.getParent()).removeView(wrappers);
        content.addView(wine);
        content.addView(dxvk);
        content.addView(drivers);
        content.addView(wrappersHeading);
        content.addView(wrappers);

        Spinner categorySpinner = runtimeView.findViewById(R.id.SRuntimeCategory);
        TextView description = runtimeView.findViewById(R.id.TVRuntimeCategoryDescription);
        int[] categories = {R.string.runtime_manager_wine_description,
                R.string.runtime_manager_dxvk_description,
                R.string.runtime_manager_drivers_description,
                R.string.runtime_manager_wrappers_description};
        View[] categoryViews = {wine, dxvk, drivers, wrappersHeading, wrappers};
        categorySpinner.setSelection(0, false);
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                description.setText(categories[position]);
                for (int i = 0; i < categoryViews.length; i++) {
                    boolean visible = i == position || position == 3 && i == 4;
                    categoryViews[i].setVisibility(visible ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        description.setText(categories[0]);
        wine.setVisibility(View.VISIBLE);
        dxvk.setVisibility(View.GONE);
        drivers.setVisibility(View.GONE);
        wrappersHeading.setVisibility(View.GONE);
        wrappers.setVisibility(View.GONE);
        return runtimeView;
    }

    private void loadGamepadModelSpinner(Spinner sGamepadModel) {
        final Context context = getContext();
        ArrayList<GamepadHandler.GamepadModel> gamepadModels = GamepadHandler.loadGamepadModels(context);

        String selectedModel = preferences.getString("gamepad_model", "");
        int selectedPosition = 0;
        for (int i = 0; i < gamepadModels.size(); i++) {
            if (gamepadModels.get(i).identifier().equals(selectedModel)) {
                selectedPosition = i;
                break;
            }
        }

        sGamepadModel.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, gamepadModels));
        sGamepadModel.setSelection(selectedPosition);
    }

    private void loadBox64PresetSpinner(View view, final Spinner sBox64Preset) {
        final Context context = getContext();

        Runnable updateSpinner = () -> {
            Box64PresetManager.loadSpinner(sBox64Preset, preferences.getString("box64_preset", Box64Preset.DEFAULT));
        };

        updateSpinner.run();

        view.findViewById(R.id.BTAddBox64Preset).setOnClickListener((v) -> {
            Box64EditPresetDialog dialog = new Box64EditPresetDialog(context, null);
            dialog.setOnConfirmCallback(updateSpinner);
            dialog.show();
        });
        view.findViewById(R.id.BTEditBox64Preset).setOnClickListener((v) -> {
            Box64EditPresetDialog dialog = new Box64EditPresetDialog(context, Box64PresetManager.getSpinnerSelectedId(sBox64Preset));
            dialog.setOnConfirmCallback(updateSpinner);
            dialog.show();
        });
        view.findViewById(R.id.BTDuplicateBox64Preset).setOnClickListener((v) -> {
            Box64PresetManager.duplicatePreset(context, Box64PresetManager.getSpinnerSelectedId(sBox64Preset));
            updateSpinner.run();
            sBox64Preset.setSelection(sBox64Preset.getCount()-1);
        });
        view.findViewById(R.id.BTRemoveBox64Preset).setOnClickListener((v) -> {
            final String presetId = Box64PresetManager.getSpinnerSelectedId(sBox64Preset);
            if (!presetId.startsWith(Box64Preset.CUSTOM)) {
                AppUtils.showToast(context, R.string.you_cannot_remove_this_preset);
                return;
            }
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_preset, () -> {
                Box64PresetManager.removePreset(context, presetId);
                updateSpinner.run();
            });
        });
    }

    private void showDXVKManagerVersions(View view) {
        LinearLayout rows = view.findViewById(R.id.LLDXVKManagerVersions);
        rows.removeAllViews();

        ArrayList<String> versions = GeneralComponents.getAvailableComponentNames(GeneralComponents.Type.DXVK, getContext());
        for (String version : versions) {
            String source = getString(GeneralComponents.isBuiltinComponent(GeneralComponents.Type.DXVK, version) ?
                    R.string.dxvk_package_bundled : R.string.dxvk_package_imported);
            int selectedContainers = getDXVKContainerUseCount(version);
            String text;
            if (version.equals(DefaultVersion.MAJOR_DXVK)) {
                text = getString(R.string.dxvk_manager_row_default, version, source, selectedContainers);
            }
            else if (version.equals(DefaultVersion.MINOR_DXVK)) {
                text = getString(R.string.dxvk_manager_row_fallback, version, source, selectedContainers);
            }
            else {
                text = getString(R.string.dxvk_manager_row_installed, version, source, selectedContainers);
            }

            TextView row = new TextView(getContext());
            row.setText(text);
            int horizontalPadding = (int)UnitUtils.dpToPx(12);
            int verticalPadding = (int)UnitUtils.dpToPx(10);
            row.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
            row.setBackgroundResource(R.drawable.vanta_card_background);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = (int)UnitUtils.dpToPx(6);
            rows.addView(row, params);
        }
    }

    private void showTurnipManagerVersions(View view) {
        LinearLayout rows = view.findViewById(R.id.LLTurnipManagerVersions);
        rows.removeAllViews();

        String defaultDriver = GraphicsDrivers.parseIdentifiers(GraphicsDrivers.getDefaultDriver(getContext()))[0];
        ArrayList<String> versions = GeneralComponents.getAvailableComponentNames(GeneralComponents.Type.TURNIP, getContext());
        for (String version : versions) {
            String source = getString(GeneralComponents.isBuiltinComponent(GeneralComponents.Type.TURNIP, version) ?
                    R.string.component_package_bundled : R.string.component_package_imported);
            int selectedContainers = getTurnipContainerUseCount(version);
            int status = defaultDriver.equals(GraphicsDrivers.TURNIP) && version.equals(DefaultVersion.TURNIP) ?
                    R.string.graphics_driver_version_default :
                    R.string.graphics_driver_version_available;
            TextView row = new TextView(getContext());
            row.setText(getString(R.string.graphics_driver_turnip_row, version, source,
                    getString(status), selectedContainers));
            row.setPadding((int)UnitUtils.dpToPx(12), (int)UnitUtils.dpToPx(10),
                    (int)UnitUtils.dpToPx(12), (int)UnitUtils.dpToPx(10));
            row.setBackgroundResource(R.drawable.vanta_card_background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = (int)UnitUtils.dpToPx(6);
            rows.addView(row, params);
        }
    }

    private void showGraphicsDriverBackends(View view) {
        LinearLayout rows = view.findViewById(R.id.LLGraphicsDriverBackends);
        rows.removeAllViews();

        Context context = getContext();
        TextView gpuSummary = view.findViewById(R.id.TVGpuCapabilitySummary);
        GpuCapabilityProfile[] profile = new GpuCapabilityProfile[1];
        view.findViewById(R.id.BTExportGpuDiagnostics).setOnClickListener(button -> {
            if (profile[0] == null || getActivity() == null) return;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.gpu_diagnostics_title));
            shareIntent.putExtra(Intent.EXTRA_TEXT, profile[0].toDiagnosticText());
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_gpu_diagnostics)));
        });
        Thread probeThread = new Thread(() -> {
            GpuCapabilityProfile detected = MaliCapabilityProbe.probe(context);
            gpuSummary.post(() -> {
                if (!isAdded()) return;
                profile[0] = detected;
                gpuSummary.setText(detected.toDiagnosticText());
            });
        }, "vanta-gpu-capability-probe");
        probeThread.setDaemon(true);
        probeThread.start();

        showGraphicsRuntimePackages(view);

        String[] defaultDrivers = GraphicsDrivers.parseIdentifiers(GraphicsDrivers.getDefaultDriver(context));
        String[] drivers = {
                GraphicsDrivers.TURNIP,
                GraphicsDrivers.VORTEK,
                GraphicsDrivers.ZINK,
                GraphicsDrivers.VIRGL,
                GraphicsDrivers.GLADIO
        };
        for (String driver : drivers) {
            boolean isVulkan = GraphicsDrivers.isVulkanDriver(driver);
            int index = isVulkan ? 0 : 1;
            String status = driver.equals(defaultDrivers[index]) ?
                    getString(R.string.graphics_driver_default_for_device) :
                    getString(R.string.graphics_driver_available);
            String api = getString(isVulkan ? R.string.vulkan : R.string.opengl);
            TextView row = new TextView(context);
            row.setText(getString(R.string.graphics_driver_backend_row,
                    GraphicsDrivers.getName(driver), DefaultVersion.valueOf(driver), api, status));
            row.setTextColor(AppUtils.getThemeColor(context, R.attr.colorPrimaryText));
            row.setTextSize(13);
            row.setPadding((int)UnitUtils.dpToPx(12), (int)UnitUtils.dpToPx(10),
                    (int)UnitUtils.dpToPx(12), (int)UnitUtils.dpToPx(10));
            row.setBackgroundResource(R.drawable.vanta_card_background);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = (int)UnitUtils.dpToPx(6);
            rows.addView(row, params);
        }
    }

    private void showGraphicsRuntimePackages(View view) {
        LinearLayout drivers = view.findViewById(R.id.LLGraphicsRuntimePackages);
        LinearLayout wrappers = view.findViewById(R.id.LLGraphicsWrapperPackages);
        drivers.removeAllViews();
        wrappers.removeAllViews();
        long refreshId = graphicsPackageRefreshId.incrementAndGet();

        view.findViewById(R.id.BTImportGraphicsDriver).setOnClickListener(button ->
                chooseGraphicsRuntimePackage(GraphicsRuntimePackage.Kind.VULKAN_DRIVER, view));
        view.findViewById(R.id.BTImportGraphicsWrapper).setOnClickListener(button ->
                chooseGraphicsRuntimePackage(GraphicsRuntimePackage.Kind.VULKAN_WRAPPER, view));

        Context appContext = requireContext().getApplicationContext();
        Thread scanThread = new Thread(() -> {
            List<GraphicsRuntimePackage> packages = GraphicsRuntimePackageStore.getInstalledPackages(appContext);
            drivers.post(() -> {
                if (!isAdded() || graphicsPackageRefreshId.get() != refreshId) return;
                drivers.removeAllViews();
                wrappers.removeAllViews();
                for (GraphicsRuntimePackage runtimePackage : packages) {
                    LinearLayout target = runtimePackage.kind == GraphicsRuntimePackage.Kind.VULKAN_DRIVER ? drivers : wrappers;
                    addGraphicsPackageRow(target, runtimePackage);
                }
                if (drivers.getChildCount() == 0) addEmptyPackageMessage(drivers, R.string.no_graphics_drivers_installed);
                if (wrappers.getChildCount() == 0) addEmptyPackageMessage(wrappers, R.string.no_graphics_wrappers_installed);
            });
        }, "vanta-graphics-package-scan");
        scanThread.setDaemon(true);
        scanThread.start();
    }

    private void addEmptyPackageMessage(LinearLayout target, int message) {
        TextView text = new TextView(requireContext());
        text.setText(message);
        text.setTextColor(AppUtils.getThemeColor(requireContext(), R.attr.colorSecondaryText));
        text.setTextSize(13);
        int padding = (int)UnitUtils.dpToPx(10);
        text.setPadding(padding, padding, padding, padding);
        target.addView(text);
    }

    private void addGraphicsPackageRow(LinearLayout target, GraphicsRuntimePackage runtimePackage) {
        Context context = requireContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int)UnitUtils.dpToPx(12), (int)UnitUtils.dpToPx(10),
                (int)UnitUtils.dpToPx(12), (int)UnitUtils.dpToPx(8));
        card.setBackgroundResource(R.drawable.vanta_card_background);

        TextView details = new TextView(context);
        String status = runtimePackage.experimental ? getString(R.string.graphics_package_experimental) :
                getString(R.string.graphics_package_manual);
        details.setText(getString(R.string.graphics_package_row, runtimePackage.name, runtimePackage.version,
                runtimePackage.kind.name().replace('_', ' '), runtimePackage.upstream, runtimePackage.license,
                runtimePackage.abi, runtimePackage.sha256, status, runtimePackage.revision,
                runtimePackage.loader, runtimePackage.gpuFamilies, runtimePackage.gpuModels,
                runtimePackage.scheduler, runtimePackage.kernelRequirement,
                runtimePackage.kbaseRequirement, runtimePackage.vulkanVersion,
                runtimePackage.limitations));
        details.setTextColor(AppUtils.getThemeColor(context, R.attr.colorPrimaryText));
        details.setTextSize(12);
        card.addView(details);

        androidx.appcompat.widget.AppCompatButton remove = new androidx.appcompat.widget.AppCompatButton(context);
        remove.setText(R.string.graphics_package_remove);
        remove.setOnClickListener(button -> {
            if (GraphicsRuntimePackageStore.isInUse(context, runtimePackage)) {
                AppUtils.showToast(context, R.string.component_in_use);
                return;
            }
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_component_version, () -> {
                if (GraphicsRuntimePackageStore.remove(context, runtimePackage)) {
                    showGraphicsRuntimePackages(requireView());
                }
                else AppUtils.showToast(context, R.string.component_removal_failed);
            });
        });
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (int)UnitUtils.dpToPx(40));
        removeParams.topMargin = (int)UnitUtils.dpToPx(4);
        card.addView(remove, removeParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = (int)UnitUtils.dpToPx(6);
        target.addView(card, cardParams);
    }

    private void chooseGraphicsRuntimePackage(GraphicsRuntimePackage.Kind kind, View refreshView) {
        Activity activity = getActivity();
        if (!(activity instanceof MainActivity)) return;
        MainActivity mainActivity = (MainActivity)activity;
        mainActivity.setOpenFileCallback(uri -> {
            if (uri == null || !isAdded()) return;
            preloaderDialog.show(R.string.validating_component_package);
            File archive = new File(activity.getCacheDir(), "graphics-runtime-import-" + java.util.UUID.randomUUID() + ".zip");
            Thread importThread = new Thread(() -> {
                GraphicsRuntimePackage installed = null;
                Exception failure = null;
                try (InputStream input = activity.getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(archive)) {
                    if (input == null) throw new IOException("Unable to open the selected package.");
                    byte[] buffer = new byte[16 * 1024];
                    long total = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > 512L * 1024 * 1024) throw new IOException("Graphics package archive exceeds 512 MiB.");
                        output.write(buffer, 0, count);
                    }
                    output.getFD().sync();
                    installed = GraphicsRuntimePackageStore.install(activity, archive, kind);
                }
                catch (Exception e) {
                    failure = e;
                }
                finally {
                    FileUtils.delete(archive);
                }

                GraphicsRuntimePackage result = installed;
                Exception error = failure;
                activity.runOnUiThread(() -> {
                    preloaderDialog.close();
                    if (!isAdded() || activity.isFinishing()) return;
                    if (error != null) {
                        ContentDialog dialog = new ContentDialog(activity);
                        dialog.setTitle(R.string.graphics_package_invalid);
                        dialog.setMessage(error.getMessage());
                        dialog.show();
                    }
                    else {
                        showGraphicsRuntimePackages(refreshView);
                        AppUtils.showToast(activity, R.string.graphics_package_installed);
                    }
                });
            }, "vanta-graphics-package-import");
            importThread.setDaemon(true);
            importThread.start();
        });
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        mainActivity.startActivityForResult(intent, MainActivity.OPEN_FILE_REQUEST_CODE);
    }

    private int getTurnipContainerUseCount(String version) {
        int count = 0;
        for (Container container : new ContainerManager(getContext()).getContainers()) {
            String[] drivers = GraphicsDrivers.parseIdentifiers(container.getGraphicsDriver());
            if (!GraphicsDrivers.TURNIP.equals(drivers[0])) continue;
            KeyValueSet[] config = GraphicsDrivers.parseConfigs(container.getGraphicsDriver(), container.getGraphicsDriverConfig());
            if (version.equals(config[0].get("version", DefaultVersion.TURNIP))) count++;
        }
        return count;
    }

    private int getDXVKContainerUseCount(String version) {
        int count = 0;
        for (Container container : new ContainerManager(getContext()).getContainers()) {
            if (!DXWrappers.DXVK.equals(container.getDXWrapper())) continue;
            KeyValueSet[] config = DXWrappers.parseConfigs(container.getDXWrapper(), container.getDXWrapperConfig());
            String[] drivers = GraphicsDrivers.parseIdentifiers(container.getGraphicsDriver());
            String selectedVersion = config[0].get("version", DefaultVersion.DXVK(drivers[0]));
            if (version.equals(selectedVersion)) count++;
        }
        return count;
    }

    private void removeInstalledWine(WineInfo wineInfo, Runnable onSuccess) {
        final Activity activity = getActivity();
        ContainerManager manager = new ContainerManager(activity);

        ArrayList<Container> containers = manager.getContainers();
        for (Container container : containers) {
            if (container.getWineVersion().equals(wineInfo.identifier())) {
                AppUtils.showToast(activity, R.string.unable_to_remove_this_wine_version);
                return;
            }
        }

        File installedWineDir = RootFS.find(activity).getInstalledWineDir();
        File wineDir = new File(wineInfo.path);
        File containerPatternFile = new File(installedWineDir, "container-pattern-"+wineInfo.fullVersion()+".tzst");

        if (!wineDir.isDirectory() || !containerPatternFile.isFile()) {
            AppUtils.showToast(activity, R.string.unable_to_remove_this_wine_version);
            return;
        }

        preloaderDialog.show(R.string.removing_wine);
        Executors.newSingleThreadExecutor().execute(() -> {
            File wineBackup = new File(installedWineDir, ".wine-remove-"+System.nanoTime());
            File patternBackup = new File(installedWineDir, ".pattern-remove-"+System.nanoTime());
            boolean movedWine = wineDir.renameTo(wineBackup);
            boolean movedPattern = movedWine && containerPatternFile.renameTo(patternBackup);
            boolean removed = false;
            if (!movedPattern) {
                if (movedWine) wineBackup.renameTo(wineDir);
                activity.runOnUiThread(() -> AppUtils.showToast(activity, R.string.unable_to_remove_this_wine_version));
            }
            else {
                boolean wineRemoved = FileUtils.delete(wineBackup);
                boolean patternRemoved = FileUtils.delete(patternBackup);
                removed = wineRemoved && patternRemoved;
                if (!removed) activity.runOnUiThread(() -> AppUtils.showToast(activity, R.string.unable_to_remove_this_wine_version));
            }
            preloaderDialog.closeOnUiThread();
            if (removed && onSuccess != null) activity.runOnUiThread(onSuccess);
        });
    }

    private void loadWineVersionSpinner(final View view, final Spinner sWineVersion) {
        Context context = getContext();
        final ArrayList<WineInfo> wineInfos = WineInstaller.getInstalledWineInfos(context);
        sWineVersion.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, wineInfos));
        TextView wineInfoView = view.findViewById(R.id.TVWineManagerInfo);
        Runnable updateInfo = () -> {
            int selected = sWineVersion.getSelectedItemPosition();
            if (selected < 0 || selected >= wineInfos.size()) return;
            WineInfo wineInfo = wineInfos.get(selected);
            int selectedContainers = 0;
            for (Container container : new ContainerManager(context).getContainers()) {
                if (container.getWineVersion().equals(wineInfo.identifier())) selectedContainers++;
            }
            wineInfoView.setText(wineInfo == WineInfo.MAIN_WINE_INFO ?
                    context.getString(R.string.wine_builtin_default_details, wineInfo.fullVersion(), selectedContainers) :
                    context.getString(R.string.wine_installed_details, wineInfo.fullVersion(), selectedContainers));
        };
        sWineVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View child, int position, long id) {
                updateInfo.run();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        updateInfo.run();

        view.findViewById(R.id.BTInstallWine).setOnClickListener((v) -> selectWineFileForInstall());
        view.findViewById(R.id.BTRemoveWine).setOnClickListener((v) -> {
            WineInfo wineInfo = wineInfos.get(sWineVersion.getSelectedItemPosition());
            if (wineInfo != WineInfo.MAIN_WINE_INFO) {
                ContentDialog.confirm(getContext(), R.string.do_you_want_to_remove_this_wine_version, () -> {
                    removeInstalledWine(wineInfo, () -> loadWineVersionSpinner(view, sWineVersion));
                });
            }
        });
    }

    private void selectWineFileForInstall() {
        final Context context = getContext();
        selectWineFileCallback = (uri) -> {
            preloaderDialog.show(R.string.preparing_installation);
            WineInstaller.extractWineFileForInstallAsync(context, uri, (wineDir) -> {
                if (wineDir != null) {
                    WineInstaller.findWineVersionAsync(context, wineDir, (wineInfo) -> {
                        preloaderDialog.closeOnUiThread();
                        if (wineInfo == null) {
                            AppUtils.showToast(context, R.string.unable_to_install_wine);
                            return;
                        }

                        getActivity().runOnUiThread(() -> showWineInstallDialog(wineInfo));
                    });
                }
                else {
                    AppUtils.showToast(context, R.string.unable_to_install_wine);
                    preloaderDialog.closeOnUiThread();
                }
            });
        };

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_FILE_REQUEST_CODE);
    }

    private void installWine(final WineInfo wineInfo) {
        Context context = getContext();
        File installedWineDir = RootFS.find(context).getInstalledWineDir();

        File wineDir = new File(installedWineDir, wineInfo.identifier());
        if (wineDir.isDirectory()) {
            AppUtils.showToast(context, R.string.unable_to_install_wine);
            return;
        }

        Intent intent = new Intent(context, XServerDisplayActivity.class);
        intent.putExtra("generate_wineprefix", true);
        intent.putExtra("wine_info", wineInfo);
        context.startActivity(intent);
    }

    private void showWineInstallDialog(final WineInfo wineInfo) {
        Context context = getContext();
        ContentDialog dialog = new ContentDialog(context, R.layout.wine_install_dialog);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setTitle(R.string.install_wine);
        dialog.setIcon(R.drawable.icon_wine);

        EditText etVersion = dialog.findViewById(R.id.ETVersion);
        etVersion.setText("Wine "+wineInfo.version+(wineInfo.subversion != null ? " ("+wineInfo.subversion+")" : ""));

        final EditText etSize = dialog.findViewById(R.id.ETSize);
        final AtomicLong totalSizeRef = new AtomicLong();
        FileUtils.getSizeAsync(new File(wineInfo.path), (size) -> {
            totalSizeRef.addAndGet(size);
            etSize.post(() -> etSize.setText(StringUtils.formatBytes(totalSizeRef.get())));
        });

        dialog.setOnConfirmCallback(() -> installWine(wineInfo));
        dialog.show();
    }

    private void loadWineDebugChannels(final View view, final ArrayList<String> debugChannels) {
        final Context context = getContext();
        LinearLayout container = view.findViewById(R.id.LLWineDebugChannels);
        container.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(context);
        View itemView = inflater.inflate(R.layout.wine_debug_channel_list_item, container, false);
        itemView.findViewById(R.id.TextView).setVisibility(View.GONE);
        itemView.findViewById(R.id.BTRemove).setVisibility(View.GONE);

        View addButton = itemView.findViewById(R.id.BTAdd);
        addButton.setVisibility(View.VISIBLE);
        addButton.setOnClickListener((v) -> {
            JSONArray jsonArray = null;
            try {
                jsonArray = new JSONArray(FileUtils.readString(context, "wine_debug_channels.json"));
            }
            catch (JSONException e) {}

            final String[] items = ArrayUtils.toStringArray(jsonArray);
            ContentDialog.showSelectionList(context, R.string.wine_debug_channel, items, true, (selectedPositions) -> {
                for (int selectedPosition : selectedPositions) if (!debugChannels.contains(items[selectedPosition])) debugChannels.add(items[selectedPosition]);
                loadWineDebugChannels(view, debugChannels);
            });
        });

        View resetButton = itemView.findViewById(R.id.BTReset);
        resetButton.setVisibility(View.VISIBLE);
        resetButton.setOnClickListener((v) -> {
            debugChannels.clear();
            debugChannels.addAll(Arrays.asList(DEFAULT_WINE_DEBUG_CHANNELS.split(",")));
            loadWineDebugChannels(view, debugChannels);
        });
        container.addView(itemView);

        for (int i = 0; i < debugChannels.size(); i++) {
            itemView = inflater.inflate(R.layout.wine_debug_channel_list_item, container, false);
            TextView textView = itemView.findViewById(R.id.TextView);
            textView.setText(debugChannels.get(i));
            final int index = i;
            itemView.findViewById(R.id.BTRemove).setOnClickListener((v) -> {
                debugChannels.remove(index);
                loadWineDebugChannels(view, debugChannels);
            });
            container.addView(itemView);
        }
    }

    public static void resetPreferenceVersions(AppCompatActivity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("box64_version", DefaultVersion.BOX64);
        editor.remove("current_box64_version");
        editor.remove("current_graphics_driver");
        editor.apply();
    }

    private void loadMIDIInputDeviceSpinner(final Spinner sMIDIInputDevice, final String selectedValue) {
        Context context = getContext();
        MidiManager mm = (MidiManager)context.getSystemService(Context.MIDI_SERVICE);
        MidiDeviceInfo[] infos = mm.getDevices();

        if (!midiDeviceCallbackRegistered) {
            midiDeviceCallbackRegistered = true;
            mm.registerDeviceCallback(new MidiManager.DeviceCallback() {
                @Override
                public void onDeviceAdded(MidiDeviceInfo device) {
                    loadMIDIInputDeviceSpinner(sMIDIInputDevice, selectedValue);
                }

                @Override
                public void onDeviceRemoved(MidiDeviceInfo device) {
                    loadMIDIInputDeviceSpinner(sMIDIInputDevice, selectedValue);
                }
            }, new Handler(Looper.getMainLooper()));
        }

        ArrayList<String> items = new ArrayList<>();
        items.add(context.getString(R.string.none));
        items.add(context.getString(R.string.auto));

        for (MidiDeviceInfo info : infos) {
            if (info.getOutputPortCount() > 0) {
                Bundle properties = info.getProperties();
                items.add(properties.getString(MidiDeviceInfo.PROPERTY_NAME));
            }
        }

        sMIDIInputDevice.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, items));

        if (selectedValue.equals("none")) {
            sMIDIInputDevice.setSelection(0, false);
        }
        else if (selectedValue.equals("auto") || !AppUtils.setSpinnerSelectionFromValue(sMIDIInputDevice, selectedValue)) {
            sMIDIInputDevice.setSelection(1, false);
        }
    }

    private void loadGamepadPlayerConfigs(View view) {
        LinearLayout container = view.findViewById(R.id.LLGamepadPlayer);
        view.findViewById(R.id.BTResetGamepadPlayerConfigs).setOnClickListener((v) -> {
            ContentDialog.confirm(v.getContext(), R.string.do_you_want_to_reset_configurations, () -> {
                for (int i = 0; i < container.getChildCount(); i++) container.getChildAt(i).setTag("");
            });
        });

        for (int i = 0; i < container.getChildCount(); i++) {
            final View child = container.getChildAt(i);
            child.setTag(preferences.getString("gamepad_player"+i, ""));
            final byte slot = (byte)i;
            child.setOnClickListener((v) -> (new GamepadPlayerConfigDialog(child, slot)).show());
        }
    }

    private void putGamepadPlayerConfigs(View view, SharedPreferences.Editor editor) {
        LinearLayout container = view.findViewById(R.id.LLGamepadPlayer);
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            String config = child.getTag().toString();
            String key = "gamepad_player"+i;
            if (!config.isEmpty()) {
                editor.putString(key, child.getTag().toString());
            }
            else editor.remove(key);
        }
    }
}
