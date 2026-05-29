# Eppo Android SDK

## Module Structure

Three-layer dependency chain:

```
eppo-sdk-framework (external, platform-neutral flag evaluation)
  └── sdk-common-jvm (external, OkHttp + Jackson + JVM defaults)
        └── :android-sdk-framework (Android abstractions)
              └── :eppo (batteries-included public API)
```

| Module | Version | Artifact | Role |
|---|---|---|---|
| `:eppo` | 4.12.1 | `cloud.eppo:android-sdk` | Public `EppoClient` / `EppoPrecomputedClient`, Moshi parser, default implementations |
| `:android-sdk-framework` | 0.1.0-SNAPSHOT | `cloud.eppo:android-sdk-framework` | `BaseAndroidEppoClient<T>`, `ByteStore`, `ConfigurationCodec`, `CachingConfigurationStore` |
| `eppo-sdk-framework` | 0.1.0-SNAPSHOT | external | `BaseEppoClient`, `IConfigurationStore`, `EppoConfigurationClient`, `ConfigurationParser<T>` |
| `sdk-common-jvm` | 4.0.0-SNAPSHOT | external | `OkHttpEppoClient`, `JacksonConfigurationParser`, `Configuration`, data models |

`:eppo` and `:android-sdk-framework` are versioned together. `:example` is a sample app only.

## Android API Constraints

**compileSdk 34, minSdk 26.** The build target is Android's `android.jar`, not a full JDK.

APIs that are NOT available even though they appear in Java 9+ docs:

- `java.io.ObjectInputFilter` — Java 9+, absent from `android.jar` at all API levels. Do not use it in any module that compiles against Android.
- `java.lang.ProcessHandle` — Java 9+, absent.
- `java.util.concurrent.Flow` — Java 9+, absent.

When a dependency or pattern requires a Java 9+ API, it will compile locally (against a full JDK) but fail CI (which uses Android's SDK). Check `android.jar` contents before adding new Java 9+ usages.

## Key Abstractions

`ByteStore` — read/write interface for raw bytes; Android implementation uses `FileBackedByteStore` (app private internal storage). Inaccessible to other apps on non-rooted devices.

`ConfigurationCodec` — serialization interface; default uses Java serialization. The `Default` implementation does not apply `ObjectInputFilter` (not available in Android SDK); the deserialization risk is low because bytes come from the SDK's own write path and live in private storage.

`CachingConfigurationStore` — wraps `ByteStore` + `ConfigurationCodec` with an in-memory `AtomicReference`; optimistic write with compareAndSet revert on IO failure.

## Build & Test

```bash
make test-data   # clone sdk-test-data and copy UFC fixtures into androidTest/assets
make test        # run test-data then ./gradlew connectedCheck (requires connected device/emulator)
./gradlew :android-sdk-framework:test        # unit tests (Robolectric)
./gradlew spotlessApply                      # auto-fix formatting
```

## Publishing

Both modules use the `vanniktech` Maven publish plugin targeting **Sonatype Central Portal** (not the legacy OSSRH).

Required in `~/.gradle/gradle.properties`:
```
mavenCentralUsername=<your-central-portal-token-username>
mavenCentralPassword=<your-central-portal-token-password>
```

Publish flow enforces `-Prelease` or `-Psnapshot` flag:
```bash
./gradlew :eppo:publish -Prelease                  # release build
./gradlew :android-sdk-framework:publish -Psnapshot  # snapshot build
```

`make publish-release` runs tests then checks for `mavenCentralUsername`/`mavenCentralPassword` before publishing `:eppo`.

GPG signing uses `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE` environment variables (CI) or `signing.*` properties (local).

## Gradle Conventions

- Spotless (Google Java Format) enforced on all `.java` files — run `spotlessApply` before committing.
- `checkVersion` task validates the `-Prelease`/`-Psnapshot` flag before any publish task runs.
- Snapshot repo: `https://central.sonatype.com/repository/maven-snapshots/` — scope with `content { includeModule(...) }` to avoid querying it for all artifacts.
