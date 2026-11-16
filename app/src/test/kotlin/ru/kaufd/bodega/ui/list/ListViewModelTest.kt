package ru.kaufd.bodega.ui.list

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
import ru.kaufd.bodega.domain.DeleteEntryUseCase
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.LabelRecognizer
import ru.kaufd.bodega.domain.LabelScan
import ru.kaufd.bodega.domain.PhotoLease
import ru.kaufd.bodega.domain.PhotoSource
import ru.kaufd.bodega.domain.RecognitionConfig
import ru.kaufd.bodega.domain.RecognitionNotices
import ru.kaufd.bodega.domain.Settings
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.domain.SortOrder
import ru.kaufd.bodega.ui.state.FilterStateHolder

private class FakeRepo : EntryRepository {
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    val deleted = mutableListOf<Entry>()
    val restored = mutableListOf<Entry>()
    val purged = mutableListOf<String?>()
    override fun observeEntries(filter: EntryFilter) = entries.map { list ->
        list.filter { filter.category?.let { c -> it.category == c } ?: (filter.includeRecipes || !it.category.isRecipe) }
            .filter { filter.query.isEmpty() || it.name.contains(filter.query, ignoreCase = true) }
    }
    override fun observeCount(category: Category?) = entries.map { l ->
        l.count { category == null || it.category == category }
    }
    override suspend fun getById(id: Long) = entries.value.find { it.id == id }
    override fun observeById(id: Long): Flow<Entry?> = entries.map { l -> l.find { it.id == id } }
    override suspend fun upsert(entry: Entry) = entry.id
    override suspend fun insertAll(entries: List<Entry>) { entries.forEach { upsert(it) } }
    override suspend fun delete(id: Long): Entry? {
        val stored = entries.value.find { it.id == id } ?: return null
        deleted += stored; entries.value = entries.value - stored
        return stored
    }
    override suspend fun restore(entry: Entry) {
        restored += entry; entries.value = entries.value + entry
    }
    override suspend fun purgePhotos(paths: List<String>): Boolean { purged += paths; return true }
    override suspend fun usedPhotoPaths(): List<String> = emptyList()
    override suspend fun recentNotes(category: Category, limit: Int): List<String> = emptyList()
    override fun observeUsedTypesGrouped(category: Category?): Flow<Map<Category, List<String>>> = flowOf(emptyMap())
    override fun observeUsedCountries(): Flow<List<String>> = flowOf(emptyList())
    override fun observeUsedStyles(): Flow<List<String>> = flowOf(emptyList())
    override fun observeWineFacets() = flowOf(ru.kaufd.bodega.domain.WineFacets())
}

private class FakeSettings(
    /** Открывается, когда «чтение настроек» должно завершиться. */
    val gate: CompletableDeferred<Unit>? = null,
    val saveFailure: Throwable? = null,
) : SettingsStore {
    val saved = mutableListOf<Settings>()
    private val stored = MutableStateFlow(Settings())
    override val settings: Flow<Settings> = flow {
        gate?.await()
        emitAll(stored)
    }
    fun set(value: Settings) { stored.value = value }
    override suspend fun save(settings: Settings) {
        saveFailure?.let { throw it }
        saved += settings
    }

    override val compactList = MutableStateFlow(true)
    override suspend fun saveCompactList(compact: Boolean) {
        saveFailure?.let { throw it }
        compactList.value = compact
    }

    override val recognition = MutableStateFlow(RecognitionConfig())
    override suspend fun saveRecognition(config: RecognitionConfig) { recognition.value = config }
}

private class FakeLabelRecognizer(var result: String = "", var throwOnRecognize: Boolean = false) : LabelRecognizer {
    val calls = mutableListOf<PhotoSource>()
    override suspend fun recognize(source: PhotoSource): LabelScan {
        calls += source
        if (throwOnRecognize) throw RuntimeException("OCR error")
        return LabelScan(rawText = result)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepo
    private lateinit var settings: FakeSettings
    private lateinit var holder: FilterStateHolder
    private lateinit var deleteUseCase: DeleteEntryUseCase
    private lateinit var recognizer: FakeLabelRecognizer
    private lateinit var notices: RecognitionNotices

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepo(); settings = FakeSettings(); holder = FilterStateHolder()
        deleteUseCase = DeleteEntryUseCase(repo, PhotoLease())
        recognizer = FakeLabelRecognizer()
        notices = RecognitionNotices()
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(
        recognizer: LabelRecognizer = this.recognizer,
        settingsStore: SettingsStore = settings,
        repository: EntryRepository = repo,
        notices: RecognitionNotices = this.notices,
    ) = ListViewModel(repository, settingsStore, holder, deleteUseCase, recognizer, notices)

    private fun wine(id: Long, name: String) =
        Entry(id = id, category = Category.WINE, name = name, createdAt = id)

    private fun beer(id: Long, name: String) =
        Entry(id = id, category = Category.BEER, name = name, createdAt = id)

    private fun spirit(id: Long, name: String) =
        Entry(id = id, category = Category.SPIRIT, name = name, createdAt = id)

    @Test
    fun `каждый раздел восстанавливает свой фильтр`() = runTest {
        val vm = vm()
        val drinksFilter = EntryFilter(category = Category.WINE, query = "бароло", minRating = 8, sort = SortOrder.RATING)
        val cocktailFilter = EntryFilter(category = Category.COCKTAIL, query = "негрони")
        holder.filter.value = drinksFilter
        vm.onSectionSelect(true)
        dispatcher.scheduler.runCurrent()
        assertEquals(EntryFilter(category = Category.COCKTAIL), holder.filter.value)
        assertTrue("раздел рецептов не пишет в настройки", settings.saved.none { it.lastCategory == Category.COCKTAIL })
        vm.onQueryChange("негрони")
        vm.onSectionSelect(false)
        dispatcher.scheduler.runCurrent()
        assertEquals(drinksFilter, holder.filter.value)
        assertEquals(Category.WINE, settings.saved.last().lastCategory)
        vm.onSectionSelect(true)
        dispatcher.scheduler.runCurrent()
        assertEquals(cocktailFilter, holder.filter.value)
    }

    @Test
    fun `коктейли остаются в своей категории и исключены из общей ленты и счётчика`() = runTest {
        val cocktail = Entry(id = 2, category = Category.COCKTAIL, name = "Негрони")
        repo.entries.value = listOf(wine(1, "Бароло"), cocktail)
        val vm = vm()
        vm.state.test {
            var s = awaitItem()
            while (s.totalCount < 0) s = awaitItem()
            assertEquals(listOf("Бароло"), s.entries.map { it.name })
            assertEquals(1, s.totalCount)
            assertEquals(1, s.categoryCounts[Category.COCKTAIL])
            vm.onCategorySelect(Category.COCKTAIL)
            do { s = awaitItem() } while (s.filter.category != Category.COCKTAIL)
            assertEquals(listOf(cocktail), s.entries)
        }
    }

    @Test
    fun `пустой журнал с выбранной категорией напитков - стартовая заглушка`() {
        val state = ListState(filter = EntryFilter(category = Category.WINE), totalCount = 0)
        assertEquals(EmptyKind.COLLECTION, state.emptyKind)
    }

    @Test
    fun `смена категории внутри раздела держит прежний список`() {
        val state = ListState(entries = listOf(wine(1, "Бароло")), filter = EntryFilter(category = Category.WINE), totalCount = 1)
        assertEquals(state, sectionView(state, EntryFilter(category = Category.BEER), emptyMap()))
    }

    @Test
    fun `смена раздела ждёт данных, а при отказе загрузки показывает ошибку`() {
        val recipes = EntryFilter(category = Category.COCKTAIL)
        val drinks = ListState(entries = listOf(wine(1, "Бароло")), totalCount = 1)
        assertNull(sectionView(drinks, recipes, emptyMap()))
        val failed = ListState(loadFailed = true)
        assertTrue(sectionView(failed, recipes, emptyMap())!!.loadFailed)
        val cached = mapOf(LibrarySection.COCKTAILS to SectionListSnapshot(recipes, emptyList()))
        assertEquals(recipes, sectionView(drinks, recipes, cached)!!.filter)
    }

    @Test
    fun `пустой раздел коктейлей доступен даже без напитков`() = runTest {
        val vm = vm()
        vm.onCategorySelect(Category.COCKTAIL)
        vm.state.test {
            var s = awaitItem()
            while (s.totalCount < 0) s = awaitItem()
            assertEquals(EmptyKind.CATEGORY, s.emptyKind)
        }
    }

    @Test
    fun `пустая база - EmptyKind COLLECTION`() = runTest {
        vm().state.test {
            var s = awaitItem()
            while (s.emptyKind != EmptyKind.COLLECTION) s = awaitItem()
            assertEquals(EmptyKind.COLLECTION, s.emptyKind)
        }
    }

    @Test
    fun `пустая категория при непустой базе - EmptyKind CATEGORY`() = runTest {
        repo.entries.value = listOf(wine(1, "Бароло"))
        val vm = vm()
        vm.onCategorySelect(Category.BEER)
        vm.state.test {
            var s = awaitItem()
            while (s.emptyKind != EmptyKind.CATEGORY) s = awaitItem()
            assertEquals(EmptyKind.CATEGORY, s.emptyKind)
        }
    }

    @Test
    fun `пустой результат поиска - EmptyKind SEARCH`() = runTest {
        repo.entries.value = listOf(wine(1, "Бароло"))
        val vm = vm()
        vm.onQueryChange("кьянти")
        vm.state.test {
            var s = awaitItem()
            while (s.emptyKind != EmptyKind.SEARCH) s = awaitItem()
            assertEquals(EmptyKind.SEARCH, s.emptyKind)
        }
    }

    @Test
    fun `delete затем undo восстанавливает и не чистит фото`() = runTest {
        val e = wine(1, "Бароло").copy(photos = listOf("photos/a.jpg"))
        repo.entries.value = listOf(e)
        val vm = vm()
        deleteUseCase.delete(e)
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(e), repo.deleted)
        vm.onUndoDelete()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf(e), repo.restored)
        assertEquals(emptyList<String>(), repo.purged)
        assertNull(vm.state.first().pendingDelete)
    }

    @Test
    fun `delete затем истечение undo чистит фото`() = runTest {
        val e = wine(1, "Бароло").copy(photos = listOf("photos/a.jpg"))
        repo.entries.value = listOf(e)
        val vm = vm()
        deleteUseCase.delete(e)
        dispatcher.scheduler.runCurrent()
        vm.onUndoExpired()
        dispatcher.scheduler.runCurrent()
        assertEquals(listOf<String?>("photos/a.jpg"), repo.purged)
    }

    @Test
    fun `смена категории сохраняется в settings`() = runTest {
        val vm = vm()
        vm.onCategorySelect(Category.SPIRIT)
        dispatcher.scheduler.runCurrent()
        assertEquals(Category.SPIRIT, settings.saved.last().lastCategory)
    }

    @Test
    fun `categoryCounts считаются по категориям и не зависят от запроса и фильтров`() = runTest {
        repo.entries.value = listOf(
            wine(1, "Бароло"), wine(2, "Кьянти"),
            beer(3, "Чимэй"),
            spirit(4, "Дон Хулио"),
        )
        val vm = vm()
        // Задаём запрос и продвинутые фильтры — счётчики категорий от них не меняются.
        vm.onQueryChange("бароло")
        holder.filter.value = holder.filter.value.copy(minRating = 8, sort = SortOrder.RATING)
        vm.state.test {
            var s = awaitItem()
            // ждём, пока счётчики прогрузятся (totalCount != -1 — удобный маркер)
            while (s.totalCount < 0 || s.categoryCounts.isEmpty()) s = awaitItem()
            assertEquals(4, s.totalCount)
            assertEquals(2, s.categoryCounts[Category.WINE])
            assertEquals(1, s.categoryCounts[Category.BEER])
            assertEquals(1, s.categoryCounts[Category.SPIRIT])
        }
    }

    @Test
    fun `onClearFilters сбрасывает продвинутые фильтры сохраняя категорию и запрос`() = runTest {
        holder.filter.value = EntryFilter(
            category = Category.WINE,
            query = "неббиоло",
            colors = setOf("Красное"),
            minRating = 8,
            sort = SortOrder.RATING,
        )
        val vm = vm()
        vm.onClearFilters()
        dispatcher.scheduler.runCurrent()
        val f = holder.filter.value
        assertEquals(Category.WINE, f.category)
        assertEquals("неббиоло", f.query)
        assertEquals(0, f.activeFilterCount)
        assertEquals(SortOrder.DATE, f.sort)
    }

    @Test
    fun `onResetFilters дополнительно чистит запрос`() = runTest {
        holder.filter.value = EntryFilter(
            category = Category.BEER,
            query = "ламбик",
            styles = setOf("Ламбик"),
            minRating = 7,
        )
        val vm = vm()
        vm.onResetFilters()
        dispatcher.scheduler.runCurrent()
        val f = holder.filter.value
        assertEquals(Category.BEER, f.category)
        assertEquals("", f.query)
        assertEquals(0, f.activeFilterCount)
    }

    @Test
    fun `filter отражает ввод немедленно, не дожидаясь пересчёта списка`() = runTest {
        // Поле ввода читает query из быстрого filter, а не из Room-зависимого state,
        // иначе значение отстаёт на один запрос к БД и курсор прыгает назад.
        val vm = vm()
        vm.onQueryChange("ба")
        // Без прогона диспетчера (без round-trip к репозиторию) поле уже видит запрос.
        assertEquals("ба", vm.filter.value.query)
    }

    @Test
    fun `поздний результат сканирования не попадает в поиск рецептов`() = runTest {
        val result = CompletableDeferred<LabelScan>()
        val delayedRecognizer = object : LabelRecognizer {
            override suspend fun recognize(source: PhotoSource): LabelScan = result.await()
        }
        val vm = vm(recognizer = delayedRecognizer)
        vm.onScanLabel(PhotoSource("content://test/uri"))
        dispatcher.scheduler.runCurrent()
        vm.onSectionSelect(true)
        result.complete(LabelScan(rawText = "Barolo"))
        dispatcher.scheduler.runCurrent()
        assertEquals(EntryFilter(category = Category.COCKTAIL), holder.filter.value)
    }

    @Test
    fun `onScanLabel кладёт распознанный текст в query и сбрасывает scanning`() = runTest {
        recognizer.result = "rioja gran reserva"
        val vm = vm()
        vm.onScanLabel(PhotoSource("content://test/uri"))
        dispatcher.scheduler.runCurrent()
        assertEquals("rioja gran reserva", holder.filter.value.query)
        assertEquals(listOf(PhotoSource("content://test/uri")), recognizer.calls)
        assertFalse(vm.state.first().scanning)
    }

    @Test
    fun `onScanLabel схлопывает переносы строк из OCR в пробелы`() = runTest {
        // ML Kit возвращает блоки через \n; строка поиска однострочная.
        recognizer.result = "Rioja\nGran Reserva\n2014"
        val vm = vm()
        vm.onScanLabel(PhotoSource("content://test/uri"))
        dispatcher.scheduler.runCurrent()
        assertEquals("Rioja Gran Reserva 2014", holder.filter.value.query)
    }

    @Test
    fun `onScanLabel при исключении не трогает query и сбрасывает scanning`() = runTest {
        holder.filter.value = holder.filter.value.copy(query = "прежний запрос")
        recognizer.throwOnRecognize = true
        val vm = vm()
        vm.onScanLabel(PhotoSource("content://test/uri"))
        dispatcher.scheduler.runCurrent()
        assertEquals("прежний запрос", holder.filter.value.query)
        assertFalse(vm.state.first().scanning)
    }

    @Test
    fun `onScanLabel игнорирует двойной запуск`() = runTest {
        recognizer.result = "первый"
        val vm = vm()
        vm.onScanLabel(PhotoSource("content://test/1"))
        // Не даём диспетчеру отработать — второй вызов должен быть проигнорирован
        vm.onScanLabel(PhotoSource("content://test/2"))
        dispatcher.scheduler.runCurrent()
        assertEquals(1, recognizer.calls.size)
    }

    @Test
    fun `выбор категории до загрузки настроек не затирается восстановленным значением`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slowSettings = FakeSettings(gate = gate).apply { set(Settings(lastCategory = Category.BEER)) }
        val vm = vm(settingsStore = slowSettings)
        runCurrent()

        // Пользователь успел переключиться раньше, чем прочитались настройки.
        vm.onCategorySelect(Category.SPIRIT)
        runCurrent()
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Category.SPIRIT, holder.filter.value.category)
    }


    @Test
    fun `переключение категории списка нормализует фасеты`() = runTest {
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()
        holder.filter.value = holder.filter.value.copy(
            category = Category.WINE,
            colors = setOf("Красное"),
            countries = setOf("Италия"),
        )

        vm.onCategorySelect(Category.BEER)

        // copy(category = ...) оставил бы винные фасеты и дал ложно пустой список.
        assertEquals(Category.BEER, holder.filter.value.category)
        assertEquals(emptySet<String>(), holder.filter.value.colors)
        assertEquals(emptySet<String>(), holder.filter.value.countries)
    }

    @Test
    fun `неудавшийся undo возвращает снекбар, увеличивая счётчик отказов`() = runTest {
        val failing = object : EntryRepository by repo {
            override suspend fun restore(entry: Entry) = error("database is locked")
        }
        val useCase = DeleteEntryUseCase(failing, PhotoLease())
        deleteUseCase = useCase
        repo.entries.value = listOf(wine(1, "Бароло"))
        val vm = vm()
        dispatcher.scheduler.advanceUntilIdle()

        useCase.delete(wine(1, "Бароло"))
        val before = vm.state.first().undoFailures
        vm.onUndoDelete()
        dispatcher.scheduler.advanceUntilIdle()

        // pending при отказе не меняется — без счётчика ключ снекбара остался бы
        // прежним, и повторить отмену было бы нечем.
        assertEquals(before + 1, vm.state.first().undoFailures)
        assertEquals(wine(1, "Бароло"), useCase.pending.value)
    }

    @Test
    fun `явный выбор «Все» не отменяется поздно пришедшими настройками`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slowSettings = FakeSettings(gate = gate).apply { set(Settings(lastCategory = Category.BEER)) }
        val vm = vm(settingsStore = slowSettings)
        runCurrent()

        // Пользователь нажал уже выбранное «Все»: значение фильтра не изменилось,
        // и сравнение с исходным снимком такой выбор не заметило бы.
        vm.onCategorySelect(null)
        runCurrent()
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(holder.filter.value.category)
    }

    @Test
    fun `сохранённая категория восстанавливается, если пользователь не вмешался`() = runTest {
        settings.set(Settings(lastCategory = Category.BEER, sort = SortOrder.RATING))
        vm()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Category.BEER, holder.filter.value.category)
        assertEquals(SortOrder.RATING, holder.filter.value.sort)
    }

    @Test
    fun `отказ DataStore при сохранении категории не роняет ViewModel`() = runTest {
        val failing = FakeSettings(saveFailure = java.io.IOException("disk"))
        val vm = vm(settingsStore = failing)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onCategorySelect(Category.WINE)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Category.WINE, holder.filter.value.category)
    }

    @Test
    fun `отказ Room переводит список в состояние ошибки, повтор перезапускает подписку`() = runTest {
        var fail = true
        val flaky = object : EntryRepository by repo {
            override fun observeEntries(filter: EntryFilter): Flow<List<Entry>> =
                if (fail) flow { throw IllegalStateException("database is locked") }
                else repo.observeEntries(filter)
        }
        repo.entries.value = listOf(wine(1, "Бароло"))
        val vm = vm(repository = flaky)

        vm.state.test {
            assertTrue(awaitItem().loadFailed || awaitItem().loadFailed)
            fail = false
            vm.onRetryLoad()
            var s = awaitItem()
            while (s.loadFailed) s = awaitItem()
            assertEquals(listOf("Бароло"), s.entries.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onScanLabel при пустом результате OCR сохраняет query и сообщает экрану`() = runTest {
        recognizer.result = ""
        holder.filter.value = holder.filter.value.copy(query = "прежний")
        val vm = vm()
        val errors = mutableListOf<ListError>()
        val job = launch { vm.error.collect { errors += it } }
        runCurrent()

        vm.onScanLabel(PhotoSource("content://test/uri"))
        dispatcher.scheduler.runCurrent()

        // Пустой распознанный текст — не команда «очистить поиск».
        assertEquals("прежний", holder.filter.value.query)
        assertEquals(listOf(ListError.ScanEmpty), errors)
        assertFalse(vm.state.first().scanning)
        job.cancel()
    }

    @Test
    fun `onScanLabel включает fuzzy-режим`() = runTest {
        recognizer.result = "rioja"
        val vm = vm()
        vm.onScanLabel(PhotoSource("content://test/uri"))
        dispatcher.scheduler.runCurrent()
        assertTrue(holder.filter.value.queryFuzzy)
    }

    @Test
    fun `onScanLabel при ошибке сообщает об этом экрану`() = runTest {
        recognizer.throwOnRecognize = true
        val vm = vm()
        val errors = mutableListOf<ListError>()
        val job = launch { vm.error.collect { errors += it } }
        dispatcher.scheduler.runCurrent()

        vm.onScanLabel(PhotoSource("content://test/uri"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(ListError.Scan), errors)
        job.cancel()
    }

    @Test
    fun `набор запроса задебаунсен, а смена категории применяется сразу`() = runTest {
        repo.entries.value = listOf(wine(1, "Бароло"), beer(2, "Гиннесс"))
        val vm = vm()
        vm.state.test {
            var s = awaitItem()
            while (s.totalCount < 0) s = awaitItem()

            vm.onCategorySelect(Category.BEER)
            // Категория — без задержки: хватает прогона очереди без сдвига времени.
            dispatcher.scheduler.runCurrent()
            assertEquals(Category.BEER, expectMostRecentItem().filter.category)

            vm.onQueryChange("гин")
            dispatcher.scheduler.runCurrent()
            expectNoEvents() // запрос ещё не ушёл в репозиторий

            dispatcher.scheduler.advanceTimeBy(300)
            assertEquals("гин", awaitItem().filter.query)
        }
    }

    @Test
    fun `ручной ввод после скана сбрасывает fuzzy-режим`() = runTest {
        recognizer.result = "rioja"
        val vm = vm()
        vm.onScanLabel(PhotoSource("content://test/uri"))
        dispatcher.scheduler.runCurrent()
        assertTrue(holder.filter.value.queryFuzzy)

        vm.onQueryChange("riojo")
        assertFalse(holder.filter.value.queryFuzzy)
    }
}
