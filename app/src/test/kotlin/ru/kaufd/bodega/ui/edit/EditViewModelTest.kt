package ru.kaufd.bodega.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.DraftWriter
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryLimits
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.EntryViolation
import ru.kaufd.bodega.domain.PhotoCleaner
import ru.kaufd.bodega.domain.PhotoLease
import ru.kaufd.bodega.domain.PhotoSource
import ru.kaufd.bodega.domain.PhotoStorage
import ru.kaufd.bodega.domain.RecognitionConfig
import ru.kaufd.bodega.domain.Settings
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.domain.StoredPhoto

private class FakeRepo : EntryRepository {
    val store = MutableStateFlow<Map<Long, Entry>>(emptyMap())
    var nextId = 1L
    override fun observeEntries(filter: EntryFilter): Flow<List<Entry>> = flowOf(emptyList())
    override fun observeCount(category: Category?): Flow<Int> = flowOf(0)
    override suspend fun getById(id: Long) = store.value[id]
    override fun observeById(id: Long): Flow<Entry?> = store.map { m -> m[id] }
    override suspend fun upsert(entry: Entry): Long {
        val id = if (entry.id == 0L) nextId++ else entry.id
        store.value = store.value + (id to entry.copy(id = id))
        return id
    }
    override suspend fun insertAll(entries: List<Entry>) { entries.forEach { upsert(it) } }
    override suspend fun delete(id: Long): Entry? =
        store.value[id]?.also { store.value = store.value - id }
    override suspend fun restore(entry: Entry) { store.value = store.value + (entry.id to entry) }
    override suspend fun purgePhotos(paths: List<String>) = true
    override suspend fun recentNotes(category: Category, limit: Int): List<String> =
        store.value.values
            .filter { it.category == category && !it.note.isNullOrBlank() }
            .sortedByDescending { it.createdAt }
            .take(limit)
            .map { it.note!! }
    override suspend fun usedPhotoPaths(): List<String> =
        store.value.values.flatMap { it.photos }
    override fun observeUsedTypesGrouped(category: Category?): Flow<Map<Category, List<String>>> = flowOf(emptyMap())
    override fun observeUsedCountries(): Flow<List<String>> = flowOf(emptyList())
    override fun observeUsedStyles(): Flow<List<String>> = flowOf(emptyList())
    override fun observeWineFacets() = flowOf(ru.kaufd.bodega.domain.WineFacets())
}

/**
 * Базовый фейк хранилища. Варианты ниже отличаются только поведением импорта,
 * поэтому переопределяют один метод, а не переписывают все пять заглушек.
 */
private open class FakePhotoStorage : PhotoStorage {
    val imported = mutableListOf<PhotoSource>()
    val deleted = mutableListOf<String>()
    private var counter = 0

    override suspend fun delete(relativePath: String): Boolean { deleted += relativePath; return true }
    override fun absolutePath(relativePath: String) = "/abs/$relativePath"
    override fun open(relativePath: String): java.io.InputStream? = null
    override suspend fun store(input: java.io.InputStream): String = error("not used")
    override suspend fun list(): List<StoredPhoto> = emptyList()
    /** Локаторы, на которых импорт падает, — для проверки частичного сбоя пачки. */
    val failLocators = mutableSetOf<String>()

    override suspend fun importImage(source: PhotoSource): String {
        if (source.locator in failLocators) error("decode failed")
        imported += source
        return "photos/imported${++counter}.jpg"
    }
}

/** Импорт зависает до открытия «ворот» — для проверки порядка завершения. */
private class SlowPhotoStorage : FakePhotoStorage() {
    val gates = mutableListOf<CompletableDeferred<Unit>>()

    override suspend fun importImage(source: PhotoSource): String {
        val gate = CompletableDeferred<Unit>()
        val index = gates.size
        gates += gate
        gate.await()
        return "photos/slow$index.jpg"
    }
}

private class ThrowingPhotoStorage : FakePhotoStorage() {
    override suspend fun importImage(source: PhotoSource): String = error("decode failed")
}


/** Настройки с настроенным облаком: черновик доступен, остальное экрану не нужно. */
private class EditFakeSettings(
    config: RecognitionConfig = RecognitionConfig(
        enabled = true, apiKey = "sk-or-v1", model = "vendor/model",
    ),
) : SettingsStore {
    override val recognition = MutableStateFlow(config)
    override suspend fun saveRecognition(config: RecognitionConfig) { recognition.value = config }

    override val settings: Flow<Settings> = MutableStateFlow(Settings())
    override suspend fun save(settings: Settings) = Unit
    override val compactList: Flow<Boolean> = MutableStateFlow(true)
    override suspend fun saveCompactList(compact: Boolean) = Unit
}

private class EditFakeDraftWriter(var result: String = "") : DraftWriter {
    var calls = 0
    var seenExamples: List<String> = emptyList()
    override suspend fun draftNote(entry: Entry, examples: List<String>): String {
        calls++
        seenExamples = examples
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val lease = PhotoLease()
    private lateinit var repo: FakeRepo

    @Before fun setup() { Dispatchers.setMain(dispatcher); repo = FakeRepo() }
    @After fun tearDown() { Dispatchers.resetMain() }

    /**
     * VM, помещённая в собственный ViewModelStore: `store.clear()` доводит её до
     * onCleared() так же, как это делает уход с экрана.
     */
    private fun scopedVm(
        savedState: SavedStateHandle = SavedStateHandle(),
        photoStorage: PhotoStorage = FakePhotoStorage(),
    ): Pair<EditViewModel, ViewModelStore> {
        val store = ViewModelStore()
        val factory = viewModelFactory {
            initializer { vm(savedState = savedState, photoStorage = photoStorage) }
        }
        return ViewModelProvider(store, factory)[EditViewModel::class.java] to store
    }

    private fun vm(
        id: Long? = null,
        photoStorage: PhotoStorage = FakePhotoStorage(),
        repository: EntryRepository = repo,
        savedState: SavedStateHandle = SavedStateHandle(mapOf("id" to id?.toString())),
        draftWriter: DraftWriter = EditFakeDraftWriter(),
        settings: SettingsStore = EditFakeSettings(),
    ) = EditViewModel(
        savedState = savedState,
        repository = repository,
        photoStorage = photoStorage,
        photoCleaner = PhotoCleaner(photoStorage, CoroutineScope(dispatcher)),
        photoLease = lease,
        draftWriter = draftWriter,
        settings = settings,
    )

    @Test
    fun `выбранный PhotoSource импортируется через порт`() = runTest {
        val photos = FakePhotoStorage()
        val vm = vm(photoStorage = photos)
        val source = PhotoSource("content://picker/photo")

        vm.onPhotosPicked(listOf(source))
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(source), photos.imported)
        assertEquals("photos/imported1.jpg", vm.state.value.entry.photos.firstOrNull())
    }

    @Test
    fun `сбой импорта не роняет VM, эмитит photoError и не меняет фотографии`() = runTest {
        val vm = vm(photoStorage = ThrowingPhotoStorage())
        val errors = mutableListOf<Unit>()
        val job = launch { vm.photoError.collect { errors += it } }
        runCurrent()

        vm.onPhotosPicked(listOf(PhotoSource("content://picker/bad")))
        advanceUntilIdle()

        assertEquals(1, errors.size)
        assertNull(vm.state.value.entry.photos.firstOrNull())
        job.cancel()
    }

    @Test
    fun `новая запись - категория WINE по умолчанию, сохранять нельзя без имени`() = runTest {
        val vm = vm()
        dispatcher.scheduler.runCurrent()
        val s = vm.state.value
        assertTrue(s.isNew)
        assertEquals(Category.WINE, s.entry.category)
        assertFalse(s.entry.name.isNotBlank())
    }

    @Test
    fun `новый рецепт не задаёт дату дегустации`() = runTest {
        val recipe = vm(savedState = SavedStateHandle(mapOf("category" to "COCKTAIL")))
        dispatcher.scheduler.runCurrent()
        assertNull(recipe.state.value.entry.tastedAt)
        val drink = vm()
        dispatcher.scheduler.runCurrent()
        assertTrue(drink.state.value.entry.tastedAt != null)
    }

    @Test
    fun `редактирование - загружает запись по id`() = runTest {
        repo.upsert(Entry(category = Category.BEER, name = "Гиннесс", createdAt = 1))
        val vm = vm(id = 1)
        dispatcher.scheduler.runCurrent()
        assertEquals("Гиннесс", vm.state.value.entry.name)
        assertFalse(vm.state.value.isNew)
    }

    @Test
    fun `смена категории очищает категорийные поля, общие сохраняет`() = runTest {
        val vm = vm()
        dispatcher.scheduler.runCurrent()
        vm.onField { it.copy(name = "X", grapes = listOf("Неббиоло"), country = "Италия", priceRub = 500) }
        vm.onCategoryChange(Category.BEER)
        val e = vm.state.value.entry
        assertEquals("X", e.name)
        assertEquals(500, e.priceRub)
        assertTrue(e.grapes.isEmpty()); assertNull(e.country); assertNull(e.vintageYear)
        assertEquals(Category.BEER, e.category)
    }

    @Test
    fun `save проставляет createdAt для новой записи и помечает состояние сохранённым`() = runTest {
        val vm = vm()
        dispatcher.scheduler.runCurrent()
        vm.onField { it.copy(name = "Бароло") }
        vm.save()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.saved)
        val saved = repo.store.value.values.single()
        assertEquals("Бароло", saved.name)
        assertTrue(saved.createdAt > 0)
    }

    @Test
    fun `dirty становится true после правки`() = runTest {
        val vm = vm()
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.dirty)
        vm.onField { it.copy(name = "Y") }
        assertTrue(vm.state.value.dirty)
    }

    @Test
    fun `крепость вне границ блокирует сохранение вместо падения`() = runTest {
        val vm = vm()
        dispatcher.scheduler.runCurrent()
        vm.onField { it.copy(name = "X", abvPercent = 97.0) }

        assertTrue(EntryViolation.ABV in vm.state.value.violations)
        assertFalse(vm.state.value.canSave)

        vm.save()
        advanceUntilIdle()
        assertFalse(vm.state.value.saved)
        assertTrue(repo.store.value.isEmpty())
    }

    @Test
    fun `отказ репозитория снимает saving и показывает ошибку`() = runTest {
        val failing = object : EntryRepository by repo {
            override suspend fun upsert(entry: Entry): Long = error("disk full")
        }
        val vm = vm(repository = failing)
        dispatcher.scheduler.runCurrent()
        vm.onField { it.copy(name = "Бароло") }

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.state.value.saved)
        assertFalse(vm.state.value.saving)
        assertTrue(vm.state.value.saveFailed)
        assertTrue(vm.state.value.canSave) // повтор возможен
    }

    @Test
    fun `до загрузки записи форма недоступна и сохранение не создаёт дубль`() = runTest {
        repo.upsert(Entry(category = Category.BEER, name = "Гиннесс", createdAt = 1))
        val vm = vm(id = 1)

        // Диспетчер ещё не отработал — запись не прочитана.
        assertEquals(EditPhase.LOADING, vm.state.value.phase)
        assertFalse(vm.state.value.canSave)
        vm.onField { it.copy(name = "Ранний ввод") }
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(EditPhase.READY, vm.state.value.phase)
        assertEquals("Гиннесс", vm.state.value.entry.name)
        assertEquals(1, repo.store.value.size)
    }

    @Test
    fun `отсутствующая запись даёт NOT_FOUND`() = runTest {
        val vm = vm(id = 42)
        advanceUntilIdle()
        assertEquals(EditPhase.NOT_FOUND, vm.state.value.phase)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `новый выбор добавляется к прежним фотографиям, а не заменяет их`() = runTest {
        val photos = FakePhotoStorage()
        val vm = vm(photoStorage = photos)
        dispatcher.scheduler.runCurrent()

        vm.onPhotosPicked(listOf(PhotoSource("content://1")))
        advanceUntilIdle()
        vm.onPhotosPicked(listOf(PhotoSource("content://2")))
        advanceUntilIdle()

        assertEquals(
            listOf("photos/imported1.jpg", "photos/imported2.jpg"),
            vm.state.value.entry.photos,
        )
        assertEquals(emptyList<String>(), photos.deleted)
    }

    @Test
    fun `выбор пачки добавляет все фотографии по порядку`() = runTest {
        val photos = FakePhotoStorage()
        val vm = vm(photoStorage = photos)
        dispatcher.scheduler.runCurrent()

        vm.onPhotosPicked(listOf(PhotoSource("content://1"), PhotoSource("content://2")))
        advanceUntilIdle()

        assertEquals(
            listOf("photos/imported1.jpg", "photos/imported2.jpg"),
            vm.state.value.entry.photos,
        )
        assertFalse(vm.state.value.photoImporting)
    }

    @Test
    fun `сверх лимита фотографии не импортируются вовсе`() = runTest {
        val photos = FakePhotoStorage()
        val vm = vm(photoStorage = photos)
        dispatcher.scheduler.runCurrent()

        vm.onPhotosPicked(List(EntryLimits.PHOTOS_MAX + 1) { PhotoSource("content://$it") })
        advanceUntilIdle()

        assertEquals(EntryLimits.PHOTOS_MAX, vm.state.value.entry.photos.size)
        // Лишний источник не читается: импорт дорогой, а место в записи уже кончилось.
        assertEquals(EntryLimits.PHOTOS_MAX, photos.imported.size)
    }

    @Test
    fun `сбой импорта одной фотографии не теряет остальные`() = runTest {
        val photos = FakePhotoStorage().apply { failLocators += "content://bad" }
        val vm = vm(photoStorage = photos)
        val errors = mutableListOf<Unit>()
        val job = launch { vm.photoError.collect { errors += it } }
        runCurrent()

        vm.onPhotosPicked(listOf(PhotoSource("content://bad"), PhotoSource("content://good")))
        advanceUntilIdle()

        assertEquals(listOf("photos/imported1.jpg"), vm.state.value.entry.photos)
        assertEquals(1, errors.size)
        job.cancel()
    }

    @Test
    fun `удаление несохранённой фотографии чистит её файл`() = runTest {
        val photos = FakePhotoStorage()
        val vm = vm(photoStorage = photos)
        dispatcher.scheduler.runCurrent()
        vm.onPhotosPicked(listOf(PhotoSource("content://1"), PhotoSource("content://2")))
        advanceUntilIdle()

        vm.onPhotoRemove("photos/imported1.jpg")
        advanceUntilIdle()

        assertEquals(listOf("photos/imported2.jpg"), vm.state.value.entry.photos)
        assertEquals(listOf("photos/imported1.jpg"), photos.deleted)
    }

    @Test
    fun `удаление сохранённой фотографии не трогает файл до сохранения`() = runTest {
        repo.upsert(
            Entry(
                category = Category.WINE, name = "Бароло",
                photos = listOf("photos/old1.jpg", "photos/old2.jpg"), createdAt = 1,
            )
        )
        val photos = FakePhotoStorage()
        val vm = vm(id = 1, photoStorage = photos)
        advanceUntilIdle()

        vm.onPhotoRemove("photos/old1.jpg")
        advanceUntilIdle()

        assertEquals(listOf("photos/old2.jpg"), vm.state.value.entry.photos)
        // Отмена редактирования должна вернуть фотографию записи — файл ещё нужен.
        assertEquals(emptyList<String>(), photos.deleted)
    }

    @Test
    fun `смена обложки переносит фотографию в начало и не трогает файлы`() = runTest {
        val photos = FakePhotoStorage()
        val vm = vm(photoStorage = photos)
        dispatcher.scheduler.runCurrent()
        vm.onPhotosPicked(listOf(PhotoSource("content://1"), PhotoSource("content://2")))
        advanceUntilIdle()

        vm.onPhotoMakeCover("photos/imported2.jpg")

        assertEquals(
            listOf("photos/imported2.jpg", "photos/imported1.jpg"),
            vm.state.value.entry.photos,
        )
        assertEquals(emptyList<String>(), photos.deleted)
    }

    @Test
    fun `сохранение чистит только выпавшие из записи фотографии`() = runTest {
        repo.upsert(
            Entry(
                category = Category.WINE, name = "Бароло",
                photos = listOf("photos/old1.jpg", "photos/old2.jpg"), createdAt = 1,
            )
        )
        val photos = FakePhotoStorage()
        val vm = vm(id = 1, photoStorage = photos)
        advanceUntilIdle()

        vm.onPhotoRemove("photos/old1.jpg")
        vm.onPhotosPicked(listOf(PhotoSource("content://new")))
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("photos/old1.jpg"), photos.deleted)
        assertEquals(
            listOf("photos/old2.jpg", "photos/imported1.jpg"),
            repo.store.value.getValue(1L).photos,
        )
    }

    @Test
    fun `отмена чистит только несохранённые фотографии`() = runTest {
        repo.upsert(
            Entry(category = Category.WINE, name = "Бароло", photos = listOf("photos/old.jpg"), createdAt = 1)
        )
        val photos = FakePhotoStorage()
        val vm = vm(id = 1, photoStorage = photos)
        advanceUntilIdle()
        vm.onPhotosPicked(listOf(PhotoSource("content://new")))
        advanceUntilIdle()

        vm.discard()
        advanceUntilIdle()

        assertEquals(listOf("photos/imported1.jpg"), photos.deleted)
    }

    @Test
    fun `во время импорта добавление фотографий не принимается`() = runTest {
        val photos = SlowPhotoStorage()
        val vm = vm(photoStorage = photos)
        dispatcher.scheduler.runCurrent()

        vm.onPhotosPicked(listOf(PhotoSource("content://1")))
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.photoImporting)
        vm.onPhotosPicked(listOf(PhotoSource("content://2")))
        dispatcher.scheduler.runCurrent()

        // Второй выбор отброшен целиком — читается только первый источник.
        assertEquals(1, photos.gates.size)
        photos.gates[0].complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("photos/slow0.jpg"), vm.state.value.entry.photos)
        assertFalse(vm.state.value.photoImporting)
    }

    @Test
    fun `отмена редактора удаляет импортированное, но не сохранённое фото`() = runTest {
        val photos = FakePhotoStorage()
        val vm = vm(photoStorage = photos)
        dispatcher.scheduler.runCurrent()
        vm.onPhotosPicked(listOf(PhotoSource("content://1")))
        advanceUntilIdle()

        vm.discard()
        advanceUntilIdle()

        assertEquals(listOf("photos/imported1.jpg"), photos.deleted)
    }

    @Test
    fun `уход из редактора во время сохранения не удаляет фото записи`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slowRepo = object : EntryRepository by repo {
            override suspend fun upsert(entry: Entry): Long {
                gate.await()
                return repo.upsert(entry)
            }
        }
        val photos = FakePhotoStorage()
        val vm = vm(photoStorage = photos, repository = slowRepo)
        runCurrent()
        vm.onField { it.copy(name = "Бароло") }
        vm.onPhotosPicked(listOf(PhotoSource("content://1")))
        advanceUntilIdle()

        vm.save()
        runCurrent()
        // Пользователь жмёт «Отмена», пока upsert ещё в полёте.
        vm.discard()
        gate.complete(Unit)
        advanceUntilIdle()

        // Фото сохранённой записи должно остаться на диске.
        assertEquals(emptyList<String>(), photos.deleted)
        assertEquals("photos/imported1.jpg", repo.store.value.values.single().photos.firstOrNull())
    }

    @Test
    fun `во время сохранения форма не принимает правки`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slowRepo = object : EntryRepository by repo {
            override suspend fun upsert(entry: Entry): Long {
                gate.await()
                return repo.upsert(entry)
            }
        }
        val vm = vm(repository = slowRepo)
        runCurrent()
        vm.onField { it.copy(name = "Бароло") }

        vm.save()
        runCurrent()
        vm.onField { it.copy(name = "Изменено после Save") }
        gate.complete(Unit)
        advanceUntilIdle()

        // Иначе правка визуально принималась бы, а в БД ушёл бы прежний снимок.
        assertEquals("Бароло", vm.state.value.entry.name)
        assertEquals("Бароло", repo.store.value.values.single().name)
    }

    @Test
    fun `нераспарсенная крепость блокирует сохранение и не стирает прежнее значение`() = runTest {
        repo.upsert(Entry(category = Category.WINE, name = "Бароло", abvPercent = 14.0, createdAt = 1))
        val vm = vm(id = 1)
        advanceUntilIdle()
        assertEquals("14.0", vm.state.value.abvText)

        vm.onAbvInput(".")

        assertTrue(vm.state.value.abvUnparsed)
        assertFalse(vm.state.value.canSave)
        vm.save()
        advanceUntilIdle()
        assertEquals(14.0, repo.store.value.getValue(1L).abvPercent!!, 0.0)
    }

    @Test
    fun `частичный ввод крепости сохраняется как есть, а запятая разбирается`() = runTest {
        val vm = vm()
        runCurrent()
        vm.onField { it.copy(name = "X") }

        vm.onAbvInput("13,")
        assertEquals("13,", vm.state.value.abvText)
        assertEquals(13.0, vm.state.value.entry.abvPercent!!, 0.0)

        vm.onAbvInput("13,5")
        assertEquals(13.5, vm.state.value.entry.abvPercent!!, 0.0)
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `пустая крепость валидна и очищает значение`() = runTest {
        repo.upsert(Entry(category = Category.WINE, name = "Бароло", abvPercent = 14.0, createdAt = 1))
        val vm = vm(id = 1)
        advanceUntilIdle()

        vm.onAbvInput("")

        assertFalse(vm.state.value.abvUnparsed)
        assertTrue(vm.state.value.canSave)
        vm.save()
        advanceUntilIdle()
        assertNull(repo.store.value.getValue(1L).abvPercent)
    }


    @Test
    fun `редактор держит аренду на фото черновика, пока жив`() = runTest {
        val vm = vm()
        runCurrent()
        vm.onPhotosPicked(listOf(PhotoSource("content://1")))
        advanceUntilIdle()

        // Фото импортировано, но ни одной записью не используется — уборка
        // не должна счесть его осиротевшим.
        assertTrue(lease.isHeld("photos/imported1.jpg"))
    }

    @Test
    fun `аренда снимается с удалённой из формы фотографии и при закрытии редактора`() = runTest {
        val (vm, store) = scopedVm()
        runCurrent()
        vm.onPhotosPicked(listOf(PhotoSource("content://1"), PhotoSource("content://2")))
        advanceUntilIdle()
        assertTrue(lease.isHeld("photos/imported1.jpg"))

        vm.onPhotoRemove("photos/imported1.jpg")

        assertFalse(lease.isHeld("photos/imported1.jpg"))
        assertTrue(lease.isHeld("photos/imported2.jpg"))

        vm.discard()
        store.clear()
        assertFalse(lease.isHeld("photos/imported2.jpg"))
    }

    @Test
    fun `восстановленный черновик сразу берёт аренду на своё фото`() = runTest {
        val savedState = SavedStateHandle()
        val (first, store) = scopedVm(savedState = savedState)
        runCurrent()
        first.onPhotosPicked(listOf(PhotoSource("content://1")))
        advanceUntilIdle()
        store.clear()
        assertFalse(lease.isHeld("photos/imported1.jpg"))

        // Процесс умер, ViewModel создаётся заново из того же SavedStateHandle.
        vm(savedState = savedState)
        runCurrent()

        assertTrue(lease.isHeld("photos/imported1.jpg"))
    }

    @Test
    fun `отказ БД при загрузке даёт FAILED, а повтор перечитывает запись`() = runTest {
        var fail = true
        val flaky = object : EntryRepository by repo {
            override suspend fun getById(id: Long): Entry? =
                if (fail) throw IllegalStateException("database is locked") else repo.getById(id)
        }
        repo.upsert(Entry(category = Category.BEER, name = "Гиннесс", createdAt = 1))
        val vm = vm(id = 1, repository = flaky)
        advanceUntilIdle()

        // Отказ БД — не «записи нет»: иначе пользователь решит, что запись пропала.
        assertEquals(EditPhase.FAILED, vm.state.value.phase)
        assertFalse(vm.state.value.canSave)

        fail = false
        vm.onRetryLoad()
        advanceUntilIdle()

        assertEquals(EditPhase.READY, vm.state.value.phase)
        assertEquals("Гиннесс", vm.state.value.entry.name)
    }

    @Test
    fun `временный снимок освобождается и после импорта, и при отказе принять выбор`() = runTest {
        val vm = vm()
        runCurrent()
        val consumed = mutableListOf<String>()

        vm.onPhotosPicked(listOf(PhotoSource("content://1"))) { consumed += "1" }
        advanceUntilIdle()
        assertEquals(listOf("1"), consumed)

        // Выбор, отброшенный во время импорта, тоже дочитал свой источник:
        // иначе временный снимок камеры остался бы в кэше навсегда.
        val slow = SlowPhotoStorage()
        val vm2 = vm(photoStorage = slow)
        runCurrent()
        vm2.onPhotosPicked(listOf(PhotoSource("content://a"))) { consumed += "a" }
        runCurrent()
        vm2.onPhotosPicked(listOf(PhotoSource("content://b"))) { consumed += "b" }
        runCurrent()
        slow.gates.forEach { it.complete(Unit) }
        advanceUntilIdle()

        assertTrue("a" in consumed && "b" in consumed)
    }


    @Test
    fun `сохранение публикуется состоянием и переживает пересоздание Activity`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slowRepo = object : EntryRepository by repo {
            override suspend fun upsert(entry: Entry): Long {
                gate.await()
                return repo.upsert(entry)
            }
        }
        val vm = vm(repository = slowRepo)
        runCurrent()
        vm.onField { it.copy(name = "Бароло") }

        vm.save()
        runCurrent()
        assertTrue(vm.state.value.saving)
        assertFalse(vm.state.value.saved)

        // Activity пересоздаётся (поворот): ViewModel живёт дальше, композиция —
        // нет. Признак завершения обязан остаться в состоянии, иначе новый экран
        // навсегда замрёт с saving = true.
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `черновик переживает пересоздание ViewModel`() = runTest {
        repo.upsert(Entry(category = Category.BEER, name = "Гиннесс", createdAt = 1))
        val savedState = SavedStateHandle(mapOf("id" to "1"))
        val first = vm(savedState = savedState)
        advanceUntilIdle()
        first.onField { it.copy(name = "Гиннесс Экстра") }

        // Процесс умер, ViewModel создаётся заново с тем же SavedStateHandle.
        val restored = vm(savedState = savedState)
        advanceUntilIdle()

        assertEquals(EditPhase.READY, restored.state.value.phase)
        assertEquals("Гиннесс Экстра", restored.state.value.entry.name)
        assertTrue(restored.state.value.dirty)
    }

    @Test
    fun `черновик заметки не запрашивается без названия`() = runTest {
        val writer = EditFakeDraftWriter("Живая заметка")
        val vm = vm(draftWriter = writer)

        vm.onDraftNote()
        advanceUntilIdle()

        assertEquals(0, writer.calls)
        assertEquals(null, vm.state.value.entry.note)
    }

    @Test
    fun `черновик заметки кладётся в поле записи`() = runTest {
        val writer = EditFakeDraftWriter("Живая заметка")
        val vm = vm(draftWriter = writer)
        vm.onField { it.copy(name = "Бароло") }

        vm.onDraftNote()
        advanceUntilIdle()

        assertEquals("Живая заметка", vm.state.value.entry.note)
        assertFalse(vm.state.value.drafting)
    }

    /**
     * Без образцов модель пишет обобщённым «винным» языком. Заметки автора —
     * единственный источник его стиля, поэтому они обязаны доехать до запроса.
     */
    @Test
    fun `в черновик передаются прежние заметки той же категории`() = runTest {
        repo.upsert(
            Entry(
                id = 1, category = Category.WINE, name = "Бароло",
                note = "👀 Цвет рубиновый. ВЕРДИКТ: хорошо.", createdAt = 10,
            )
        )
        repo.upsert(
            Entry(id = 2, category = Category.BEER, name = "Стаут", note = "пивная", createdAt = 20)
        )
        val writer = EditFakeDraftWriter("черновик")
        val vm = vm(draftWriter = writer)
        vm.onField { it.copy(name = "Кьянти") }

        vm.onDraftNote()
        advanceUntilIdle()

        assertEquals(listOf("👀 Цвет рубиновый. ВЕРДИКТ: хорошо."), writer.seenExamples)
    }

    /**
     * Черновик перезаписывает заметку целиком, а ответа модели ждать до полутора
     * минут: без заморозки набранное за это время пропало бы молча.
     */
    @Test
    fun `пока пишется черновик, поле заметки заморожено`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val writer = object : DraftWriter {
            override suspend fun draftNote(entry: Entry, examples: List<String>): String {
                gate.await()
                return "Живая заметка"
            }
        }
        val vm = vm(draftWriter = writer)
        vm.onField { it.copy(name = "Бароло") }

        vm.onDraftNote()
        runCurrent()
        assertTrue(vm.state.value.drafting)
        assertFalse(vm.state.value.noteEditable)
        // Остальные поля правятся как обычно — их черновик не трогает.
        assertTrue(vm.state.value.editable)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("Живая заметка", vm.state.value.entry.note)
        assertTrue(vm.state.value.noteEditable)
    }

    @Test
    fun `черновик доступен только с настроенным облаком`() = runTest {
        val settings = EditFakeSettings(RecognitionConfig())
        val vm = vm(settings = settings)
        // Подписчик нужен: draftAvailable живёт, пока экран его читает.
        val watching = launch { vm.draftAvailable.collect {} }
        advanceUntilIdle()

        // Кнопки на экране нет: нажатие всё равно отвечало бы отказом.
        assertFalse(vm.draftAvailable.value)

        settings.recognition.value =
            RecognitionConfig(enabled = true, apiKey = "sk-or-v1", model = "vendor/model")
        advanceUntilIdle()

        assertTrue(vm.draftAvailable.value)
        watching.cancel()
    }
}
