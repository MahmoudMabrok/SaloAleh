# SaloAleh Desktop

Standalone Compose Multiplatform (JVM) counter app. Tap to count salawat,
then generate a signed QR code that the Android/iOS app scans to merge the
score into the current round. The counter clears automatically once the QR
is shown, so the same count cannot be submitted twice.

It produces the same `saloaleh-submit` payload the mobile scanner already
verifies (`app/.../ui/settings/ExtensionQrScreen.kt`).

## Requirements

- **JDK 17** (the Gradle toolchain pins `jvmToolchain(17)` — any newer JDK on
  `PATH` is fine, Gradle provisions/uses 17 for compilation).
- Building a **native installer can only target the OS you build on** —
  `jpackage` does not cross-compile. To ship all three platforms, build on
  each one (locally or via a CI matrix — see below).
- Per-OS installer prerequisites:
  | Target | Needs |
  |--------|-------|
  | Windows `.msi` | [WiX Toolset 3.x](https://wixtoolset.org/) on `PATH` |
  | macOS `.dmg` | Xcode Command Line Tools (`xcode-select --install`) |
  | Linux `.deb` | `binutils`, `fakeroot` (`sudo apt install binutils fakeroot`) |

## Run from source

```bash
./gradlew :desktop:run        # or: make desktop
```

## Build executables

All commands are run from the repo root. Output lands under
`desktop/build/compose/binaries/main/`.

### Native installer for the machine you're on

```bash
./gradlew :desktop:packageDistributionForCurrentOS   # or: make desktop-package
```

Produces, depending on the host OS:

| Host | Artifact |
|------|----------|
| Windows | `desktop/build/compose/binaries/main/msi/SaloAleh-1.0.0.msi` |
| macOS | `desktop/build/compose/binaries/main/dmg/SaloAleh-1.0.0.dmg` |
| Linux | `desktop/build/compose/binaries/main/deb/saloaleh_1.0.0_amd64.deb` |

To target one specific format explicitly: `:desktop:packageMsi`,
`:desktop:packageDmg`, or `:desktop:packageDeb`.

### App image (no installer)

A ready-to-run application folder — useful for zipping and distributing
without an installer:

```bash
./gradlew :desktop:createDistributable
# -> desktop/build/compose/binaries/main/app/SaloAleh/
```

### Cross-platform runnable JAR

A single fat JAR launched with any JDK 17+ (`java -jar <file>`). Note the
bundled Skia natives are for the build host only, so build the JAR on each
OS you want to support:

```bash
./gradlew :desktop:packageUberJarForCurrentOS
# -> desktop/build/compose/jars/
```

## Building all platforms via CI

`jpackage` cannot cross-compile, so use a GitHub Actions matrix that runs the
build once per OS:

```yaml
jobs:
  desktop:
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - run: ./gradlew :desktop:packageDistributionForCurrentOS
      - uses: actions/upload-artifact@v4
        with:
          name: saloaleh-desktop-${{ matrix.os }}
          path: desktop/build/compose/binaries/main/
```

## Versioning

The installer version comes from `packageVersion` in
`desktop/build.gradle.kts` (`compose.desktop.application.nativeDistributions`).
Bump it there before cutting a release.
