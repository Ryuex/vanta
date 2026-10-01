# Compatibility

This page describes the compatibility surface of Vanta as it exists in the repository today, and
how to report results so this document can grow.

> **Status:** Vanta has **not** been runtime-tested yet. No game compatibility results are claimed
> here. The tables below describe what the build *supports configuring*, not what has been verified
> on device. Verified results will be added as reports come in
> (see [Reporting compatibility](#reporting-compatibility)).

## Graphics drivers (Vulkan / OpenGL)

Selectable per container in the container details screen (`GraphicsDriverPicker`, with separate
Vulkan and OpenGL selections):

| Driver | Bundled version | API | Notes |
|---|---|---|---|
| Turnip (Mesa) | 26.2.0 | Vulkan | Adreno-focused Vulkan driver, loaded rootlessly via `libadrenotools` when a custom driver is used |
| Zink (Mesa) | 22.2.5 | OpenGL → Vulkan | Uses the `GALLIUM_DRIVER=zink` path |
| VirGL (Mesa) | 23.1.9 | OpenGL | Virtualized OpenGL through `libvirglrenderer` |
| Vortek | 2.1 | OpenGL/Vulkan path | Renderer component (`libvortekrenderer`), `/tmp/.vortek/V0` |
| Gladio | 1.1 | OpenGL | Renderer component used by the GLX extension (`libgladiorenderer`) |

Driver configuration dialogs exist for Turnip, Vortek and VirGL (`TurnipConfigDialog`,
`VirGLConfigDialog`, `VortekConfigDialog`); Zink and Gladio are selectable without an extra
configuration dialog.

## DirectX wrappers

Selected in the container details screen (`DXWrappers`, `DXWrapperPicker`):

| Wrapper | Bundled version(s) | Translates | Exposed in the UI |
|---|---|---|---|
| DXVK | 1.10.3 and 2.4.1 | Direct3D 8/9/10/11 → Vulkan | Yes — Direct3D selector + config dialog |
| WineD3D | ships with Wine 10.10 | Direct3D → OpenGL fallback | Yes — Direct3D selector + config dialog |
| VKD3D | 2.14.1 | Direct3D 12 → Vulkan | Yes — "DirectX 12" selector + config dialog |
| CNC DDraw | 6.6 | DirectDraw for classic games | Yes — DDraw wrapper option |
| D7VK | 1.11 | Direct3D 7 → Vulkan | Bundled and handled by the runtime; not offered in the picker |
| D8VK | 1.0 | Direct3D 8 → Vulkan | Bundled and handled by the runtime; not offered in the picker |

DXVK options exposed by the UI: version selection, framerate cap, max device memory, custom GPU
device, DDraw wrapper and `DXVK_*` environment variables (`DXVK_HUD`, `DXVK_LOG_LEVEL`,
`DXVK_ASYNC`).

Version selection logic: DXVK `2.4.1` is the default when the Vulkan driver is Turnip or the device
exposes Vulkan 1.3 or newer; otherwise `1.10.3` is used
(`DefaultVersion.DXVK(...)` in `app/src/main/java/io/vanta/app/core/DefaultVersion.java`).

## CPU translation

| Component | Bundled version | Notes |
|---|---|---|
| Box64 | 0.4.4 | x86_64 → ARM64; used for every guest program launch |
| Box86 | — | Credited upstream, **not bundled** in this repository |
| FEX | — | **Not present**; listed as an experimental idea in the roadmap |

Box64 presets available today (`Box64PresetManager`): **Stability**, **Conservative**,
**Intermediate**, **Performance**, plus user-created custom presets with editable
`BOX64_DYNAREC_*` and related environment variables.

## Wine

| Item | Value |
|---|---|
| Main Wine version | **10.10** (`WineInfo.MAIN_WINE_VERSION`) |
| Additional versions | Installable from the settings screen (`WineInstaller`), removable from the same screen |
| Wineprefix handling | RootFS-based, versioned through `RootFSInstaller.UPDATE_WINEPREFIX_VERSION` |
| Debug channels | Selectable Wine debug channels (`wine_debug_channels.json`, `enable_wine_debug`) |

Bundled Windows components (`assets/wincomponents/`): Direct3D, DirectMusic, DirectPlay,
DirectShow, DirectSound, XAudio, WMV decoder and VC runtimes 2005/2010.

## Audio and input

- Audio backends: **PulseAudio** and **ALSA** (`AudioDrivers`, `AudioDriverConfigDialog`).
- SoundFont: bundled SONiVOX EAS GM Wavetable plus user-importable `.sf2` files, with a soundfont
  test dialog; MIDI handled by `libmidihandler`/FluidSynth.
- Input: touch control profiles and editor, external controller bindings, gamepad models
  (`gamepad_models.json`), cursor speed/size options.

## Host requirements

- Android 8.0 (API 26) or newer, **arm64-v8a** device.
- Vulkan-capable GPU for Turnip/DXVK paths; Adreno devices get the most options through Turnip and
  `libadrenotools`.
- Storage for the RootFS, container pattern and installed components (several hundred MB).

## Reporting compatibility

Use the **Game Compatibility Report** issue template (`.github/ISSUE_TEMPLATE/`), one game per
report. Useful information:

1. Game name, version and how it was installed.
2. Device: model, SoC, GPU, Android version.
3. Container configuration: screen size, Vulkan driver, OpenGL driver, DX wrapper and version,
   Box64 preset, Wine version, custom env vars.
4. Result: works / partially works / does not start, with the exact failure point.
5. Logs: Wine debug output, Box64 logs, `logcat`.
6. Screenshots or a short recording when relevant.

Confirmed reports will be collected here in a per-game compatibility table as soon as enough
results exist. Until then, treat every game as untested on Vanta.
