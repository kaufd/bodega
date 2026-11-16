package ru.kaufd.bodega.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val NOW = 100L * DAY_MS

private class SweeperFakeRepo(
    private var used: List<String>,
    private val failure: Throwable? = null,
    /** Вызывается перед каждым чтением путей — точка для имитации гонки. */
    private val onRead: (SweeperFakeRepo) -> Unit = {},
) : EntryRepository {
    var reads = 0
        private set
    fun setUsed(paths: List<String>) { used = paths }
    override fun observeEntries(filter: EntryFilter): Flow<List<Entry>> = flowOf(emptyList())
    override fun observeCount(category: Category?): Flow<Int> = flowOf(0)
    override fun observeById(id: Long): Flow<Entry?> = flowOf(null)
    override suspend fun getById(id: Long): Entry? = null
    override suspend fun upsert(entry: Entry): Long = 0
    override suspend fun insertAll(entries: List<Entry>) {}
    override suspend fun delete(id: Long): Entry? = null
    override suspend fun restore(entry: Entry) {}
    override suspend fun purgePhotos(paths: List<String>) = true
    override suspend fun recentNotes(category: Category, limit: Int): List<String> = emptyList()
    override suspend fun usedPhotoPaths(): List<String> {
        reads++
        onRead(this)
        return failure?.let { throw it } ?: used
    }
    override fun observeUsedTypesGrouped(category: Category?): Flow<Map<Category, List<String>>> = flowOf(emptyMap())
    override fun observeUsedCountries(): Flow<List<String>> = flowOf(emptyList())
    override fun observeUsedStyles(): Flow<List<String>> = flowOf(emptyList())
    override fun observeWineFacets(): Flow<WineFacets> = flowOf(WineFacets())
}

private class SweeperFakePhotoStorage(private val files: List<StoredPhoto>) : PhotoStorage {
    val deleted = mutableListOf<String>()
    var deleteResult = true
    override suspend fun delete(relativePath: String): Boolean {
        deleted += relativePath
        return deleteResult
    }
    override fun absolutePath(relativePath: String) = "/abs/$relativePath"
    override fun open(relativePath: String): java.io.InputStream? = null
    override suspend fun store(input: java.io.InputStream): String = error("not used")
    override suspend fun importImage(source: PhotoSource): String = error("not used")
    override suspend fun list(): List<StoredPhoto> = files
}

@OptIn(ExperimentalCoroutinesApi::class)
class OrphanPhotoSweeperTest {

    private val lease = PhotoLease()

    private fun sweeper(repo: EntryRepository, photos: PhotoStorage) =
        OrphanPhotoSweeper(repo, photos, lease, CoroutineScope(StandardTestDispatcher()))

    @Test
    fun `удаляет давний файл, на который никто не ссылается`() = runTest {
        val photos = SweeperFakePhotoStorage(listOf(StoredPhoto("photos/orphan.jpg", NOW - 30 * DAY_MS)))
        val removed = sweeper(SweeperFakeRepo(used = emptyList()), photos).sweepNow(NOW)

        assertEquals(listOf("photos/orphan.jpg"), photos.deleted)
        assertEquals(1, removed)
    }

    @Test
    fun `не трогает файл используемой записи`() = runTest {
        val photos = SweeperFakePhotoStorage(listOf(StoredPhoto("photos/kept.jpg", NOW - 30 * DAY_MS)))
        sweeper(SweeperFakeRepo(used = listOf("photos/kept.jpg")), photos).sweepNow(NOW)

        assertEquals(emptyList<String>(), photos.deleted)
    }

    @Test
    fun `не трогает свежий файл — это может быть фото открытого черновика`() = runTest {
        val photos = SweeperFakePhotoStorage(listOf(StoredPhoto("photos/staged.jpg", NOW - 60_000)))
        sweeper(SweeperFakeRepo(used = emptyList()), photos).sweepNow(NOW)

        assertEquals(emptyList<String>(), photos.deleted)
    }

    @Test
    fun `при отказе БД не удаляет ничего`() = runTest {
        val photos = SweeperFakePhotoStorage(listOf(StoredPhoto("photos/orphan.jpg", NOW - 30 * DAY_MS)))
        val repo = SweeperFakeRepo(used = emptyList(), failure = IllegalStateException("database is locked"))

        val removed = sweeper(repo, photos).sweepNow(NOW)

        assertEquals(emptyList<String>(), photos.deleted)
        assertEquals(0, removed)
    }

    @Test
    fun `не трогает арендованный файл — черновик редактора или pending undo`() = runTest {
        val photos = SweeperFakePhotoStorage(listOf(StoredPhoto("photos/held.jpg", NOW - 30 * DAY_MS)))
        lease.acquire("photos/held.jpg")

        sweeper(SweeperFakeRepo(used = emptyList()), photos).sweepNow(NOW)

        assertEquals(emptyList<String>(), photos.deleted)
    }

    @Test
    fun `освобождённая аренда снова открывает файл для уборки`() = runTest {
        val photos = SweeperFakePhotoStorage(listOf(StoredPhoto("photos/held.jpg", NOW - 30 * DAY_MS)))
        lease.acquire("photos/held.jpg")
        lease.release("photos/held.jpg")

        sweeper(SweeperFakeRepo(used = emptyList()), photos).sweepNow(NOW)

        assertEquals(listOf("photos/held.jpg"), photos.deleted)
    }

    @Test
    fun `аренда двух держателей снимается только после обоих`() = runTest {
        val photos = SweeperFakePhotoStorage(listOf(StoredPhoto("photos/held.jpg", NOW - 30 * DAY_MS)))
        lease.acquire("photos/held.jpg")
        lease.acquire("photos/held.jpg")
        lease.release("photos/held.jpg")

        sweeper(SweeperFakeRepo(used = emptyList()), photos).sweepNow(NOW)

        assertEquals(emptyList<String>(), photos.deleted)
    }

    @Test
    fun `вернувшаяся в БД запись не теряет фото — принадлежность перепроверяется`() = runTest {
        val photos = SweeperFakePhotoStorage(listOf(StoredPhoto("photos/p.jpg", NOW - 30 * DAY_MS)))
        // Первое чтение застаёт файл осиротевшим, ко второму undo уже вернул запись.
        val repo = SweeperFakeRepo(used = emptyList(), onRead = { r ->
            if (r.reads == 2) r.setUsed(listOf("photos/p.jpg"))
        })

        val removed = sweeper(repo, photos).sweepNow(NOW)

        assertEquals(emptyList<String>(), photos.deleted)
        assertEquals(0, removed)
    }

    @Test
    fun `отказ БД при перепроверке не приводит к удалению`() = runTest {
        val photos = SweeperFakePhotoStorage(listOf(StoredPhoto("photos/p.jpg", NOW - 30 * DAY_MS)))
        val repo = SweeperFakeRepo(used = emptyList(), onRead = { r ->
            if (r.reads == 2) throw IllegalStateException("database is locked")
        })

        assertEquals(0, sweeper(repo, photos).sweepNow(NOW))
        assertEquals(emptyList<String>(), photos.deleted)
    }

    @Test
    fun `sweep запускается один раз за процесс`() = runTest {
        val photos = SweeperFakePhotoStorage(emptyList())
        val repo = SweeperFakeRepo(used = emptyList())
        val dispatcher = StandardTestDispatcher(testScheduler)
        val sweeper = OrphanPhotoSweeper(repo, photos, lease, CoroutineScope(dispatcher))

        // MainActivity пересоздаётся при повороте — параллельных уборок быть не должно.
        sweeper.sweep()
        sweeper.sweep()
        sweeper.sweep()
        testScheduler.advanceUntilIdle()

        assertEquals(1, repo.reads)
    }

    @Test
    fun `неудачное удаление не считается успехом`() = runTest {
        val photos = SweeperFakePhotoStorage(listOf(StoredPhoto("photos/orphan.jpg", NOW - 30 * DAY_MS)))
            .apply { deleteResult = false }

        assertEquals(0, sweeper(SweeperFakeRepo(used = emptyList()), photos).sweepNow(NOW))
    }
}
