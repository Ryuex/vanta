# Credits and Attribution

Vanta is a **derivative project based on Winlator**. This file collects the attribution that the
repository owes to the upstream project and to the third-party components that are actually used
in this tree. Nothing here replaces the license files shipped in the repository — those remain the
authoritative texts and must not be removed.

## Upstream project

| Project | Author / Links | Relationship |
|---|---|---|
| **Winlator** | [brunodev85](https://github.com/brunodev85/winlator) · [winlator.org](https://www.winlator.org) | Original project that Vanta is based on/forked from. The Java application layer, the native components, the runtime assets and the overall design come from Winlator. Please visit and credit the original repository. |

Vanta is **not** written from scratch. Any documentation, release notes or app-store listing for
Vanta must keep this attribution visible.

Note: this repository still fetches some runtime assets from the Winlator upstream repositories
(`installable_components`, `input_controls`, `wine_addons` — see
`app/src/main/java/io/vanta/app/core/GeneralComponents.java`,
`app/src/main/java/io/vanta/app/InputControlsFragment.java` and
`app/src/main/java/io/vanta/app/core/WineUtils.java`). Until Vanta hosts its own asset repository,
those downloads rely on the upstream project and its terms.

## Runtime components used by Vanta

| Component | Project / Author | License (upstream) | Where it shows up in Vanta |
|---|---|---|---|
| Wine 10.10 | [WineHQ](https://www.winehq.org/) | LGPL-2.1 | Main Windows API runtime inside the container |
| Box64 0.4.4 | [ptitSeb](https://github.com/ptitSeb) | GPL-2.0 | x86_64 → ARM64 translation (`assets/box64/`) |
| Box86 | [ptitSeb](https://github.com/ptitSeb) | GPL-2.0 | Credited with Box64 upstream; **no Box86 binary is bundled** in the current asset set |
| DXVK 1.10.3 / 2.4.1 | [doitsujin/dxvk](https://github.com/doitsujin/dxvk) | Zlib | Direct3D 8/9/10/11 → Vulkan (`assets/dxwrapper/`) |
| D7VK 1.11 / D8VK 1.0 | DXVK-family projects (D7VK / D8VK) | Zlib (DXVK lineage) | Direct3D 7 / Direct3D 8 → Vulkan (`assets/dxwrapper/`) |
| VKD3D 2.14.1 | [WineHQ vkd3d](https://gitlab.winehq.org/wine/vkd3d) | LGPL-2.1 | Direct3D 12 → Vulkan (`assets/dxwrapper/`) |
| WineD3D | [WineHQ](https://www.winehq.org/) | LGPL-2.1 | Direct3D → OpenGL fallback |
| Mesa — Turnip 26.2.0 | [mesa3d.org](https://www.mesa3d.org) | MIT | Vulkan driver for Adreno GPUs (`assets/graphics_driver/`) |
| Mesa — Zink 22.2.5 | [mesa3d.org](https://www.mesa3d.org) | MIT | OpenGL → Vulkan (`assets/graphics_driver/`) |
| Mesa — VirGL 23.1.9 | [Mesa / VirGL](https://docs.mesa3d.org/drivers/virgl.html) | MIT | Virtualized OpenGL renderer (`app/src/main/cpp/virglrenderer/`, in-tree sources carry Red Hat copyright notices) |
| Vortek 2.1 | Vortek renderer (bundled sources) | see in-tree headers | Renderer component (`app/src/main/cpp/vortekrenderer/`) |
| Gladio 1.1 | Gladio renderer (bundled sources) | see in-tree headers | OpenGL renderer component (`app/src/main/cpp/gladiorenderer/`) |
| CNC DDraw 6.6 | [FunkyFr3sh/cnc-ddraw](https://github.com/FunkyFr3sh/cnc-ddraw) | GPL-3.0 | DirectDraw implementation for classic games (`assets/dxwrapper/`) |
| GLIBC patches | [Termux Pacman glibc-packages](https://github.com/termux-pacman/glibc-packages) | GPL-2.0 | Guest userspace toolchain patches |
| PulseAudio 13.0 | [freedesktop.org – PulseAudio](https://www.freedesktop.org/wiki/Software/PulseAudio/) | LGPL (see upstream) | Audio server libraries (`app/src/main/jniLibs/`) |
| FluidSynth | [FluidSynth](https://www.fluidsynth.org/) | LGPL-2.1 | MIDI synthesis (`app/src/main/cpp/midihandler/`, bundled library + license) |
| SoundFont — SONiVOX EAS GM Wavetable | SONiVOX | see asset distribution terms | Default soundfont (`assets/soundfont/`) |

## In-tree third-party code and its licenses

These license files already exist in the repository and are **kept untouched**:

| Path | License | Copyright holder |
|---|---|---|
| `LICENSE` | LGPL-2.1 | project/upstream license text |
| `app/src/main/cpp/libadrenotools/LICENSE` | BSD 2-Clause | Billy Laws (2021) |
| `app/src/main/cpp/libadrenotools/lib/linkernsbypass/LICENSE` | BSD 2-Clause | Billy Laws (2021) |
| `app/src/main/cpp/midihandler/fluidsynth/LICENSE` | LGPL-2.1 | Free Software Foundation |
| `app/src/main/cpp/virglrenderer/**` sources | MIT-style notices | Red Hat Inc. and others (see file headers) |

**libadrenotools** enables rootless loading of custom Adreno GPU drivers (Turnip) and driver
configuration redirection; see `app/src/main/cpp/libadrenotools/README.md`.

## Build-time dependencies

Declared in `app/build.gradle` and the root `build.gradle`:

- Android Gradle Plugin 8.4.2 / Gradle 8.14.5
- `androidx.appcompat`, `androidx.preference`, Material Components
- `com.github.luben:zstd-jni`, `org.tukaani:xz`, `org.apache.commons:commons-compress`
- `androidx.lifecycle:lifecycle-process`

All of them keep their own upstream licenses.

## Logo and visual identity

`logo.png` is the logo inherited from the upstream Winlator project. It is displayed with credit
until a dedicated Vanta logo exists. New Vanta media must follow
[docs/media/README.md](docs/media/README.md).

## Contributions of the community

Special thanks to the Winlator author and contributors, to the developers of every component listed
above, and to everyone testing and reporting compatibility for Vanta.

If you find a component used in this repository that is missing from this list, please open an issue
or a pull request so the attribution can be completed.
