package ru.kaufd.bodega.domain

import ebt.Ebt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.IOException
import java.nio.file.Paths

/**
 * Свидетельства EBT для REQ-PHOTO-SWEEP (ebt/spec.toml, INV-SWEEP-OBS), уровень наблюдения:
 * запись делает тест — что он подал в подделки и что фактически осталось в хранилище.
 * Ожидаемых значений здесь нет: оракул — независимый чекер ebt/checks.py.
 *
 * Номер перепроверки → путь: хранилище отдаёт файлы в порядке вставки, а в сценариях со сбоем
 * перепроверки все файлы — старые сироты, поэтому k-я перепроверка относится к k-му файлу.
 * Соответствие проверено для текущей реализации уборки; при изменении порядка обхода сценарий
 * требует пересмотра, корректность свидетельств автоматически не гарантируется.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OrphanPhotoSweeperEbtTest {

    private val lease = PhotoLease()

    private fun sweeper(repo: EntryRepository, photos: PhotoStorage) =
        OrphanPhotoSweeper(repo, photos, lease, CoroutineScope(StandardTestDispatcher()))

    @Test
    fun `пустое хранилище`() = runTest {
        scenario("empty", files = emptyList())
    }

    @Test
    fun `давняя сирота`() = runTest {
        scenario("old_orphan", files = listOf(old("photos/orphan.jpg")))
    }

    @Test
    fun `молодая сирота`() = runTest {
        scenario("young_orphan", files = listOf(StoredPhoto("photos/young.jpg", NOW - DAY_MS)))
    }

    @Test
    fun `сирота ровно недельной давности`() = runTest {
        scenario("exact_week", files = listOf(StoredPhoto("photos/week.jpg", NOW - WEEK_MS)))
    }

    @Test
    fun `файл используемой записи`() = runTest {
        scenario("used", files = listOf(old("photos/used.jpg")), usedFirst = setOf("photos/used.jpg"))
    }

    @Test
    fun `арендованный файл`() = runTest {
        scenario("leased", files = listOf(old("photos/leased.jpg")), leased = setOf("photos/leased.jpg"))
    }

    @Test
    fun `отмена удаления между проходами`() = runTest {
        scenario("undo_between_passes", files = listOf(old("photos/undo.jpg")),
            usedRecheck = setOf("photos/undo.jpg"))
    }

    @Test
    fun `первое чтение БД упало`() = runTest {
        scenario("first_read_fails", files = listOf(old("photos/a.jpg"), old("photos/b.jpg")), firstFails = true)
    }

    @Test
    fun `сбой перепроверки первого файла`() = runTest {
        scenario("recheck_fails", files = listOf(old("photos/a.jpg"), old("photos/b.jpg")),
            failRecheckReads = setOf(1))
    }

    @Test
    fun `сбой удаления первого файла`() = runTest {
        scenario("delete_fails", files = listOf(old("photos/a.jpg"), old("photos/b.jpg")),
            failDelete = setOf("photos/a.jpg"))
    }

    @Test
    fun `удаление первого файла бросает исключение`() = runTest {
        scenario("delete_throws", files = listOf(old("photos/a.jpg"), old("photos/b.jpg")),
            throwDelete = setOf("photos/a.jpg"))
    }

    @Test
    fun `смешанное хранилище`() = runTest {
        scenario("mixed",
            files = listOf(old("photos/orphan.jpg"), old("photos/used.jpg"), StoredPhoto("photos/young.jpg", NOW - DAY_MS)),
            usedFirst = setOf("photos/used.jpg"))
    }

    /**
     * Запись удаляется (DeleteEntryUseCase, замок общий с уборкой) одновременно с уборкой:
     * строки в БД уже нет, аренды на время отмены ещё нет. Первый проход уборки видит файл
     * сиротой; решение об удалении под замком должно увидеть аренду. Гонка не допущение:
     * первое чтение фиксирует, что удаление ещё идёт, строки нет и аренды нет, иначе тест падает.
     */
    @Test
    fun `удаление записи во время уборки`() = runTest {
        val path = "photos/entry.jpg"
        val entry = Entry(id = 1, category = Category.WINE, name = "Пино", photos = listOf(path))
        val photos = EbtStorage(listOf(old(path)))
        val gate = CompletableDeferred<Unit>()
        val repo = EbtRepo(used = setOf(path), deleted = entry, deleteGate = gate)
        var leasedAtFirstRead = emptySet<String>()
        var deletionInProgress = false
        repo.onFirstRead = {
            leasedAtFirstRead = setOf(path).filter(lease::isHeld).toSet()
            deletionInProgress = !gate.isCompleted
        }
        val useCase = DeleteEntryUseCase(repo, lease)

        launch { useCase.delete(entry) }
        runCurrent()  // строка удалена, удаление держит замок и ждёт
        var removed = -1
        launch { removed = sweeper(repo, photos).sweepNow(NOW) }
        runCurrent()  // первый проход уже прошёл, уборка ждёт замок
        gate.complete(Unit)
        advanceUntilIdle()

        val raced = deletionInProgress && path !in repo.firstReturned && path !in leasedAtFirstRead
        assertTrue("гонки не было: первое чтение не попало в промежуток удаления", raced)
        record("deleted_during_sweep", photosBefore = listOf(old(path)), usedFirst = repo.firstReturned,
            usedRecheck = repo.recheckReturned, leased = leasedAtFirstRead, deletedDuringSweep = setOf(path),
            firstFails = false, recheckFails = emptyList(), failedDeletes = photos.failedDeletes,
            remaining = photos.paths(), removed = removed)
    }

    private suspend fun scenario(
        name: String,
        files: List<StoredPhoto>,
        usedFirst: Set<String> = emptySet(),
        usedRecheck: Set<String>? = null,
        leased: Set<String> = emptySet(),
        firstFails: Boolean = false,
        failRecheckReads: Set<Int> = emptySet(),
        failDelete: Set<String> = emptySet(),
        throwDelete: Set<String> = emptySet(),
    ) {
        val photos = EbtStorage(files, failDelete, throwDelete)
        val repo = EbtRepo(used = usedFirst, afterFirstRead = usedRecheck, firstFails = firstFails,
            failRecheckReads = failRecheckReads)
        leased.forEach(lease::acquire)
        var leasedAtFirstRead = emptySet<String>()
        repo.onFirstRead = { leasedAtFirstRead = files.map { it.relativePath }.filter(lease::isHeld).toSet() }
        val removed = sweeper(repo, photos).sweepNow(NOW)
        record(name, photosBefore = files, usedFirst = repo.firstReturned,
            usedRecheck = repo.recheckReturned, leased = leasedAtFirstRead, deletedDuringSweep = emptySet(),
            firstFails = firstFails, recheckFails = failRecheckReads.sorted().map { files[it - 1].relativePath },
            failedDeletes = photos.failedDeletes, remaining = photos.paths(), removed = removed)
    }

    private fun record(
        scope: String,
        photosBefore: List<StoredPhoto>,
        usedFirst: Set<String>,
        usedRecheck: Set<String>,
        leased: Set<String>,
        deletedDuringSweep: Set<String>,
        firstFails: Boolean,
        recheckFails: List<String>,
        failedDeletes: Set<String>,
        remaining: List<String>,
        removed: Int,
    ) {
        Ebt.setScope(scope)
        Ebt.ev("INV-SWEEP-OBS", "REQ-PHOTO-SWEEP", true, mapOf(
            "now" to NOW,
            "files" to photosBefore.map { listOf(it.relativePath, it.modifiedAt) },
            "used_first" to usedFirst.sorted(),
            "used_recheck" to usedRecheck.sorted(),
            "leased" to leased.sorted(),
            "deleted_during_sweep" to deletedDuringSweep.sorted(),
            "first_read_fails" to firstFails,
            "recheck_fails" to recheckFails,
            "delete_fails" to failedDeletes.sorted(),
            "remaining" to remaining,
            "removed" to removed,
        ))
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val WEEK_MS = 7 * DAY_MS
        private const val NOW = 100L * DAY_MS

        private fun old(path: String) = StoredPhoto(path, NOW - 30 * DAY_MS)

        @BeforeClass @JvmStatic
        fun startLog() = Ebt.reset()

        // Рабочая папка юнит-тестов — app/; ebt/test.sh копирует файл в OUT_DIR гейта.
        @AfterClass @JvmStatic
        fun writeLog() = Ebt.writeTo(Paths.get("build/ebt/orphan_photo_sweeper.jsonl"))
    }
}

/**
 * Хранилище: файлы в порядке вставки; удаление пути из [failDelete] возвращает false, из
 * [throwDelete] — бросает. [failedDeletes] — пути, удаление которых действительно пытались
 * сделать и оно не удалось: свидетельство берёт наблюдённое, а не настроенное.
 */
private class EbtStorage(
    files: List<StoredPhoto>,
    private val failDelete: Set<String> = emptySet(),
    private val throwDelete: Set<String> = emptySet(),
) : PhotoStorage {
    private val files = LinkedHashMap<String, Long>().apply { files.forEach { put(it.relativePath, it.modifiedAt) } }
    val failedDeletes = mutableSetOf<String>()

    fun paths(): List<String> = files.keys.sorted()

    override suspend fun delete(relativePath: String): Boolean {
        if (relativePath in throwDelete) {
            failedDeletes += relativePath
            throw SecurityException("нет доступа к $relativePath")
        }
        if (relativePath in failDelete) {
            failedDeletes += relativePath
            return false
        }
        files.remove(relativePath)
        return true
    }
    override suspend fun list(): List<StoredPhoto> = files.map { StoredPhoto(it.key, it.value) }
    override fun absolutePath(relativePath: String) = "/abs/$relativePath"
    override fun open(relativePath: String): java.io.InputStream? = null
    override suspend fun store(input: java.io.InputStream): String = error("not used")
    override suspend fun importImage(source: PhotoSource): String = error("not used")
}

/**
 * Репозиторий: первое чтение путей — [used] (или сбой при [firstFails]), после него набор
 * становится [afterFirstRead], если он задан; k-я перепроверка (2-е чтение и далее) падает,
 * если k в [failRecheckReads]. [delete] убирает фото записи [deleted] из используемых и ждёт
 * [deleteGate]. Свидетельство берёт фактически отданное: [firstReturned], [recheckReturned].
 */
private class EbtRepo(
    private var used: Set<String>,
    private val afterFirstRead: Set<String>? = null,
    private val firstFails: Boolean = false,
    private val failRecheckReads: Set<Int> = emptySet(),
    private val deleted: Entry? = null,
    val deleteGate: CompletableDeferred<Unit>? = null,
) : EntryRepository {
    private var reads = 0
    var onFirstRead: () -> Unit = {}
    val firstReturned = mutableSetOf<String>()
    val recheckReturned = mutableSetOf<String>()

    override suspend fun usedPhotoPaths(): List<String> {
        reads++
        if (reads == 1) {
            onFirstRead()
            if (firstFails) throw IOException("БД недоступна")
            val first = used.toList()
            firstReturned += first
            afterFirstRead?.let { used = it }
            return first
        }
        if (reads - 1 in failRecheckReads) throw IOException("БД недоступна")
        return used.toList().also { recheckReturned += it }
    }

    override suspend fun delete(id: Long): Entry? {
        val entry = deleted ?: return null
        used = used - entry.photos.toSet()
        deleteGate?.await()
        return entry
    }

    override fun observeEntries(filter: EntryFilter): Flow<List<Entry>> = flowOf(emptyList())
    override fun observeCount(category: Category?): Flow<Int> = flowOf(0)
    override fun observeById(id: Long): Flow<Entry?> = flowOf(null)
    override suspend fun getById(id: Long): Entry? = null
    override suspend fun upsert(entry: Entry): Long = 0
    override suspend fun insertAll(entries: List<Entry>) {}
    override suspend fun restore(entry: Entry) {}
    override suspend fun purgePhotos(paths: List<String>) = true
    override suspend fun recentNotes(category: Category, limit: Int): List<String> = emptyList()
    override fun observeUsedTypesGrouped(category: Category?): Flow<Map<Category, List<String>>> = flowOf(emptyMap())
    override fun observeUsedCountries(): Flow<List<String>> = flowOf(emptyList())
    override fun observeUsedStyles(): Flow<List<String>> = flowOf(emptyList())
    override fun observeWineFacets(): Flow<WineFacets> = flowOf(WineFacets())
}
