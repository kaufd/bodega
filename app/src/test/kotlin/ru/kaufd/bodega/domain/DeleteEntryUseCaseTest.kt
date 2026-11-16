package ru.kaufd.bodega.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRepo : EntryRepository {
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    val purged = mutableListOf<String>()
    val restored = mutableListOf<Entry>()
    var restoreFailure: Throwable? = null
    var purgeFailure: Throwable? = null
    var deleteFailureId: Long? = null
    var beforeDelete: suspend (Long) -> Unit = {}
    override fun observeEntries(filter: EntryFilter): Flow<List<Entry>> = entries
    override fun observeCount(category: Category?): Flow<Int> = flowOf(0)
    override fun observeById(id: Long): Flow<Entry?> = flowOf(null)
    override suspend fun getById(id: Long): Entry? = entries.value.find { it.id == id }
    override suspend fun upsert(entry: Entry): Long = entry.id
    override suspend fun insertAll(entries: List<Entry>) { entries.forEach { upsert(it) } }
    override suspend fun delete(id: Long): Entry? {
        beforeDelete(id)
        if (id == deleteFailureId) error("delete failed")
        val stored = entries.value.find { it.id == id }
        entries.value = entries.value.filterNot { it.id == id }
        return stored
    }
    override suspend fun restore(entry: Entry) {
        restoreFailure?.let { throw it }
        restored += entry
        entries.value = entries.value + entry
    }
    override suspend fun purgePhotos(paths: List<String>): Boolean {
        purgeFailure?.let { throw it }
        purged += paths
        return true
    }
    override suspend fun recentNotes(category: Category, limit: Int): List<String> = emptyList()
    override suspend fun usedPhotoPaths(): List<String> = entries.value.flatMap { it.photos }
    override fun observeUsedTypesGrouped(category: Category?): Flow<Map<Category, List<String>>> = flowOf(emptyMap())
    override fun observeUsedCountries(): Flow<List<String>> = flowOf(emptyList())
    override fun observeUsedStyles(): Flow<List<String>> = flowOf(emptyList())
    override fun observeWineFacets(): Flow<WineFacets> = flowOf(WineFacets())
}

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteEntryUseCaseTest {

    private val lease = PhotoLease()
    private val a = Entry(id = 1, category = Category.WINE, name = "A", photos = listOf("photos/a.jpg"))
    private val b = Entry(id = 2, category = Category.WINE, name = "B", photos = listOf("photos/b.jpg"))

    @Test
    fun `удаление второй записи очищает фото первой pending`() = runTest {
        val repo = FakeRepo()
        repo.entries.value = listOf(a, b)
        val uc = DeleteEntryUseCase(repo, lease)
        uc.delete(a)
        assertEquals(emptyList<String>(), repo.purged) // фото A ещё не тронуто (undo активно)
        uc.delete(b)
        assertEquals(listOf("photos/a.jpg"), repo.purged) // фото A очищено при вытеснении
        assertEquals(b, uc.pending.value)
    }

    @Test
    fun `undo восстанавливает и не чистит фото`() = runTest {
        val repo = FakeRepo()
        repo.entries.value = listOf(a, b)
        val uc = DeleteEntryUseCase(repo, lease)
        uc.delete(a)
        uc.undo()
        assertEquals(listOf(a), repo.restored)
        assertEquals(emptyList<String>(), repo.purged)
        assertNull(uc.pending.value)
    }

    @Test
    fun `expire чистит фото pending-записи`() = runTest {
        val repo = FakeRepo()
        repo.entries.value = listOf(a, b)
        val uc = DeleteEntryUseCase(repo, lease)
        uc.delete(a)
        uc.expire()
        assertEquals(listOf("photos/a.jpg"), repo.purged)
        assertNull(uc.pending.value)
    }

    @Test
    fun `expire чистит все фотографии записи, а не только обложку`() = runTest {
        val repo = FakeRepo()
        val many = a.copy(photos = listOf("photos/a1.jpg", "photos/a2.jpg", "photos/a3.jpg"))
        repo.entries.value = listOf(many)
        val uc = DeleteEntryUseCase(repo, lease)

        uc.delete(many)
        uc.expire()

        assertEquals(listOf("photos/a1.jpg", "photos/a2.jpg", "photos/a3.jpg"), repo.purged)
    }

    @Test
    fun `pending держит аренду на всех фотографиях записи`() = runTest {
        val repo = FakeRepo()
        val many = a.copy(photos = listOf("photos/a1.jpg", "photos/a2.jpg"))
        repo.entries.value = listOf(many)
        val uc = DeleteEntryUseCase(repo, lease)

        uc.delete(many)
        assertTrue(lease.isHeld("photos/a1.jpg"))
        assertTrue(lease.isHeld("photos/a2.jpg"))

        uc.undo()
        assertFalse(lease.isHeld("photos/a1.jpg"))
        assertFalse(lease.isHeld("photos/a2.jpg"))
    }

    @Test
    fun `вытеснение прежней pending чистит все её фотографии`() = runTest {
        val repo = FakeRepo()
        val many = a.copy(photos = listOf("photos/a1.jpg", "photos/a2.jpg"))
        repo.entries.value = listOf(many, b)
        val uc = DeleteEntryUseCase(repo, lease)

        uc.delete(many)
        uc.delete(b)

        assertEquals(listOf("photos/a1.jpg", "photos/a2.jpg"), repo.purged)
    }

    @Test
    fun `ошибка restore сохраняет pending для повторного undo`() = runTest {
        val repo = FakeRepo()
        repo.entries.value = listOf(a, b)
        val uc = DeleteEntryUseCase(repo, lease)
        uc.delete(a)
        repo.restoreFailure = IllegalStateException("restore failed")

        val failure = runCatching { uc.undo() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(a, uc.pending.value)
    }

    @Test
    fun `ошибка expire сохраняет pending для повторной очистки`() = runTest {
        val repo = FakeRepo()
        repo.entries.value = listOf(a, b)
        val uc = DeleteEntryUseCase(repo, lease)
        uc.delete(a)
        repo.purgeFailure = IllegalStateException("purge failed")

        val failure = runCatching { uc.expire() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(a, uc.pending.value)
    }

    @Test
    fun `ошибка нового delete сохраняет прежний pending нетронутым`() = runTest {
        val repo = FakeRepo()
        repo.entries.value = listOf(a, b)
        val uc = DeleteEntryUseCase(repo, lease)
        uc.delete(a)
        repo.deleteFailureId = b.id

        val failure = runCatching { uc.delete(b) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        // Прежняя pending вытесняется только после удавшегося удаления новой строки.
        // Иначе отказ забирал бы с собой и чужой undo: фото A уже стёрты, а pending
        // приходится снимать, потому что восстанавливать A стало не с чем.
        assertEquals(emptyList<String>(), repo.purged)
        assertEquals(a, uc.pending.value)
    }

    @Test
    fun `удаление уже исчезнувшей строки сохраняет прежний pending`() = runTest {
        val repo = FakeRepo()
        repo.entries.value = listOf(a)
        val uc = DeleteEntryUseCase(repo, lease)
        uc.delete(a)

        // B в базе нет: экран мог показывать список, устаревший на одно удаление.
        uc.delete(b)

        assertEquals(emptyList<String>(), repo.purged)
        assertEquals(a, uc.pending.value)
    }

    @Test
    fun `undo ждёт незавершённый delete`() = runTest {
        val repo = FakeRepo()
        repo.entries.value = listOf(a, b)
        val deleteStarted = CompletableDeferred<Unit>()
        val continueDelete = CompletableDeferred<Unit>()
        repo.beforeDelete = {
            deleteStarted.complete(Unit)
            continueDelete.await()
        }
        val uc = DeleteEntryUseCase(repo, lease)

        val deleteJob = launch { uc.delete(a) }
        deleteStarted.await()
        val undoJob = launch { uc.undo() }
        runCurrent()
        continueDelete.complete(Unit)
        deleteJob.join()
        undoJob.join()

        assertEquals(listOf(a), repo.restored)
        assertNull(uc.pending.value)
    }

    @Test
    fun `undo восстанавливает строку из БД, а не устаревший UI-снимок`() = runTest {
        // Экран прочитал запись, её отредактировали, и только потом нажали «удалить»:
        // восстановить нужно актуальную версию, иначе правки и новое фото пропадут.
        val repo = FakeRepo()
        val fresh = a.copy(name = "A (переименовано)", photos = listOf("photos/new.jpg"))
        repo.entries.value = listOf(fresh)
        val uc = DeleteEntryUseCase(repo, lease)

        uc.delete(a) // устаревший снимок из UI

        assertEquals(fresh, uc.pending.value)
        uc.undo()
        assertEquals(listOf(fresh), repo.restored)
    }

    @Test
    fun `удаление отсутствующей строки не создаёт pending`() = runTest {
        val repo = FakeRepo()
        val uc = DeleteEntryUseCase(repo, lease)

        uc.delete(a)

        assertNull(uc.pending.value)
    }

    @Test
    fun `pending держит аренду на фото, undo и истечение её снимают`() = runTest {
        val repo = FakeRepo()
        repo.entries.value = listOf(a)
        val uc = DeleteEntryUseCase(repo, lease)

        uc.delete(a)
        // Строки в БД уже нет: без аренды уборка сочла бы файл осиротевшим,
        // и undo вернул бы запись без фотографии.
        assertTrue(lease.isHeld("photos/a.jpg"))

        uc.undo()
        assertFalse(lease.isHeld("photos/a.jpg"))
    }

    @Test
    fun `истечение undo снимает аренду вместе с файлом`() = runTest {
        val repo = FakeRepo()
        repo.entries.value = listOf(a)
        val uc = DeleteEntryUseCase(repo, lease)

        uc.delete(a)
        uc.expire()

        assertFalse(lease.isHeld("photos/a.jpg"))
        assertEquals(listOf("photos/a.jpg"), repo.purged)
    }


    @Test
    fun `отмена вызывающей корутины не разрывает переход владения`() = runTest {
        // Room успел удалить строку и приостановился до того, как появились
        // pending и аренда, — ровно то окно, в котором отменяется экран.
        val gate = CompletableDeferred<Unit>()
        val repo = FakeRepo()
        repo.entries.value = listOf(a)
        val slow = object : EntryRepository by repo {
            override suspend fun delete(id: Long): Entry? {
                val removed = repo.delete(id)
                gate.await()
                return removed
            }
        }
        val uc = DeleteEntryUseCase(slow, lease)

        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        scope.launch { uc.delete(a) }
        testScheduler.runCurrent()

        // Экран закрывается сразу после подтверждения удаления.
        scope.cancel()
        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Строки в БД уже нет — значит pending и аренда обязаны существовать,
        // иначе undo потерян, а фото осиротело.
        assertEquals(a, uc.pending.value)
        assertTrue(lease.isHeld("photos/a.jpg"))
    }

}
