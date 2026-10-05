# Eppo Android SDK

[![Test](https://github.com/Eppo-exp/android-sdk/actions/workflows/test.yaml/badge.svg)](https://github.com/Eppo-exp/android-sdk/actions/workflows/test.yaml)  
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/cloud.eppo/android-sdk/badge.svg)](https://maven-badges.herokuapp.com/maven-central/cloud.eppo/android-sdk)

[Eppo](https://geteppo.com) is a feature management and experimentation platform. This SDK enables
feature flagging and experimentation for Eppo customers. An API key is required to use it.

## Usage

### build.gradle:

```groovy
dependencies {
  implementation 'cloud.eppo:android-sdk:4.11.1'
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


## Publishing both Android artifacts

Stable releases start by publishing a GitHub release tagged `v<android-sdk version>`
from a commit on main. The tag must match `eppo/build.gradle`. Before publishing,
set both module versions and their Java common/framework dependencies to stable
versions already available on Maven Central. SNAPSHOT and dynamic production
dependencies are rejected. The migration's snapshot pins remain until Java v4 ships.

The release workflow runs the Android emulator matrix and local checks using the
pinned `scripts/release/test-data-ref`, then publishes `android-sdk-framework`
before `android-sdk`. Every GitHub release requires a new SDK version. An unchanged
framework version is reused only when its recorded source/build fingerprint and
all Maven Central checksums match. Changed framework inputs require a version bump.
The emulator matrix reads that same pinned fixture ref from the release plan job.

Each publication records its exact staged AAR, POM, Gradle metadata, sources and
Javadoc checksums as an immutable JSON asset on the GitHub release before deployment.
Retries reconcile those checksums against Central and never blindly upload again.
If a run stops after recording intent, inspect its existing Central deployment and
finish that deployment before retrying; do not delete provenance to force an upload.
Unknown pre-existing coordinates fail closed. A final consumer resolves both AARs
and transitive dependencies from remote repositories, without Maven Local.

Publishing uses repository secrets `MAVEN_CENTRAL_TOKEN_USERNAME`,
`MAVEN_CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`, and `GPG_PASSPHRASE`, plus repository
variable `GPG_PUBLIC_KEY`. GitHub release publication authorizes deployment; no
separate environment approval is introduced. Prereleases are ignored.

Pushes to main and `snapshot/**` continue publishing both snapshot artifacts.
After a stable release, restore snapshot versions before the next snapshot push.
The workflow changes are a separate stack entry above migration PR #261.
