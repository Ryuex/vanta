# Compatibility

This page describes the compatibility surface of Vanta as it exists in the repository today, and
how to report results so this document can grow.

> **Status:** Build 2 container startup was physically verified by the project owner. Build 3
> component-management changes have only been statically validated and compiled; Build 3 has not
> been physically tested. No game compatibility results are claimed here.

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

Box64 is bundled as `assets/box64/box64-0.4.4.tzst`; its ARM64 executable is extracted to
`/usr/local/bin/box64`. The RC file and `BOX64_RCFILE` setup are preserved. Box64 version and preset
are persisted per container and resolved before the guest Wine process is launched.

Built-in presets use only controls listed in `assets/box64/env_vars.json`:

| UI preset | Existing profile | Values (`SAFEFLAGS, FASTNAN, FASTROUND, X87DOUBLE, BIGBLOCK, STRONGMEM, FORWARD, CALLRET, WAIT, NATIVEFLAGS, WEAKBARRIER`) |
|---|---|---|
| Compatibility | `STABILITY` | `2,0,0,1,0,2,128,0,0,0,0` |
| Balanced | `INTERMEDIATE` | `2,1,0,1,2,0,128,0,1,0,2` |
| Performance | `PERFORMANCE` | `1,1,1,0,3,0,512,1,1,1,2` |
| Custom | User-defined | Only the existing JSON-listed Box64 controls can be edited |

The legacy **Conservative** profile remains available so existing saved containers do not lose
their settings. `BOX64_DYNAREC=1`, logging controls, and the RC file path are launcher settings
rather than preset-specific values.

## Wine

| Item | Value |
|---|---|
| Main Wine version | **10.10**, in the bundled RootFS under `/opt/wine` (`WineInfo.MAIN_WINE_VERSION`) |
| Additional versions | Importable in Wine Manager from x86_64 Wine archives accepted by `WineInstaller` |
| Wineprefix handling | RootFS-based, versioned through `RootFSInstaller.UPDATE_WINEPREFIX_VERSION` |
| Debug channels | Selectable Wine debug channels (`wine_debug_channels.json`, `enable_wine_debug`) |

Bundled Windows components (`assets/wincomponents/`): Direct3D, DirectMusic, DirectPlay,
DirectShow, DirectSound, XAudio, WMV decoder and VC runtimes 2005/2010.

Wine Manager lists the bundled default and only imported versions whose package directory contains
an x86_64 `bin/wine` or `bin/wine64` executable and a generated
`container-pattern-<version>.tzst`. It blocks removal while a container refers to that version.
Wine 11.0 is not bundled: there is no Wine 11 archive and generated prefix pattern in the
repository. A compatible package must provide the project's x86_64 Wine directory layout and
libraries and pass the existing Wine-prefix generation flow; a version-string change is not
sufficient.

## Component management limits

- Bundled DXVK is **1.10.3** and **2.4.1**, in `assets/dxwrapper/`, with `system32/` and
  `syswow64/` DLLs. DXVK Async is not a separate bundled package (the existing DXVK Async
  configuration switch does not imply an Async build is installed).
- Bundled VKD3D is **2.14.1**. VKD3D-Proton is not present. WineD3D ships with the bundled Wine
  version; D7VK 1.11 and D8VK 1.0 are also bundled.
- Per-container DXVK selection is saved in the DX wrapper configuration and used to extract the
  selected package into that container's Wine prefix. The graphics-dependent fallback remains
  2.4.1 on Turnip/Vulkan 1.3+ and 1.10.3 for other profiles.
- DXVK 2.6.x and Wine 11.0 are **not integrated**. No compatible package artifacts are in the
  repository. DXVK 2.6.x requires a validated archive compatible with Vanta's existing
  `system32/` + `syswow64/` DLL layout and component importer; Wine 11 requires the x86_64 package,
  runtime libraries, and generated versioned prefix pattern. Neither package was fabricated or
  fetched from an unverified source.
- The Settings screen's DXVK Manager lists actual bundled/imported archives, labels their source,
  and guards selected packages against removal. The container editor remains the actual per-
  container version selector.

The graphics component archives present in this repository are Turnip (Mesa) **26.2.0**, VirGL
(Mesa) **23.1.9**, Vortek **2.1**, Gladio **1.1**, and Zink (Mesa) **22.2.5**. There is no separate
Mesa archive, standalone Vulkan loader, or Zink-specific version override in the container model.
Existing Android/Vulkan and native OpenGL paths remain in use.

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
