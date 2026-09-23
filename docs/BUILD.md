# Build

## Prereqs

- Android SDK with platforms `android-36`, `android-37` and build-tools `36.0.0`
  (`sdk.dir` in `local.properties`)
- JDK 17 — locally via `org.gradle.java.home` in `~/.gradle/gradle.properties`
  (global, machine-specific, never committed); CI uses `actions/setup-java`
- Internet for first dependency fetch (Maven Central + Google)

## Gradle without re-downloading

The wrapper pins Gradle 9.7.1. If the distribution already sits in
`~/.gradle/wrapper/dists/gradle-9.7.1-bin`, `./gradlew` reuses it —
nothing is downloaded again.

```bash
./gradlew assembleDebug      # APK -> app/build/outputs/apk/debug/
./gradlew installDebug       # install on a connected device
./gradlew assembleDebug --rerun-tasks   # full clean-room verification
```

## AGP 9 gotcha: built-in Kotlin

AGP 9.4 compiles Kotlin itself — applying `org.jetbrains.kotlin.android`
fails the build. This project therefore:

- applies only `com.android.application`, `org.jetbrains.kotlin.plugin.compose`,
  `org.jetbrains.kotlin.plugin.serialization`;
- pins the compiler via `buildscript { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20") }`;
- configures `kotlin { compilerOptions { jvmTarget = 17 } }` (provided by AGP).

Needs: Gradle ≥ 9.6.0, JDK 17, build-tools 36.0.0 (all satisfied here).

## Release

Debug builds use the default debug key. For Play uploads, add a signing
config + `targetSdk 36` already meets the Aug 2026 Play requirement.
Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
plus `CHANGE_WIFI_MULTICAST_STATE` (normal, auto-granted — lets mDNS/SSDP
multicast reach the app for the Neighbor tool).
