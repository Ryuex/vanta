# Building Vanta

How to build the project from this repository. Documentation only — no build workflow is defined in
`.github/` at the moment (the directory contains `FUNDING.yml` and issue templates).

## Requirements

| Tool | Version used in this repository |
|---|---|
| JDK | 17 (required by Android Gradle Plugin 8.x) |
| Android SDK | `compileSdk 35` |
| Build tools / AGP | Android Gradle Plugin **8.4.2** |
| Gradle | **8.14.5** (wrapper: `./gradlew`) |
| NDK | **24.0.8215888** |
| CMake | **3.22.1** |
| Target ABI | `arm64-v8a` only |
| minSdk / targetSdk | 26 (Android 8.0) / 28 |

Versions are declared in `build.gradle`, `gradle/wrapper/gradle-wrapper.properties` and
`app/build.gradle`.

## Steps

1. Install the Android SDK, NDK `24.0.8215888` and CMake `3.22.1` (Android Studio SDK Manager is
   the simplest way).
2. Make sure `local.properties` points to your SDK installation:
   ```
   sdk.dir=/path/to/Android/Sdk
   ```
   (`local.properties` is ignored by git and must not be committed.)
3. Build the debug APK:

   ```bash
   ./gradlew :app:assembleDebug
   ```

4. Find the artifact:

   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

   Debug builds enable minification (`minifyEnabled true`) with `proguard-rules.pro`.

## Notes

- The native part is built through CMake from `app/src/main/cpp/CMakeLists.txt`
  (`vanta`, `virglrenderer`, `vortekrenderer`, `gladiorenderer`, `midihandler`, `libadrenotools`).
- `app/build/`, `.gradle/`, `.cxx/` and `*.apk` are build outputs and must not be committed.
- Gradle resolves dependencies from Maven Central and Google's Maven repository, so the first build
  needs network access.
- The repository does not currently configure a `release` signing config; release APKs are not
  produced out of the box.

## Status of builds

A **debug APK was built locally from this tree**, which confirms the baseline compiles. That is a
compile check only: no runtime or game testing has been performed for Vanta builds. Alpha builds,
when published on [GitHub Releases](https://github.com/Ryuex/vanta/releases), are experimental.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `SDK location not found` | Missing `local.properties` with `sdk.dir` |
| NDK version mismatch | NDK `24.0.8215888` not installed |
| CMake not found | CMake `3.22.1` not installed through SDK Manager |
| Out-of-memory during Gradle build | Increase Gradle heap in `gradle.properties` |
| Stale native build | Delete `app/.cxx/` and `app/build/`, then rebuild |
