# Migration Notes: Android SDK v4 Upgrade

## Current Status
- **Phase:** Blocked - waiting for common SDK SNAPSHOT publish
- **Branch:** `feature/v4-core-upgrade/pr3-sdk-v4-upgrade`
- **PR:** #246 (Draft)
- **Last Updated:** 2026-02-21

### CI Status: FAILING (Dependency Resolution)
CI cannot compile because `eppo-sdk-framework:0.1.0-SNAPSHOT` isn't published to Maven Central Snapshots.

**Error:** `cannot find symbol: method execute(EppoConfigurationRequest)`

The tests and code are correct, but CI can't find the `execute()` method because the SNAPSHOT dependency isn't available remotely.

## Blocker

**Action Required:** Publish `eppo-sdk-framework:0.1.0-SNAPSHOT` from the common SDK repository to Maven Central Snapshots.

Once published, CI should pass because:
1. Jackson deserializers are correctly configured
2. Mock tests now stub `execute()` (the correct method in v4)
3. Offline tests already pass

## Recent Fixes

### Fixed: Mock tests calling wrong method (2026-02-21)
Tests were stubbing `mockHttpClient.get()` but SDK v4's `ConfigurationRequestor` calls `execute()`.
The `get()` method is now a deprecated default method in `EppoConfigurationClient` that delegates to `execute()`.

Changed all test mocks from:
```java
when(mockHttpClient.get(any(EppoConfigurationRequest.class))).thenReturn(response);
```
to:
```java
when(mockHttpClient.execute(any(EppoConfigurationRequest.class))).thenReturn(response);
```

### Fixed: Jackson deserializers for v4 configuration
Added EppoModule and deserializers from common SDK to properly parse v4 flag config.

Files added in `dto/adapters/`:
- `EppoModule.java`
- `FlagConfigResponseDeserializer.java`
- `BanditParametersResponseDeserializer.java`
- `EppoValueDeserializer.java`
- `EppoValueSerializer.java`
- `DateSerializer.java`

## Test Categories

### Expected Passing Tests (after SNAPSHOT is published)
- `testOfflineInit` - Uses `initialConfiguration(byte[])`
- `testObfuscatedOfflineInit` - Same with obfuscated config
- `testLoadConfiguration` - Uses mocked HTTP client
- `testPollingClient` - Uses mocked HTTP client
- All other mock-based tests
- Unit tests (local)

## Commits on Branch
- `4b9a599` fix: stub execute() instead of get() in mock tests
- `84b50dd` style: fix import ordering for spotless
- `5995f89` feat: add Jackson deserializers for v4 configuration parsing
- `0b45c98` docs: update migration notes with recent fixes
- Earlier commits for initial v4 upgrade

## Dependency Graph
```
Android SDK (eppo module)
    └── eppo-sdk-framework:0.1.0-SNAPSHOT  <-- NOT PUBLISHED
            └── Contains: EppoConfigurationClient.execute()
```
