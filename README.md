cat > README.md <<'EOF'
<p align="center">
  <img src="docs/images/vanta-logo.png" width="180" alt="Vanta Logo">
</p>

<h1 align="center">Vanta</h1>

<p align="center">
  <b>Windows compatibility environment for Android</b>
</p>

<p align="center">
  Based on Winlator • Wine • Box64 • Mesa • DXVK
</p>

---

> [!WARNING]
> **Vanta is currently experimental.**
>
> Pre-release builds may contain bugs, crashes, graphical issues,
> performance regressions or device-specific incompatibilities.

## 💜 About Vanta

Vanta is an Android Windows compatibility project focused on performance,
runtime management, GPU compatibility and a polished mobile experience.

Vanta is based on **Winlator**, created by
**BrunoSX / brunodev85**.

Original Winlator project:

https://github.com/brunodev85/winlator

The project builds upon the work of Winlator while developing additional
runtime management, graphics configuration, performance tools, UI improvements
and experimental GPU compatibility features.

Vanta does not claim authorship of upstream Winlator code.

---

## 🚀 Current Development

Current Vanta development includes:

- Runtime Manager
- Wine management
- DXVK and DirectX translation configuration
- Graphics driver management
- Graphics wrapper management
- Box64 configuration and presets
- Vanta Thermal Guard
- Performance profiles
- GPU capability detection
- Mali Runtime foundation
- Graphics Runtime Packages
- Separate Vulkan Driver and Wrapper architecture
- PanVK package infrastructure
- Vanta Wrapper architecture
- Improved virtual controls
- Vanta desktop experience
- Turnip / Adreno support
- Per-container graphics configuration

Vanta is under active development and some features are still experimental.

---

## 🟣 Vanta Mali Runtime

Vanta already includes a working graphics wrapper/runtime path inherited and
adapted from its current Winlator-based graphics stack.

The important distinction is that the current Vanta build does **not yet bundle
multiple alternative Mali wrapper versions**.

Vanta 3.3 introduces a modular graphics-runtime architecture designed to allow
different Vulkan drivers and graphics wrappers to coexist without permanently
replacing the stable runtime path.

### Graphics Wrappers

Vanta already ships with its current **Stable / Current** graphics
wrapper/runtime path.

The new graphics-runtime architecture adds support for additional wrapper
packages.

At this stage:

- The existing Vanta graphics wrapper/runtime path remains available.
- Stable / Current remains the conservative fallback.
- Additional alternative wrapper versions are not bundled with the APK yet.
- Compatible graphics wrapper packages can be imported through Vanta.
- Community wrapper packages can be imported when they follow the expected
  Vanta runtime-package format and are compatible with the device.
- Experimental wrappers can remain isolated from the stable runtime.
- Different implementations can be tested without permanently replacing the
  current graphics path.

Wrapper compatibility can depend on:

- GPU
- Vulkan driver
- Android version
- kernel
- graphics capabilities
- DirectX translation layer
- game

Because of these differences, importing a wrapper does not guarantee
compatibility with every device or game.

Users should keep the **Stable / Current** configuration available as a
fallback.

---

## 💜 Vanta Wrapper

The **Vanta Wrapper** system is currently under development.

`Vanta Wrapper (Auto)` is designed as an orchestration and automatic-selection
layer.

The long-term objective is for Vanta to inspect information such as:

- GPU model
- GPU generation
- Vulkan capabilities
- active Vulkan driver
- graphics features
- compatibility information
- wrapper availability

and select an appropriate graphics backend.

For now, `Vanta Wrapper (Auto)` uses the existing Stable / Current path as its
conservative fallback when an alternative configuration has not been validated.

Vanta does not claim authorship of third-party wrapper implementations.

Third-party wrappers remain projects of their respective developers and retain
their respective licenses and attribution.

---

## 🌐 Community Graphics Runtimes

The Vanta Graphics Runtime Package system is designed to allow graphics
components to evolve independently from the main APK.

This architecture can allow compatible community packages to be imported into
Vanta for testing.

This includes future packages for:

- Graphics wrappers
- Vulkan drivers
- Experimental Mali components
- Other compatible graphics-runtime components

The objective is to make graphics experimentation possible without requiring
every experimental component to be permanently bundled with Vanta.

Community packages are experimental unless explicitly stated otherwise.

Users should only import packages from sources they trust.

---

## 🧩 Graphics Runtime Architecture

Vanta separates the graphics stack into different layers.

Conceptually:

Windows Game

↓

DXVK / D7VK / VKD3D / WineD3D

↓

Graphics Wrapper (when required)

↓

Vulkan Driver

↓

Android / Linux GPU interface

↓

GPU

This distinction is important.

A **graphics wrapper** and a **Vulkan driver** are not the same component.

---

## 🟠 PanVK

PanVK support is under active development.

Vanta 3.3 currently contains infrastructure for managing Vulkan-driver packages,
but a complete third-party PanVK runtime is **not yet bundled with the APK**.

PanVK is treated by Vanta as a **Vulkan driver**, not as a graphics wrapper.

The intended Mali architecture can support configurations conceptually similar
to:

Windows Game

↓

DXVK / D7VK / VKD3D

↓

Graphics Wrapper

↓

PanVK

↓

Mali GPU

or:

Windows Game

↓

DXVK / D7VK / VKD3D

↓

Graphics Wrapper

↓

System / Proprietary Mali Vulkan Driver

↓

Mali GPU

Compatible PanVK builds can eventually be imported and managed as versioned
graphics-runtime packages.

This avoids treating a single experimental PanVK build as universally
compatible with every Mali device.

PanVK compatibility may depend on:

- Mali GPU generation
- Bifrost / Valhall / newer architectures
- Android version
- kernel
- kbase implementation
- Job Manager / CSF
- Vulkan capabilities
- graphics wrapper
- DirectX translation layer

For this reason, Vanta will not assume that one PanVK build works on every
Mali GPU.

PanVK integration is currently experimental and is not yet considered complete.

---

## 🔥 Thermal Guard

Vanta includes development toward a thermal-management and sustainable
performance layer.

The goal of Vanta Thermal Guard is not to bypass Android thermal protection.

Instead, Vanta can use Android thermal information to help detect thermal
pressure and provide appropriate warnings or performance policies.

The project prioritizes sustainable mobile performance rather than only maximum
short-term FPS.

---

## 🎮 Adreno / Turnip

The existing Adreno graphics path remains supported.

Vanta preserves the Turnip-based graphics stack used for compatible Adreno
devices.

Development of the Mali Runtime must not require replacing the working Adreno
path.

---

## 🧪 Experimental Software

Vanta is currently experimental software.

A successful APK build does not guarantee compatibility with every:

- Android device
- GPU
- driver
- Windows application
- game

Pre-release builds may contain regressions.

When reporting a graphics problem, useful information includes:

- Vanta version
- Device model
- GPU model
- Android version
- Vulkan driver
- Graphics wrapper
- DXVK / D7VK / VKD3D version
- Wine version
- Box64 version
- Performance profile
- Description of the problem

Future Vanta diagnostics are intended to make this information easier to
collect.

---

## 📦 Downloads

Official public Vanta builds are distributed through the repository's
**GitHub Releases**.

Versions marked **Pre-release** should be considered experimental and intended
for testing.

Do not assume a Pre-release build is more stable simply because its version
number is newer.

---

## 🛠️ Credits and Upstream Projects

Vanta exists because of the work of many open-source developers and projects.

### ❤️ Winlator

Vanta is based on the **Winlator** project.

**Winlator was created by BrunoSX / brunodev85.**

Original project:

https://github.com/brunodev85/winlator

A significant part of the foundation that makes Vanta possible originates from
Winlator and the projects integrated by it.

Vanta preserves credit to the original Winlator project and does not claim
authorship of upstream Winlator code.

Special thanks to **BrunoSX / brunodev85** for creating Winlator and making
this type of Android compatibility project possible.

### Third-Party Projects

Special thanks to the developers and communities behind:

- **Winlator** — BrunoSX / brunodev85
- **Wine** — WineHQ
- **Box86 / Box64** — ptitSeb
- **Mesa**
- **Turnip**
- **Panfrost / PanVK**
- **DXVK**
- **VKD3D**
- **CNC DDraw**
- **VirGL**
- **Vortek**
- **Termux / GLIBC patches and related projects**

Additional components may have their own authors, licenses and attribution.

All applicable upstream copyright notices, licenses and attribution must remain
preserved.

---

## 📜 License

Vanta preserves the licensing requirements and attribution of the upstream
projects it uses.

See the repository license, source files and third-party notices for additional
licensing information.

Contributors and community runtime-package developers are responsible for
respecting the licenses of the components they distribute.

---

## ⚠️ Disclaimer

Vanta is an independent community project based on Winlator.

Vanta is not affiliated with or endorsed by Microsoft.

Windows is a trademark of Microsoft Corporation.

Third-party projects mentioned in this repository belong to their respective
developers and organizations.
EOF
