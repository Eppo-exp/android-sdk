# Eppo Android SDK

[![Test](https://github.com/Eppo-exp/android-sdk/actions/workflows/test.yaml/badge.svg)](https://github.com/Eppo-exp/android-sdk/actions/workflows/test.yaml)  
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/cloud.eppo/android-sdk/badge.svg)](https://maven-badges.herokuapp.com/maven-central/cloud.eppo/android-sdk)

[Eppo](https://geteppo.com) is a feature management and experimentation platform. This SDK enables
feature flagging and experimentation for Eppo customers. An API key is required to use it.

## Usage

### build.gradle:

```groovy
dependencies {
  implementation 'cloud.eppo:android-sdk:4.9.3'
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
