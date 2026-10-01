# Changelog

All notable changes to **Vanta** are documented here.
This project follows the upstream Winlator history; only Vanta-specific phases are recorded below.

## [Unreleased] — Phase 1: repository identity

First phase of the Vanta project. Scope of this phase is limited to repository identity,
documentation and tooling — **no runtime validation is claimed**.

### Added

- Fork/base established from [Winlator](https://github.com/brunodev85/winlator) with the upstream
  history preserved.
- Vanta identity in the code base: application id and namespace `io.vanta.app`, application label
  `Vanta`, native module directory renamed from `winlator/` to `vanta/`.
- Compilable baseline confirmed: a debug APK (`app-debug.apk`) was built locally from this tree.
- Initial documentation set: `README.md`, `CREDITS.md`, `CONTRIBUTING.md`, `docs/` (index,
  architecture, building, compatibility, media conventions).
- GitHub issue templates: Bug Report, Game Compatibility Report and Feature Request.

### Notes

- The UI is still the upstream Winlator interface; the Vanta interface is planned, not implemented.
- No release has been published yet; no runtime or game-compatibility testing has been performed or
  reported for Vanta builds.
- `versionName 11.2` / `versionCode 33` are inherited from the upstream baseline and will be
  redefined for the first Vanta release.
