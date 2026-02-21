# Migration Notes: Android SDK v4 Upgrade

## Current Status
- **Phase:** Test Fixes in Progress
- **Branch:** `feature/v4-core-upgrade/pr3-sdk-v4-upgrade`
- **Last Updated:** 2026-02-21

### Recent CI Fixes:
1. ✅ Applied spotless formatting
2. ✅ Migrated tests from reflection to builder-based HTTP client injection
3. ✅ Updated test config JSON format (added banditReferences, format fields)
4. ✅ Added required fields (createdAt, environment) to EMPTY_CONFIG
5. ❌ CI still failing - see blocking issues below

### Blocking Issues:

#### 1. Test Data Files (Upstream Dependency)
The `sdk-test-data` repository's JSON files (flags-v1.json, flags-v1-obfuscated.json) need
`banditReferences` field for v4 parser. These files are downloaded during CI and the fix
needs to happen in the upstream `sdk-test-data` repository.

**Affected tests:** `testObfuscatedOfflineInit`, `testUnobfuscatedOfflineInit`

#### 2. Mock HTTP Client Method Mismatch
The published `eppo-sdk-framework:0.1.0-SNAPSHOT` has a different `EppoConfigurationClient`
interface than expected. Tests mock the `get()` method, but the SDK internally may call
a different method (possibly `execute()`), causing the mock to not intercept requests.

**Root cause:** The common SDK v4 has:
- `execute()` as the primary method
- `get()` as a deprecated default method that delegates to `execute()`

When Mockito mocks `get()`, and the SDK calls `execute()` directly, the mock doesn't work.

**Solution needed:** Either:
- Update the common SDK to publish with consistent interface
- Or change how tests mock the HTTP client (mock both methods, or use spy instead)

**Affected tests:** `testLoadConfiguration`, `testLoadConfigurationAsync`, `testPollingClient`,
and any test that uses mock HTTP responses

## Completed Work

### PR 1: Module Structure Setup ✅
- Branch: `feature/v4-core-upgrade/pr1-module-setup`
- Commit: `e7cc18d`
- Changes:
  - Created `eppo-android-common/` module
  - Updated `settings.gradle`
  - Changed `eppo` artifact ID to `android-sdk-framework`

### PR 2: Define Precomputed Interfaces ✅
- Branch: `feature/v4-core-upgrade/pr2-precomputed-interfaces`
- Commit: `e8965d1`
- Changes:
  - Created `PrecomputedConfigParser<T>` interface
  - Created `PrecomputedParseException`
  - Created `BasePrecomputedClient<T>` abstract class
  - Refactored `EppoPrecomputedClient` to extend base class

## In Progress

### PR 3: Upgrade to Common SDK v4 🔄
- Branch: `feature/v4-core-upgrade/pr3-sdk-v4-upgrade`
- Status: Dependency updated, compilation errors being fixed

#### Compilation Errors to Fix:

1. **Import change:** ✅ Fixed
   - `cloud.eppo.ufc.dto.VariationType` → `cloud.eppo.api.dto.VariationType`

2. **BaseEppoClient constructor signature changed:**
   - Old: 14 parameters (no ConfigurationParser or EppoConfigurationClient)
   - New: 15 parameters (requires ConfigurationParser and EppoConfigurationClient)
   - Need to create/inject OkHttpConfigurationClient and JacksonConfigurationParser

3. **Configuration.Builder API changed:**
   - Old: `new Configuration.Builder(byte[])`
   - New: `new Configuration.Builder(FlagConfigResponse)`
   - Need to parse bytes with `ConfigurationParser.parseFlagConfig()`

4. **Configuration serialization removed:**
   - `configuration.serializeFlagConfigToBytes()` no longer exists
   - Need alternative approach for caching configuration

#### Key Insight:
The v4 framework design requires `ConfigurationParser` and `EppoConfigurationClient` to be injected
at construction time. This is intentional - it enables:
- Framework-only consumers to provide their own HTTP/JSON implementations
- Batteries-included consumers to get OkHttp/Jackson defaults

For PR 3, we need to:
1. Create `OkHttpConfigurationClient` implementing `EppoConfigurationClient`
2. Create `JacksonConfigurationParser` implementing `ConfigurationParser<JsonNode>`
3. Update `EppoClient` constructor to accept and forward these dependencies
4. Update `ConfigurationStore` for v4 caching API

## Files Changed/To Change

### PR 3 Changes:
- `eppo/build.gradle` - Updated dependency to `eppo-sdk-framework:0.1.0-SNAPSHOT`
- `EppoClient.java` - Needs v4 constructor + ConfigurationParser + EppoConfigurationClient
- `ConfigurationStore.java` - Needs v4 Configuration API
- NEW: `OkHttpConfigurationClient.java` - Implement EppoConfigurationClient
- NEW: `JacksonConfigurationParser.java` - Implement ConfigurationParser<JsonNode>

## Blocking Issues

The v4 upgrade is tightly coupled - you can't use `BaseEppoClient<JsonFlagType>` without providing
both `ConfigurationParser<JsonFlagType>` and `EppoConfigurationClient` implementations.

Options:
1. **Complete PR 3+4 together** - Implement both the dependency upgrade and default implementations
2. **Use stub implementations** - Create minimal implementations that forward to existing code
3. **Wait for common SDK** - Ensure `sdk-common-jvm:4.0.0-SNAPSHOT` is available (includes defaults)

## Recommended Approach

Given the tight coupling between PR 3 and PR 4, I recommend:
1. Merge PR 3 and PR 4 into a single PR
2. Create all required implementations in one go
3. This avoids a broken intermediate state

## Next Steps

1. ✅ Create `OkHttpConfigurationClient`
2. ✅ Create `JacksonConfigurationParser`
3. ✅ Update `EppoClient` with new constructor
4. ✅ Update `ConfigurationStore` for v4 API
5. 🔄 Fix Android instrumented tests (see below)

## Test Updates Required

The Android instrumented tests (`androidTest`) require significant updates for v4:

### EppoClientTest.java Issues:

1. **`EppoHttpClient` → `EppoConfigurationClient`**
   - Old interface used callback pattern
   - New interface uses `CompletableFuture<EppoConfigurationResponse>`
   - Method signature changed from `get(url, callback)` to `get(EppoConfigurationRequest)`
   - All mock setups need to be rewritten

2. **`getTypedAssignment` removed**
   - This was an internal protected method
   - Tests that mock this for error handling need a different approach
   - Consider mocking `ConfigurationStore.getConfiguration()` instead

3. **`config.serializeFlagConfigToBytes()` removed**
   - Tests that pre-populate cache need to write raw JSON bytes
   - Use Jackson ObjectMapper to serialize test data

4. **`FlagConfig` and `FlagConfigResponse` are now abstract**
   - Use `FlagConfig.Default` and `FlagConfigResponse.Default` instead
   - Or use Jackson to deserialize from JSON

5. **`ConfigurationStore` constructor changed**
   - Now requires `ConfigurationParser` parameter
   - Tests need to provide `JacksonConfigurationParser`

6. **`Configuration.Builder` changed**
   - No longer accepts `byte[]`
   - Requires `FlagConfigResponse` object

### Files Changed:
- `EppoClientTest.java` - Import updates, mock updates needed
- `AssignmentTestCase.java` - Import update (done)
- `AssignmentTestCaseDeserializer.java` - Import update, helper class added
- `EppoValueDeserializerHelper.java` - New helper to replace removed class
