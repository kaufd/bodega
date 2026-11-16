package ru.kaufd.bodega.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import ru.kaufd.bodega.domain.DocumentStorage
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.domain.Settings
import ru.kaufd.bodega.domain.RecognitionConfig
import ru.kaufd.bodega.domain.ExportBackupUseCase
import ru.kaufd.bodega.domain.ImportBackupUseCase
import ru.kaufd.bodega.domain.PhotoSource
import ru.kaufd.bodega.domain.PhotoStorage
import ru.kaufd.bodega.domain.StoredPhoto
import ru.kaufd.bodega.domain.WineFacets
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private class SettingsFakeRepo(initial: List<Entry> = emptyList()) : EntryRepository {
    val entries = initial.toMutableList()
    override fun observeEntries(filter: EntryFilter): Flow<List<Entry>> = flowOf(entries.toList())
    override suspend fun upsert(entry: Entry): Long {
        entries += entry.copy(id = entries.size + 1L)
        return entries.size.toLong()
    }
    override suspend fun insertAll(entries: List<Entry>) { entries.forEach { upsert(it) } }
    override fun observeCount(category: Category?): Flow<Int> = flowOf(entries.size)
    override fun observeById(id: Long): Flow<Entry?> = flowOf(null)
    override suspend fun getById(id: Long): Entry? = null
    override suspend fun delete(id: Long): Entry? = null
    override suspend fun restore(entry: Entry) {}
    override suspend fun purgePhotos(paths: List<String>) = true
    override suspend fun usedPhotoPaths(): List<String> = emptyList()
    override suspend fun recentNotes(category: Category, limit: Int): List<String> = emptyList()
    override fun observeUsedTypesGrouped(category: Category?): Flow<Map<Category, List<String>>> = flowOf(emptyMap())
    override fun observeUsedCountries(): Flow<List<String>> = flowOf(emptyList())
    override fun observeUsedStyles(): Flow<List<String>> = flowOf(emptyList())
    override fun observeWineFacets(): Flow<WineFacets> = flowOf(WineFacets())
}

private class FakeDocuments(var content: ByteArray = ByteArray(0)) : DocumentStorage {
    var failOnWrite = false
    val written = mutableMapOf<String, ByteArrayOutputStream>()
    override suspend fun read(locator: String): InputStream = ByteArrayInputStream(content)
    override suspend fun write(locator: String): OutputStream {
        if (failOnWrite) throw IOException("нет доступа")
        return ByteArrayOutputStream().also { written[locator] = it }
    }
}

private class SettingsFakePhotos : PhotoStorage {
    override fun open(relativePath: String): InputStream? = null
    override suspend fun store(input: InputStream): String = "photos/new.jpg"
    override suspend fun delete(relativePath: String) = true
    override fun absolutePath(relativePath: String) = "/abs/$relativePath"
    override suspend fun importImage(source: PhotoSource): String = error("not used")
    override suspend fun list(): List<StoredPhoto> = emptyList()
}

private class SettingsFakeStore : SettingsStore {
    override val settings = MutableStateFlow(Settings())
    override suspend fun save(settings: Settings) = Unit
    override val compactList = MutableStateFlow(true)
    override suspend fun saveCompactList(compact: Boolean) = Unit
    override val recognition = MutableStateFlow(RecognitionConfig())
    override suspend fun saveRecognition(config: RecognitionConfig) { recognition.value = config }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repo: EntryRepository = SettingsFakeRepo(),
        documents: FakeDocuments = FakeDocuments(),
        settings: SettingsStore = SettingsFakeStore(),
    ) = SettingsViewModel(
        ExportBackupUseCase(repo, SettingsFakePhotos()),
        ImportBackupUseCase(repo, SettingsFakePhotos()),
        documents,
        settings,
        dispatcher,
    )

    private fun archiveOf(vararg entries: Entry): ByteArray = ByteArrayOutputStream().also { out ->
        kotlinx.coroutines.runBlocking {
            ExportBackupUseCase(SettingsFakeRepo(entries.toList()), SettingsFakePhotos()).invoke(out)
        }
    }.toByteArray()

    private fun wine(name: String, createdAt: Long) =
        Entry(category = Category.WINE, name = name, createdAt = createdAt)

    @Test
    fun `экспорт пишет архив в выбранный документ`() = runTest {
        val documents = FakeDocuments()
        val vm = viewModel(SettingsFakeRepo(listOf(wine("Barolo", 10))), documents)

        vm.export("content://doc")
        advanceUntilIdle()

        assertEquals(BackupResult.Exported, vm.state.value.result)
        assertFalse(vm.state.value.busy)
        assertTrue(documents.written.getValue("content://doc").size() > 0)
    }

    @Test
    fun `импорт сообщает, сколько записей добавлено`() = runTest {
        val vm = viewModel(documents = FakeDocuments(archiveOf(wine("Barolo", 10))))

        vm.import("content://doc")
        advanceUntilIdle()

        assertEquals(BackupResult.Imported(added = 1, skipped = 0), vm.state.value.result)
    }

    @Test
    fun `чужой файл отвергается как неверный формат`() = runTest {
        val vm = viewModel(documents = FakeDocuments(byteArrayOf(1, 2, 3)))

        vm.import("content://doc")
        advanceUntilIdle()

        assertEquals(BackupResult.WrongFormat, vm.state.value.result)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `недоступный файл — отказ, а не падение`() = runTest {
        val vm = viewModel(documents = FakeDocuments().apply { failOnWrite = true })

        vm.export("content://doc")
        advanceUntilIdle()

        assertEquals(BackupResult.Failed, vm.state.value.result)
    }

    @Test
    fun `показанный результат сбрасывается`() = runTest {
        val vm = viewModel()

        vm.export("content://doc")
        advanceUntilIdle()
        vm.onResultShown()

        assertEquals(null, vm.state.value.result)
    }

    /**
     * Индикатор в настройках рисуется по running, а не по «пункт заблокирован»:
     * иначе при восстановлении он крутился и на сохранении копии тоже.
     */
    @Test
    fun `занята видна ровно та операция, которую запустили`() = runTest {
        val vm = viewModel(documents = FakeDocuments(archiveOf(wine("Barolo", 10))))

        vm.import("content://doc")
        assertEquals(BackupOperation.IMPORT, vm.state.value.running)
        assertTrue("пункты должны быть заблокированы оба", vm.state.value.busy)
        advanceUntilIdle()
        assertEquals(null, vm.state.value.running)

        vm.export("content://doc")
        assertEquals(BackupOperation.EXPORT, vm.state.value.running)
        advanceUntilIdle()
        assertEquals(null, vm.state.value.running)
    }

    /**
     * Поле ключа читает состояние: если оно ждёт круга через DataStore, быстрый
     * ввод собирает конфиг от устаревшего значения и символы теряются.
     */
    @Test
    fun `ввод виден в состоянии до записи на диск`() = runTest {
        val store = SettingsFakeStore()
        val vm = viewModel(settings = store)
        advanceUntilIdle()

        vm.onRecognitionChange { it.copy(enabled = true) }
        vm.onRecognitionChange { it.copy(apiKey = "s") }
        vm.onRecognitionChange { it.copy(apiKey = "sk") }

        // Ни одна запись ещё не выполнена, а набранное уже показано целиком.
        assertEquals("", store.recognition.value.apiKey)
        assertTrue(vm.state.value.recognition.enabled)
        assertEquals("sk", vm.state.value.recognition.apiKey)

        advanceUntilIdle()
        assertEquals("sk", store.recognition.value.apiKey)
        assertEquals("sk", vm.state.value.recognition.apiKey)
    }
}
