package ru.kaufd.bodega.ui.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.DeleteEntryUseCase
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.PhotoLease
import ru.kaufd.bodega.domain.WineFacets

private class FakeRepo : EntryRepository {
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    override fun observeEntries(filter: EntryFilter): Flow<List<Entry>> = flowOf(emptyList())
    override fun observeCount(category: Category?): Flow<Int> = flowOf(0)
    override fun observeById(id: Long): Flow<Entry?> = entries.map { l -> l.find { it.id == id } }
    override suspend fun getById(id: Long): Entry? = entries.value.find { it.id == id }
    override suspend fun upsert(entry: Entry): Long = entry.id
    override suspend fun insertAll(entries: List<Entry>) { entries.forEach { upsert(it) } }
    override suspend fun delete(id: Long): Entry? =
        entries.value.find { it.id == id }?.also { entries.value = entries.value - it }
    override suspend fun restore(entry: Entry) { entries.value = entries.value + entry }
    override suspend fun purgePhotos(paths: List<String>) = true
    override suspend fun recentNotes(category: Category, limit: Int): List<String> = emptyList()
    override suspend fun usedPhotoPaths(): List<String> = entries.value.flatMap { it.photos }
    override fun observeUsedTypesGrouped(category: Category?): Flow<Map<Category, List<String>>> = flowOf(emptyMap())
    override fun observeUsedCountries(): Flow<List<String>> = flowOf(emptyList())
    override fun observeUsedStyles(): Flow<List<String>> = flowOf(emptyList())
    override fun observeWineFacets(): Flow<WineFacets> = flowOf(WineFacets())
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepo

    @Before fun setup() { Dispatchers.setMain(dispatcher); repo = FakeRepo() }
    @After fun tearDown() { Dispatchers.resetMain() }

    private val barolo = Entry(id = 1, category = Category.WINE, name = "Бароло", createdAt = 1)

    private fun vm(repository: EntryRepository = repo) = DetailViewModel(
        savedState = SavedStateHandle(mapOf("id" to 1L)),
        repository = repository,
        deleteUseCase = DeleteEntryUseCase(repository, PhotoLease()),
    )

    @Test
    fun `удаление публикуется состоянием, а не колбэком навигации`() = runTest {
        repo.entries.value = listOf(barolo)
        val vm = vm()
        vm.state.test {
            var s = awaitItem()
            while (s.content !is DetailContent.Ready) s = awaitItem()

            vm.delete()
            // ViewModel переживает пересоздание Activity: признак завершения обязан
            // остаться в состоянии, иначе новый экран замрёт с deleting = true.
            while (!s.deleted) s = awaitItem()
            assertTrue(s.deleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `во время удаления экран заморожен и повторный вызов игнорируется`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow = object : EntryRepository by repo {
            override suspend fun delete(id: Long): Entry? {
                gate.await()
                return repo.delete(id)
            }
        }
        repo.entries.value = listOf(barolo)
        val vm = vm(repository = slow)
        vm.state.test {
            var s = awaitItem()
            while (s.content !is DetailContent.Ready) s = awaitItem()

            vm.delete()
            while (!s.deleting) s = awaitItem()
            assertFalse(s.deleted)

            vm.delete() // повторное подтверждение не должно запускать вторую операцию
            gate.complete(Unit)
            while (!s.deleted) s = awaitItem()

            assertEquals(emptyList<Entry>(), repo.entries.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `отказ Room снимает заморозку и оставляет экран на карточке`() = runTest {
        val failing = object : EntryRepository by repo {
            override suspend fun delete(id: Long): Entry? = error("database is locked")
        }
        repo.entries.value = listOf(barolo)
        val vm = vm(repository = failing)
        vm.state.test {
            var s = awaitItem()
            while (s.content !is DetailContent.Ready) s = awaitItem()

            vm.delete()
            while (!s.deleteFailed) s = awaitItem()

            assertFalse(s.deleting)
            assertFalse(s.deleted)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
