# Contributing to Vanta

Thanks for your interest in improving Vanta. This project is a derivative of
[Winlator](https://github.com/brunodev85/winlator) — please read
[CREDITS.md](CREDITS.md) first so the upstream attribution stays intact.

## Branches

| Branch | Purpose |
|---|---|
| `main` | Default branch of the repository. |
| `vanta-dev` | Development branch where Vanta work happens. |

- Base your work on `vanta-dev`.
- Keep branches short-lived and focused on a single topic, e.g. `docs/fix-readme`,
  `feat/compat-matrix`, `fix/container-crash`.
- Do not force-push shared branches.

## Pull requests

1. Fork the repository and create a branch from `vanta-dev`.
2. Make the change, then verify:
   - the project still compiles: `./gradlew :app:assembleDebug`;
   - documentation links you touched resolve to existing files;
   - no claim is added for a feature that is not actually implemented.
3. Open a pull request with:
   - **what** changed and **why**;
   - how you tested it (build only, or runtime — be explicit);
   - any impact on licenses, credits or third-party files.
4. Keep code changes and documentation changes clearly described; unrelated refactors should go in
   a separate PR.

Please do **not** include generated outputs in a PR: `build/`, `.gradle/`, `.cxx/`, `*.apk`,
`local.properties`, IDE files.

## Reporting bugs

Use the **Bug Report** issue template and include:

- Vanta version (`versionName`/`versionCode`) and how you installed it (release APK or self-built);
- Device model, SoC, GPU, Android version and whether the device is rooted;
- Container settings involved (graphics driver, DX wrapper, Box64 preset, Wine version);
- Steps to reproduce, expected result, actual result;
- Logs when available: Wine debug output, Box64 logs, `logcat` excerpts;
- Screenshots or a short recording when the bug is visual.

## Game compatibility reports

Use the **Game Compatibility Report** template. One game per report:

- Game name, version and how it was installed;
- Container configuration used (drivers, wrappers, presets);
- Result: works / partially works / does not start;
- What exactly fails (launcher, intro video, 3D rendering, input, audio, savegames);
- Logs or screenshots if possible.

Reports like this are the basis of the future compatibility documentation
([docs/compatibility.md](docs/compatibility.md)).

## Feature requests

Use the **Feature Request** template. Describe the problem first, then the proposed solution.
Please do not open PRs implementing a large planned feature without discussing it in an issue first.

## What not to send

- **Proprietary components**: Windows binaries, licensed drivers, commercial game files, ROMs or any
  material you do not have the right to redistribute.
- **Content without an appropriate license**: any third-party code or asset must come with a license
  that allows redistribution in this project, plus clear attribution.
- **Prebuilt APKs** or large binaries in the repository — link to releases instead.
- **Upstream code that would remove credits**, license texts or notices from this repository.

When in doubt, open an issue before sending a pull request.

## Code conventions

- Java is used throughout the app module (`io.vanta.app`); keep new code in the existing style of
  the surrounding files.
- Native code lives in `app/src/main/cpp/` and is built with CMake; keep module boundaries
  (`vanta`, `virglrenderer`, `vortekrenderer`, `gladiorenderer`, `midihandler`, `libadrenotools`).
- Documentation changes should follow the structure in [README.md](README.md) and
  [docs/README.md](docs/README.md).
