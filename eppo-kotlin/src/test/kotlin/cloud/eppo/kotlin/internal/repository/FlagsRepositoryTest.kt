package cloud.eppo.kotlin.internal.repository

import androidx.datastore.core.DataStore
import cloud.eppo.ufc.dto.VariationType
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class FlagsRepositoryTest {

    private lateinit var mockDataStore: DataStore<FlagsState?>
    private lateinit var repository: FlagsRepository
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        mockDataStore = mockk(relaxed = true)
        testScope = TestScope()
        repository = FlagsRepository(mockDataStore, testScope)
    }

    @Test
    fun `initial state is null`() = runTest {
        val state = repository.stateFlow.value

        assertThat(state).isNull()
    }

    @Test
    fun `isInitialized returns false when no data`() {
        assertThat(repository.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized returns true after updateFlags`() = runTest {
        val state = createTestState()

        repository.updateFlags(
            flags = state.flags,
            bandits = state.bandits,
            salt = state.salt,
            environment = state.environment,
            createdAt = state.createdAt,
            format = state.format
        )

        assertThat(repository.isInitialized()).isTrue()
    }

    @Test
    fun `updateFlags sets state atomically`() = runTest {
        val testFlag = createTestFlag("test-value")
        val flags = mapOf("hash1" to testFlag)

        repository.updateFlags(
            flags = flags,
            bandits = emptyMap(),
            salt = "test-salt",
            environment = "production",
            createdAt = Instant.now(),
            format = "PRECOMPUTED"
        )

        val state = repository.stateFlow.value
        assertThat(state).isNotNull()
        assertThat(state?.flags).hasSize(1)
        assertThat(state?.salt).isEqualTo("test-salt")
    }

    @Test
    fun `getFlag returns null when not initialized`() {
        val flag = repository.getFlag("any-hash")

        assertThat(flag).isNull()
    }

    @Test
    fun `getFlag returns flag when exists`() = runTest {
        val testFlag = createTestFlag("test-value")
        val flags = mapOf("hash-123" to testFlag)

        repository.updateFlags(
            flags = flags,
            bandits = emptyMap(),
            salt = "salt",
            environment = null,
            createdAt = Instant.now(),
            format = "PRECOMPUTED"
        )

        val flag = repository.getFlag("hash-123")

        assertThat(flag).isEqualTo(testFlag)
    }

    @Test
    fun `getFlag returns null when flag not found`() = runTest {
        repository.updateFlags(
            flags = emptyMap(),
            bandits = emptyMap(),
            salt = "salt",
            environment = null,
            createdAt = Instant.now(),
            format = "PRECOMPUTED"
        )

        val flag = repository.getFlag("non-existent")

        assertThat(flag).isNull()
    }

    @Test
    fun `getBandit returns bandit when exists`() = runTest {
        val testBandit = createTestBandit()
        val bandits = mapOf("bandit-hash" to testBandit)

        repository.updateFlags(
            flags = emptyMap(),
            bandits = bandits,
            salt = "salt",
            environment = null,
            createdAt = Instant.now(),
            format = "PRECOMPUTED"
        )

        val bandit = repository.getBandit("bandit-hash")

        assertThat(bandit).isEqualTo(testBandit)
    }

    @Test
    fun `getSalt returns null when not initialized`() {
        assertThat(repository.getSalt()).isNull()
    }

    @Test
    fun `getSalt returns salt after update`() = runTest {
        repository.updateFlags(
            flags = emptyMap(),
            bandits = emptyMap(),
            salt = "my-salt-123",
            environment = null,
            createdAt = Instant.now(),
            format = "PRECOMPUTED"
        )

        assertThat(repository.getSalt()).isEqualTo("my-salt-123")
    }

    @Test
    fun `loadPersistedState loads data from DataStore`() = runTest {
        val persistedState = createTestState()
        coEvery { mockDataStore.data } returns flowOf(persistedState)

        repository.loadPersistedState()

        val state = repository.stateFlow.value
        assertThat(state).isEqualTo(persistedState)
        assertThat(repository.isInitialized()).isTrue()
    }

    @Test
    fun `loadPersistedState handles null data gracefully`() = runTest {
        coEvery { mockDataStore.data } returns flowOf(null)

        repository.loadPersistedState()

        assertThat(repository.isInitialized()).isFalse()
    }

    @Test
    fun `loadPersistedState handles errors gracefully`() = runTest {
        coEvery { mockDataStore.data } throws RuntimeException("Disk error")

        repository.loadPersistedState()

        // Should not throw, just log error
        assertThat(repository.isInitialized()).isFalse()
    }

    @Test
    fun `updateFlags persists to DataStore`() = runTest {
        val updateSlot = slot<suspend (FlagsState?) -> FlagsState?>()
        coEvery { mockDataStore.updateData(capture(updateSlot)) } coAnswers {
            updateSlot.captured(null)
        }

        val testFlag = createTestFlag("value")
        repository.updateFlags(
            flags = mapOf("hash1" to testFlag),
            bandits = emptyMap(),
            salt = "salt",
            environment = "production",
            createdAt = Instant.now(),
            format = "PRECOMPUTED"
        )

        // Give async persistence time to execute
        testScope.testScheduler.advanceUntilIdle()

        coVerify { mockDataStore.updateData(any()) }
    }

    @Test
    fun `updateFlags continues even if persistence fails`() = runTest {
        coEvery { mockDataStore.updateData(any()) } throws RuntimeException("Disk full")

        val testFlag = createTestFlag("value")

        // Should not throw
        repository.updateFlags(
            flags = mapOf("hash1" to testFlag),
            bandits = emptyMap(),
            salt = "salt",
            environment = null,
            createdAt = Instant.now(),
            format = "PRECOMPUTED"
        )

        // In-memory state should still be updated
        assertThat(repository.isInitialized()).isTrue()
        assertThat(repository.getFlag("hash1")).isEqualTo(testFlag)
    }

    @Test
    fun `clear sets state to null`() = runTest {
        repository.updateFlags(
            flags = mapOf("hash1" to createTestFlag("value")),
            bandits = emptyMap(),
            salt = "salt",
            environment = null,
            createdAt = Instant.now(),
            format = "PRECOMPUTED"
        )

        repository.clear()

        assertThat(repository.isInitialized()).isFalse()
        assertThat(repository.stateFlow.value).isNull()
    }

    @Test
    fun `concurrent reads during update see consistent state`() = runTest {
        val initialFlag = createTestFlag("initial")
        repository.updateFlags(
            flags = mapOf("hash1" to initialFlag),
            bandits = emptyMap(),
            salt = "salt1",
            environment = null,
            createdAt = Instant.now(),
            format = "PRECOMPUTED"
        )

        val readResults = mutableListOf<DecodedPrecomputedFlag?>()

        // Launch multiple concurrent readers
        repeat(100) {
            launch {
                readResults.add(repository.getFlag("hash1"))
            }
        }

        // Update while readers are active
        val updatedFlag = createTestFlag("updated")
        repository.updateFlags(
            flags = mapOf("hash1" to updatedFlag),
            bandits = emptyMap(),
            salt = "salt2",
            environment = null,
            createdAt = Instant.now(),
            format = "PRECOMPUTED"
        )

        // All reads should see either initial or updated, never partial/corrupt state
        readResults.forEach { flag ->
            assertThat(flag).isAnyOf(initialFlag, updatedFlag)
        }
    }

    @Test
    fun `stateFlow emits new state on update`() = runTest {
        val states = mutableListOf<FlagsState?>()

        // Collect states
        val job = launch {
            repository.stateFlow.collect { states.add(it) }
        }

        // Initial state
        assertThat(states).hasSize(1)
        assertThat(states[0]).isNull()

        // Update
        repository.updateFlags(
            flags = mapOf("hash1" to createTestFlag("value")),
            bandits = emptyMap(),
            salt = "salt",
            environment = null,
            createdAt = Instant.now(),
            format = "PRECOMPUTED"
        )

        // Should have emitted new state
        assertThat(states).hasSize(2)
        assertThat(states[1]).isNotNull()
        assertThat(states[1]?.flags).hasSize(1)

        job.cancel()
    }

    // Helper functions

    private fun createTestState(): FlagsState {
        return FlagsState(
            flags = mapOf("hash1" to createTestFlag("test")),
            bandits = emptyMap(),
            salt = "test-salt",
            environment = "production",
            createdAt = Instant.now(),
            format = "PRECOMPUTED"
        )
    }

    private fun createTestFlag(value: String): DecodedPrecomputedFlag {
        return DecodedPrecomputedFlag(
            allocationKey = "exp-1",
            variationKey = "treatment",
            variationType = VariationType.STRING,
            variationValue = value,
            extraLogging = null,
            doLog = true
        )
    }

    private fun createTestBandit(): DecodedPrecomputedBandit {
        return DecodedPrecomputedBandit(
            banditKey = "bandit-1",
            action = "action-1",
            modelVersion = "v1.0",
            actionProbability = 0.75,
            optimalityGap = 0.1,
            actionNumericAttributes = mapOf("price" to 9.99),
            actionCategoricalAttributes = mapOf("color" to "red")
        )
    }
}
