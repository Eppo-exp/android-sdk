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

For stable releases, follow [Publishing both Android artifacts](#publishing-both-android-artifacts).
Publish the GitHub release after setting the versions and validating the release inputs.
Saving a draft does not start publication.

For a local snapshot, set both module versions to snapshot versions and configure
Maven Central credentials, then run both guarded publication tasks in one invocation:

```bash
./gradlew :android-sdk-framework:publishAllPublicationsToMavenCentralRepository \
  :eppo:publishAllPublicationsToMavenCentralRepository -Psnapshot
```

Do not use the direct Maven tasks for stable releases: JReleaser owns release signing
and the coordinator records provenance and verifies both artifacts.

## Getting Started
For information on usage, refer to our [SDK Documentation](https://docs.geteppo.com/sdks/client-sdks/android/).


## Publishing both Android artifacts

Stable releases start by publishing a GitHub release tagged `v<android-sdk version>`
from a commit on main. The tag must match `eppo/build.gradle`. Before publishing,
set both Android module versions to stable versions and pin their Java
common/framework dependencies to stable versions already available on Maven Central. SNAPSHOT and dynamic production
dependencies are rejected. The migration currently consumes Java framework 0.1.3
and common 4.0.3; both are published on Maven Central.

The release workflow runs the Android emulator matrix and local checks using the
pinned `scripts/release/test-data-ref`, then publishes `android-sdk-framework`
before `android-sdk`. Every GitHub release requires a new SDK version. An unchanged
framework version is reused only when its recorded source/build fingerprint and
all Maven Central checksums match. Changed framework inputs require a version bump.
The emulator matrix reads that same pinned fixture ref from the release plan job.

Each publication records its exact staged AAR, POM, Gradle metadata, sources and
Javadoc checksums as an immutable JSON asset on the GitHub release before deployment.
Retries reconcile those checksums against Central and never blindly upload again.
Configuration and signing preflight runs before intent is recorded. If a run stops
after recording intent, first determine whether Central accepted a deployment. Finish
an existing deployment before retrying; do not delete provenance or blindly re-upload.
If no deployment was created, automatic reconciliation cannot complete: recover the
original staged bytes and establish a separately verified recovery plan before publishing.
Preflight narrows this interruption window; it does not make GitHub and Central atomic.
Unknown pre-existing coordinates fail closed. A final consumer resolves both AARs
and transitive dependencies from remote repositories, without Maven Local.

Publishing uses repository secrets `MAVEN_CENTRAL_TOKEN_USERNAME`,
`MAVEN_CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`, and `GPG_PASSPHRASE`, plus repository
variable `GPG_PUBLIC_KEY`. GitHub release publication authorizes deployment; no
separate environment approval is introduced. Prereleases are ignored.

Pushes to main and `snapshot/**` continue publishing both snapshot artifacts.
After a stable release, restore snapshot versions before the next snapshot push.
The workflow changes are a separate stack entry above migration PR #261.
