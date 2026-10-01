# Vanta documentation

Documentation for **Vanta**, a Windows compatibility environment for Android based on
[Winlator](https://github.com/brunodev85/winlator).

## Index

| Document | Contents |
|---|---|
| [architecture.md](architecture.md) | Layer-by-layer architecture, data flow and source map |
| [building.md](building.md) | Toolchain requirements, build commands and outputs |
| [compatibility.md](compatibility.md) | Graphics stack, DirectX wrappers, Box64 presets, how to report compatibility |
| [media/README.md](media/README.md) | Conventions for screenshots, banners, GIFs and the demo placeholder |

## Repository-level documents

| Document | Contents |
|---|---|
| [../README.md](../README.md) | Project overview, features, roadmap, downloads |
| [../CREDITS.md](../CREDITS.md) | Upstream and third-party attribution |
| [../CHANGELOG.md](../CHANGELOG.md) | Vanta changelog |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | Branches, pull requests and bug reporting |
| [../LICENSE](../LICENSE) | LGPL-2.1 license text |

## Conventions

- Documentation must describe **only what exists in this repository**. Planned work belongs to the
  Roadmap sections and must be marked as planned or experimental.
- Version numbers quoted in the documentation are read from
  `app/src/main/java/io/vanta/app/core/DefaultVersion.java` and
  `app/src/main/java/io/vanta/app/core/WineInfo.java`; update them there first, then here.
- Do not introduce links to files that do not exist, and never invent URLs for releases or media.
