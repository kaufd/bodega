package ru.kaufd.bodega.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

private class BackupFakeRepo(initial: List<Entry> = emptyList()) : EntryRepository {
    val entries = initial.toMutableList()
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    /** Задан — [insertAll] падает, не вставив ничего: имитация отката транзакции. */
    var insertFailure: Exception? = null

    override fun observeEntries(filter: EntryFilter): Flow<List<Entry>> = flowOf(entries.toList())
    override suspend fun upsert(entry: Entry): Long {
        val id = if (entry.id == 0L) nextId++ else entry.id
        entries += entry.copy(id = id)
        return id
    }

    override suspend fun insertAll(entries: List<Entry>) {
        insertFailure?.let { throw it }
        entries.forEach { upsert(it) }
    }

    override fun observeCount(category: Category?): Flow<Int> = flowOf(entries.size)
    override fun observeById(id: Long): Flow<Entry?> = flowOf(entries.find { it.id == id })
    override suspend fun getById(id: Long): Entry? = entries.find { it.id == id }
    override suspend fun delete(id: Long): Entry? = null
    override suspend fun restore(entry: Entry) {}
    override suspend fun purgePhotos(paths: List<String>) = true
    override suspend fun recentNotes(category: Category, limit: Int): List<String> = emptyList()
    override suspend fun usedPhotoPaths(): List<String> = entries.flatMap { it.photos }
    override fun observeUsedTypesGrouped(category: Category?): Flow<Map<Category, List<String>>> = flowOf(emptyMap())
    override fun observeUsedCountries(): Flow<List<String>> = flowOf(emptyList())
    override fun observeUsedStyles(): Flow<List<String>> = flowOf(emptyList())
    override fun observeWineFacets(): Flow<WineFacets> = flowOf(WineFacets())
}

private class BackupFakePhotos(initial: Map<String, ByteArray> = emptyMap()) : PhotoStorage {
    val files = initial.toMutableMap()
    private var stored = 0

    override fun open(relativePath: String): InputStream? =
        files[relativePath]?.let { ByteArrayInputStream(it) }

    override suspend fun store(input: InputStream): String {
        val path = "photos/stored-${stored++}.jpg"
        files[path] = input.readBytes()
        return path
    }

    override suspend fun delete(relativePath: String): Boolean = files.remove(relativePath) != null
    override fun absolutePath(relativePath: String) = "/abs/$relativePath"
    override suspend fun importImage(source: PhotoSource): String = error("not used")
    override suspend fun list(): List<StoredPhoto> = files.keys.map { StoredPhoto(it, 0) }
}

private fun wine(name: String, createdAt: Long, photos: List<String> = emptyList()) = Entry(
    category = Category.WINE,
    name = name,
    photos = photos,
    createdAt = createdAt,
)

private fun namesInZip(bytes: ByteArray): List<String> =
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry }.map { it.name }.toList()
    }

class BackupTest {

    @Test
    fun `архив содержит манифест и фотографии записей`() = runTest {
        val repo = BackupFakeRepo(listOf(wine("Barolo", createdAt = 10, photos = listOf("photos/a.jpg"))))
        val photos = BackupFakePhotos(mapOf("photos/a.jpg" to byteArrayOf(1, 2, 3)))
        val out = ByteArrayOutputStream()

        ExportBackupUseCase(repo, photos).invoke(out)

        assertEquals(listOf("entries.json", "photos/a.jpg"), namesInZip(out.toByteArray()))
    }

    @Test
    fun `манифест несёт версию формата`() = runTest {
        val out = ByteArrayOutputStream()

        ExportBackupUseCase(BackupFakeRepo(listOf(wine("Barolo", 10))), BackupFakePhotos()).invoke(out)

        val manifest = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            zip.nextEntry
            zip.readBytes().decodeToString()
        }
        assertTrue("нет версии формата: $manifest", manifest.contains("\"version\":2"))
    }

    @Test
    fun `архив более новой версии отклоняется`() = runTest {
        val fromFuture = ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("entries.json"))
                zip.write("""{"version":99,"exportedAt":0,"entries":[]}""".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val failure = runCatching {
            ImportBackupUseCase(BackupFakeRepo(), BackupFakePhotos()).invoke(ByteArrayInputStream(fromFuture))
        }.exceptionOrNull()

        assertTrue("ожидали BackupFormatException, получили $failure", failure is BackupFormatException)
    }

    @Test
    fun `импорт восстанавливает записи и их фотографии`() = runTest {
        val source = BackupFakeRepo(listOf(wine("Barolo", createdAt = 10, photos = listOf("photos/a.jpg"))))
        val sourcePhotos = BackupFakePhotos(mapOf("photos/a.jpg" to byteArrayOf(1, 2, 3)))
        val archive = ByteArrayOutputStream()
            .also { ExportBackupUseCase(source, sourcePhotos).invoke(it) }
            .toByteArray()

        val target = BackupFakeRepo()
        val targetPhotos = BackupFakePhotos()
        val result = ImportBackupUseCase(target, targetPhotos).invoke(ByteArrayInputStream(archive))

        assertEquals(1, result.added)
        val restored = target.entries.single()
        assertEquals("Barolo", restored.name)
        assertEquals(10L, restored.createdAt)
        assertTrue("фото не перенеслось: ${restored.photos}", restored.photos.size == 1)
        assertEquals(
            listOf<Byte>(1, 2, 3),
            targetPhotos.files.getValue(restored.photos.single()).toList(),
        )
    }

    @Test
    fun `повторный импорт не создаёт дублей`() = runTest {
        val source = BackupFakeRepo(listOf(wine("Barolo", createdAt = 10)))
        val archive = ByteArrayOutputStream()
            .also { ExportBackupUseCase(source, BackupFakePhotos()).invoke(it) }
            .toByteArray()

        val target = BackupFakeRepo()
        val useCase = ImportBackupUseCase(target, BackupFakePhotos())
        useCase.invoke(ByteArrayInputStream(archive))
        val second = useCase.invoke(ByteArrayInputStream(archive))

        assertEquals(0, second.added)
        assertEquals(1, second.skipped)
        assertEquals(1, target.entries.size)
    }

    @Test
    fun `запись с тем же именем, но другим временем создания — не дубль`() = runTest {
        val target = BackupFakeRepo(listOf(wine("Barolo", createdAt = 10)))
        val archive = ByteArrayOutputStream()
            .also { ExportBackupUseCase(BackupFakeRepo(listOf(wine("Barolo", createdAt = 20))), BackupFakePhotos()).invoke(it) }
            .toByteArray()

        val result = ImportBackupUseCase(target, BackupFakePhotos()).invoke(ByteArrayInputStream(archive))

        assertEquals(1, result.added)
        assertEquals(2, target.entries.size)
    }

    @Test
    fun `отказ вставки не оставляет ни записей, ни распакованных фотографий`() = runTest {
        val source = BackupFakeRepo(listOf(wine("Barolo", createdAt = 10, photos = listOf("photos/a.jpg"))))
        val archive = ByteArrayOutputStream()
            .also {
                ExportBackupUseCase(source, BackupFakePhotos(mapOf("photos/a.jpg" to byteArrayOf(1, 2, 3)))).invoke(it)
            }
            .toByteArray()

        val target = BackupFakeRepo().apply { insertFailure = IllegalStateException("disk full") }
        val targetPhotos = BackupFakePhotos()
        val failure = runCatching {
            ImportBackupUseCase(target, targetPhotos).invoke(ByteArrayInputStream(archive))
        }.exceptionOrNull()

        assertTrue("ожидали отказ вставки, получили $failure", failure is IllegalStateException)
        assertTrue("в журнале осталась запись: ${target.entries}", target.entries.isEmpty())
        assertTrue("на диске остались снимки: ${targetPhotos.files.keys}", targetPhotos.files.isEmpty())
    }

    @Test
    fun `архив без манифеста отклоняется, база остаётся нетронутой`() = runTest {
        val target = BackupFakeRepo(listOf(wine("Barolo", createdAt = 10)))
        val notABackup = ByteArrayOutputStream().also { out ->
            java.util.zip.ZipOutputStream(out).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("photos/a.jpg"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
        }.toByteArray()

        val failure = runCatching {
            ImportBackupUseCase(target, BackupFakePhotos()).invoke(ByteArrayInputStream(notABackup))
        }.exceptionOrNull()

        assertTrue("ожидали BackupFormatException, получили $failure", failure is BackupFormatException)
        assertEquals(1, target.entries.size)
    }
}
