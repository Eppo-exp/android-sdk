# Migration Notes: Android SDK v4 Upgrade

## Current Status
- **Phase:** Verifying CI after mock test fix
- **Branch:** `feature/v4-core-upgrade/pr3-sdk-v4-upgrade`
- **PR:** #246 (Draft)
- **Last Updated:** 2026-02-21

### CI Status: PENDING
- `testOfflineInit` tests PASS after adding Jackson deserializers
- Mock HTTP client tests should now PASS after switching from `get()` to `execute()`

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

### Expected Passing Tests
- `testOfflineInit` - Uses `initialConfiguration(byte[])`
- `testObfuscatedOfflineInit` - Same with obfuscated config
- `testLoadConfiguration` - Uses mocked HTTP client (now fixed)
- `testPollingClient` - Uses mocked HTTP client (now fixed)
- All other mock-based tests (now fixed)
- Unit tests (local)

## Commits on Branch
- Latest: fix mock tests to stub execute() instead of get()
- `84b50dd` style: fix import ordering for spotless
- `5995f89` feat: add Jackson deserializers for v4 configuration parsing
- `0b45c98` docs: update migration notes with recent fixes
- Earlier commits for initial v4 upgrade
