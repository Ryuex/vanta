# Graphics runtime packages

Vanta imports graphics runtimes as data-only ZIP packages. Imports are staged,
checked against `vanta-graphics-runtime.json`, verified by SHA-256, ABI-checked,
and moved into the private `files/graphics-runtimes/` store only after
validation. No package installer scripts are run. The system Vulkan driver and
the existing Turnip/Vortek paths are not overwritten by package import.

The manifest records a schema version, package ID/name/version/kind, HTTPS
upstream and revision, license identifier and license text path, ABI, loader,
primary library path and SHA-256, every packaged shared library and its
SHA-256, supported GPU families/models, kernel/kbase/scheduler requirements,
Vulkan version, experimental status, and limitations. Shared libraries must be
ARM64 ELF files. Files not listed as libraries, the license text, or the
manifest are rejected. ZIP paths are normalized and checked against traversal,
duplicates, unexpected files, and size/count limits.

Vulkan driver and wrapper packages are separate kinds. A driver may be chosen
per Mali container only when the package identifies the Vanta `adrenotools`
loader and its metadata matches the probed ABI, exact GPU model/family, and
known scheduler/kernel requirements. Unknown requirements are not assumed to
match. The advanced override is explicit and may expose unverified packages;
it never changes the system driver. Imported driver libraries are passed to
Vortek's existing custom Vulkan-library path. If the custom library cannot be
opened or lacks the required Vulkan entry points, Vanta logs the failure and
falls back once to the system Vulkan library. The selected package remains
installed and the saved selection remains reversible.
The manifest loader identifier is `adrenotools` for drivers; wrappers may name
`adrenotools` or `android-vulkan-loader`, but wrapper packages are not currently
activated by the runtime.

The probe reads the Android system Vulkan loader and OpenGL renderer. It is not
proof of which Vulkan driver a game or container actually uses. FPS Completo
therefore reports the selected library separately from whether a custom or
system library loaded, and explicitly does not claim that the physical driver
was confirmed.

No third-party PanVK binary or standalone Vulkan wrapper is bundled or
recommended by this foundation. Upstream Mesa PanVK's Linux DRM/kernel
requirements do not establish compatibility with Android's Mali `kbase`
interface. Community Android ports are device/kernel-specific, and the
researched candidate packages did not provide sufficient combined evidence for
provenance, licensing, dependencies, loader ABI, and target-device support.
The Runtime Manager's import path remains available for packages that can later
meet those checks. Vanta Wrapper (Auto) currently resolves to the stable
existing path; it is orchestration labeling, not a new Vanta Vulkan
implementation.

Current bundled graphics components (repository asset inventory):

| Layer | Components |
|---|---|
| Vulkan driver/renderer | Turnip 26.2.0; Vortek 2.1 |
| OpenGL renderer components | Zink 22.2.5; VirGL 23.1.9; Gladio 1.1 |
| DirectX translation | DXVK 1.10.3 and 2.4.1; D7VK 1.11; D8VK 1.0; VKD3D 2.14.1; WineD3D |

Runtime Manager labels for imported packages distinguish them from bundled
components. Imported graphics packages are not active globally and are not
listed as compatible/recommended solely because they are installed.

The bundled `.tzst` archives live under `app/src/main/assets/graphics_driver/`
and `app/src/main/assets/dxwrapper/`. At session startup, Turnip/Vortek and
OpenGL renderer files are extracted into the container RootFS under `/usr/lib`
and the applicable Vulkan ICD directory; the selected graphics-driver IDs and
versions are persisted in each container's graphics configuration. DXVK,
D7VK/D8VK, VKD3D, WineD3D, and the selected DDraw implementation are installed
into the container Wine prefix's Windows directories and configured by the
container's DirectX-wrapper settings. Imported graphics packages remain in the
app-private `files/graphics-runtimes/` store and are passed only to Vortek when
explicitly selected for a compatible Mali container.
