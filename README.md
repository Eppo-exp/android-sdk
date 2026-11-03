# Eppo Android SDK

[![Test](https://github.com/Eppo-exp/android-sdk/actions/workflows/test.yaml/badge.svg)](https://github.com/Eppo-exp/android-sdk/actions/workflows/test.yaml)  
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/cloud.eppo/android-sdk/badge.svg)](https://maven-badges.herokuapp.com/maven-central/cloud.eppo/android-sdk)

[Eppo](https://geteppo.com) is a feature management and experimentation platform. This SDK enables
feature flagging and experimentation for Eppo customers. An API key is required to use it.

## Usage

### build.gradle:

```groovy
dependencies {
  implementation 'cloud.eppo:android-sdk:4.12.2'
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven {
      url "https://central.sonatype.com/repository/maven-snapshots/"
    }
  }
}
```
Snapshots of the development version are available in [Maven Central's snapshots repository](https://central.sonatype.com/repository/maven-snapshots).

## Releasing a new version

You can simply [draft a new release on GitHub](https://github.com/Eppo-exp/android-sdk/releases) and then CI will take care of the rest.

For publishing a release locally, instead, follow the steps below.

### Prerequisites

1. [Generate a user token](https://central.sonatype.org/publish/generate-token/) on `central.sonatype.com`;
2. [Configure a GPG key](https://central.sonatype.org/publish/requirements/gpg/) for signing the artifact. Don't forget to upload it to the key server;
3. Make sure you have the following vars in your `~/.gradle/gradle.properties` file:
    1. `mavenCentralUsername` - User token username for Central Portal generated in step 1
    2. `mavenCentralPassword` - User token password for Central Portal generated in step 1
    3. `signing.keyId` - GPG key ID generated in step 2
    4. `signing.password` - GPG key password generated in step 2
    5. `signing.secretKeyRingFile` - Path to GPG key file generated in step 2

Once you have the prerequisites, follow the steps below to release a new version:

1. Bump the project version in `build.gradle`
2. Run `./gradlew eppo:publish -Prelease` or `./gradlew eppo:publish -Psnapshot`
3. For releases, run `./gradlew eppo:publishAndReleaseToMavenCentral -Prelease` to automatically release to Maven Central

## Getting Started
For information on usage, refer to our [SDK Documentation](https://docs.geteppo.com/sdks/client-sdks/android/).


## Migrating from 4.x to 5.0

The standard SDK now depends on `cloud.eppo:android-sdk-framework`. Applications using only
assignment methods keep those entry points; integrations using builders or storage must update:

- Replace `Builder.host(host)` with `apiBaseUrl(host + "/api")` for the standard endpoint layout.
  `apiBaseUrl` is a complete base URL and does not append `/api`. The legacy static `init` and
  `initAsync` host arguments retain their 4.x meaning and append `/api`.
- Replace byte-array initial configuration with `CompletableFuture<Configuration>`.
  Parse bytes with `JacksonConfigurationParser.parseFlagConfig`, then build the configuration
  with `buildConfig(parsedFlags, null, null, null)`.
- Custom stores now implement `CachingConfigurationStore<Configuration>` from the framework.
  The old `cloud.eppo.android.ConfigurationStore`, `ConfigCacheFile` and `BaseCacheFile` classes
  are removed. Storage abstractions live under `cloud.eppo.android.framework.storage`.
- The default cache changes from raw JSON (`eppo-sdk-config-v4-flags-<key>.json`) to Java
  serialization (`eppo-sdk-flags-<key>.ser`). There is no migration of the old cache;
  an offline first launch after upgrading cannot use it. Plan an online configuration fetch.
  Default-codec consumer rules preserve serialized Eppo class and field names in minified apps;
  custom codecs and custom configuration types must supply their own compatibility rules.
- Assignment details are available through the `get*AssignmentDetails`, `getJSONAssignmentDetails`
  and `getBanditActionDetails` methods.

Use the coordinated release workflow in PR #262 for both Android artifacts;
do not publish the standard SDK with a snapshot framework dependency.
