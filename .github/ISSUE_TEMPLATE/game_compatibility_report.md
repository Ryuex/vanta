---
name: Game Compatibility Report
about: Tell us how a specific Windows game or application behaves in Vanta (one game per report).
title: "[Compat] "
labels: compatibility
assignees: ''
---

## Game

- Title:
- Version / edition:
- Store or media: <!-- Steam, GOG, disc, installer, portable exe, ... -->
- Install method: <!-- launcher, in-app file manager, copied into the container, ... -->

## Environment

- Vanta version (`versionName` / `versionCode`):
- Device model / SoC / GPU:
- Android version:
- Rooted: <!-- yes / no -->

## Container configuration

- Screen size / resolution:
- Vulkan driver:
- OpenGL driver:
- DX wrapper and version:
- Box64 preset:
- Wine version:
- Custom environment variables / extra arguments:

## Result

- [ ] Works
- [ ] Partially works
- [ ] Does not start

<!-- If partial, list exactly what works and what does not. -->

## Failure point

<!-- e.g. launcher crashes / intro video missing / 3D scenes render black / no sound / input not detected / savegames fail -->

## Steps to reproduce

1.
2.
3.

## Logs

<!-- Wine debug output, Box64 logs, `logcat` excerpt. Text only, please. -->

```

```

## Screenshots / recording

## Regression information

<!-- Does it work with a different driver or wrapper? Which one? This is extremely useful. -->
