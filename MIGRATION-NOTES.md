# Migration Notes: Android SDK v4 Upgrade

## Current Status
- **Phase:** Test Fixes in Progress
- **Branch:** `feature/v4-core-upgrade/pr3-sdk-v4-upgrade`
- **PR:** #246 (Draft)
- **Last Updated:** 2026-02-21

### CI Status: PENDING
Pushed fix for JacksonConfigurationParser - deserializing to Default classes instead of using EppoModule.

## Recent Fixes

### Fixed: JacksonConfigurationParser not using EppoModule
The `eppo-sdk-framework` artifact is designed to be dependency-free and doesn't include Jackson deserializers (EppoModule). Fixed by deserializing directly to `FlagConfigResponse.Default` and `BanditParametersResponse.Default` classes instead.

### Fixed: Initial configuration bytes being discarded
The `EppoClient.Builder.initialConfiguration(byte[])` method was not storing the bytes properly. Fixed by adding `initialConfigurationBytes` field and parsing at build time.

## Potential Remaining Issues

### 1. Test Data Files Missing v4 Fields (Upstream)
The `sdk-test-data` repository's JSON files may need `banditReferences` field for v4 parser:
- `flags-v1.json`
- `flags-v1-obfuscated.json`

**Affected tests:** `testOfflineInit`, `testObfuscatedOfflineInit`
**Resolution:** Update files in upstream `sdk-test-data` repository (or local test assets have been updated)

### 2. Real Network Tests
Tests connecting to real test server may time out depending on network conditions.

**Affected tests:** `testAssignments`, `testUnobfuscatedAssignments`, `testCachedConfigurations`

## Completed Work

### Implementation ✅
- Created `OkHttpConfigurationClient` implementing `EppoConfigurationClient`
- Created `JacksonConfigurationParser` implementing `ConfigurationParser<JsonNode>`
- Updated `EppoClient` constructor to accept parser and HTTP client
- Updated `ConfigurationStore` for v4 caching API
- Updated test mocks for v4 API
- Fixed initialConfiguration(byte[]) to properly store and parse bytes
- Fixed JacksonConfigurationParser to deserialize to Default classes

### Commits on Branch
- `5653d42` fix: deserialize flag config to Default implementations
- `9247731` fix: properly store and parse initial configuration bytes
- `c7098ba` revert: revert execute() changes - published SDK only has get()
- `2cee153` docs: document blocking CI issues for v4 upgrade
- Previous commits fixing test format, spotless, etc.

## Next Steps

1. Monitor CI results after latest push
2. Address any remaining test failures
3. Mark PR as ready for review

## Technical Details

### v4 API Changes Applied
1. `EppoConfigurationClient.get()` returns `CompletableFuture<EppoConfigurationResponse>`
2. DTOs are interfaces with `Default` nested classes (use Default for Jackson deserialization)
3. `Configuration.Builder` requires `FlagConfigResponse` not `byte[]`
4. `ConfigurationStore` requires `ConfigurationParser` in constructor
