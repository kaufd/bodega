package ru.kaufd.bodega.ui.filters

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
import app.cash.turbine.test
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.RecognitionConfig
import ru.kaufd.bodega.domain.Settings
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.domain.SortOrder
import ru.kaufd.bodega.ui.state.FilterStateHolder

private class FakeRepo : EntryRepository {
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    override fun observeEntries(filter: EntryFilter) = entries.map { list ->
        list.filter { filter.category?.let { c -> it.category == c } ?: (filter.includeRecipes || !it.category.isRecipe) }
            .filter { filter.types.isEmpty() || it.type in filter.types }
    }
    override fun observeCount(category: Category?) = entries.map { it.size }
    override suspend fun getById(id: Long): Entry? = null
    override fun observeById(id: Long): Flow<Entry?> = flowOf(null)
    override suspend fun upsert(entry: Entry) = 0L
    override suspend fun insertAll(entries: List<Entry>) {}
    override suspend fun delete(id: Long): Entry? = null
    override suspend fun restore(entry: Entry) {}
    override suspend fun purgePhotos(paths: List<String>) = true
    override suspend fun usedPhotoPaths(): List<String> = emptyList()
    override suspend fun recentNotes(category: Category, limit: Int): List<String> = emptyList()
    override fun observeUsedTypesGrouped(category: Category?): Flow<Map<Category, List<String>>> =
        entries.map { l ->
            l.filter { category == null || it.category == category }
                .filter { it.type != null }
                .groupBy({ it.category }, { it.type!! })
                .mapValues { (_, v) -> v.distinct().sorted() }
                .toSortedMap(compareBy { it.ordinal })
        }
    override fun observeUsedCountries(): Flow<List<String>> =
        entries.map { l -> l.mapNotNull { it.country }.distinct().sorted() }
    override fun observeUsedStyles(): Flow<List<String>> = entries.map { l ->
        l.filter { it.category == Category.BEER }.mapNotNull { it.style }.distinct()
    }
    override fun observeWineFacets(): Flow<ru.kaufd.bodega.domain.WineFacets> = entries.map { l ->
        val wines = l.filter { it.category == Category.WINE }
        ru.kaufd.bodega.domain.WineFacets(
            colors = wines.mapNotNull { it.color }.distinct(),
            types = wines.mapNotNull { it.wineType }.distinct(),
            sweetness = wines.mapNotNull { it.sweetness }.distinct(),
        )
    }
}

private class FakeSettings(private val saveFailure: Throwable? = null) : SettingsStore {
    val saved = mutableListOf<Settings>()
    override val settings = MutableStateFlow(Settings())
    override suspend fun save(settings: Settings) {
        saveFailure?.let { throw it }
        saved += settings
    }

    override val compactList = MutableStateFlow(true)
    override suspend fun saveCompactList(compact: Boolean) { compactList.value = compact }

    override val recognition = MutableStateFlow(RecognitionConfig())
    override suspend fun saveRecognition(config: RecognitionConfig) { recognition.value = config }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FiltersViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeRepo
    private lateinit var holder: FilterStateHolder
    private lateinit var settings: FakeSettings

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = FakeRepo(); holder = FilterStateHolder(); settings = FakeSettings()
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(
        settingsStore: SettingsStore = settings,
        repository: EntryRepository = repo,
    ) = FiltersViewModel(repository, holder, settingsStore)

    @Test
    fun `счётчик общей ленты не включает рецепты`() = runTest {
        repo.entries.value = listOf(
            Entry(id = 1, category = Category.WINE, name = "Вино"),
            Entry(id = 2, category = Category.COCKTAIL, name = "Негрони"),
        )
        val vm = vm()
        vm.state.test {
            var s = awaitItem()
            while (s.resultCount != 1) s = awaitItem()
            assertEquals(1, s.resultCount)
        }
    }

    @Test
    fun `resultCount пересчитывается по драфту не трогая общий фильтр`() = runTest {
        repo.entries.value = listOf(
            Entry(id = 1, category = Category.BEER, name = "A", type = "IPA", createdAt = 1),
            Entry(id = 2, category = Category.BEER, name = "B", type = "Стаут", createdAt = 2),
        )
        val vm = vm()
        vm.onDraftChange { it.copy(types = setOf("IPA")) }
        vm.state.test {
            var s = awaitItem()
            while (s.resultCount != 1) s = awaitItem()
            assertEquals(1, s.resultCount)
        }
        assertEquals(EntryFilter(), holder.filter.value) // общий фильтр не изменился
    }

    @Test
    fun `страны показываются вину и разделу «Все», но не пиву`() = runTest {
        repo.entries.value = listOf(
            Entry(id = 1, category = Category.WINE, name = "Бароло", country = "Италия", createdAt = 1),
        )
        val vm = vm()
        vm.state.test {
            var s = awaitItem()
            while (s.availableCountries.isEmpty()) s = awaitItem()
            assertEquals(listOf("Италия"), s.availableCountries)

            // Страна есть только у вина — в разделе пива фильтр по ней дал бы пустой список.
            vm.onDraftChange { it.withCategory(Category.BEER) }
            while (s.draft.category != Category.BEER) s = awaitItem()
            assertEquals(emptyList<String>(), s.availableCountries)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `стили — только использованные и только в разделе пива`() = runTest {
        repo.entries.value = listOf(
            Entry(id = 1, category = Category.BEER, name = "Chimay", style = "Трипель", createdAt = 1),
            Entry(id = 2, category = Category.WINE, name = "Бароло", createdAt = 2),
        )
        val vm = vm()
        vm.onDraftChange { it.withCategory(Category.BEER) }
        vm.state.test {
            var s = awaitItem()
            while (s.availableStyles.isEmpty()) s = awaitItem()
            // Весь справочник BeerClassification здесь не нужен: фильтр по стилю,
            // которого нет в погребе, дал бы гарантированно пустой результат.
            assertEquals(listOf("Трипель"), s.availableStyles)

            vm.onDraftChange { it.withCategory(Category.WINE) }
            while (s.draft.category != Category.WINE) s = awaitItem()
            assertEquals(emptyList<String>(), s.availableStyles)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `смена категории снимает выбранные страны`() = runTest {
        val vm = vm()
        vm.onDraftChange { it.withCategory(Category.WINE).copy(countries = setOf("Италия")) }
        vm.onDraftChange { it.withCategory(Category.BEER) }
        vm.state.test {
            var s = awaitItem()
            while (s.draft.category != Category.BEER) s = awaitItem()
            assertEquals(emptySet<String>(), s.draft.countries)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `отказ DataStore не мешает применить фильтр и закрыть экран`() = runTest {
        val vm = vm(settingsStore = FakeSettings(saveFailure = java.io.IOException("disk")))
        vm.onDraftChange { it.copy(sort = SortOrder.PRICE) }

        vm.applyDraft()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(SortOrder.PRICE, holder.filter.value.sort)
        assertEquals(true, vm.applied.value)
    }

    @Test
    fun `отказ Room оставляет экран рабочим и помечает состояние ошибкой`() = runTest {
        val failing = object : EntryRepository by repo {
            override fun observeUsedCountries(): Flow<List<String>> =
                kotlinx.coroutines.flow.flow { throw IllegalStateException("database is locked") }
        }
        val vm = vm(repository = failing)
        vm.state.test {
            var s = awaitItem()
            while (!s.loadFailed) s = awaitItem()
            assertEquals(EntryFilter(), s.draft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `повтор после отказа Room возвращает справочники`() = runTest {
        var fail = true
        val flaky = object : EntryRepository by repo {
            override fun observeUsedCountries(): Flow<List<String>> =
                if (fail) kotlinx.coroutines.flow.flow { throw IllegalStateException("locked") }
                else repo.observeUsedCountries()
        }
        repo.entries.value = listOf(
            Entry(id = 1, category = Category.WINE, name = "Бароло", country = "Италия", createdAt = 1),
        )
        val vm = vm(repository = flaky)
        vm.state.test {
            var s = awaitItem()
            while (!s.loadFailed) s = awaitItem()

            fail = false
            vm.onRetryLoad()
            while (s.availableCountries.isEmpty()) s = awaitItem()
            assertEquals(listOf("Италия"), s.availableCountries)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `после отказа Room правка черновика видна на экране без повтора`() = runTest {
        val failing = object : EntryRepository by repo {
            override fun observeUsedCountries(): Flow<List<String>> =
                kotlinx.coroutines.flow.flow { throw IllegalStateException("locked") }
        }
        val vm = vm(repository = failing)
        vm.state.test {
            var s = awaitItem()
            while (!s.loadFailed) s = awaitItem()

            // Без продолжения по черновику экран замер бы здесь, и пользователь
            // правил бы фильтры вслепую — вплоть до применения невидимых изменений.
            vm.onDraftChange { it.copy(minPrice = 500) }
            while (s.draft.minPrice != 500) s = awaitItem()
            assertEquals(500, s.draft.minPrice)
            assertEquals(true, s.loadFailed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `apply публикует драфт в холдер и сохраняет сортировку`() = runTest {
        val vm = vm()
        vm.onDraftChange { it.copy(sort = SortOrder.PRICE, minRating = 7) }
        vm.applyDraft()
        dispatcher.scheduler.runCurrent()
        assertEquals(SortOrder.PRICE, holder.filter.value.sort)
        assertEquals(7, holder.filter.value.minRating)
        assertEquals(SortOrder.PRICE, settings.saved.last().sort)
        assertEquals(true, vm.applied.value)
    }

    @Test
    fun `тип крепкого показывается только при выбранной категории Крепкое`() = runTest {
        repo.entries.value = listOf(
            Entry(id = 1, category = Category.SPIRIT, name = "A", type = "Виски", createdAt = 1),
        )
        val vm = vm()
        vm.state.test {
            // по умолчанию категория не выбрана («Все») — секции типа нет
            var s = awaitItem()
            while (s.resultCount != 1) s = awaitItem()
            assertEquals(emptyMap<Category, List<String>>(), s.availableTypes)
            // при выборе «Крепкое» — появляется тип крепкого
            vm.onDraftChange { it.copy(category = Category.SPIRIT) }
            while (s.availableTypes.isEmpty()) s = awaitItem()
            assertEquals(mapOf(Category.SPIRIT to listOf("Виски")), s.availableTypes)
        }
    }

    @Test
    fun `reset сбрасывает фильтры драфта но не категорию`() = runTest {
        holder.filter.value = EntryFilter(category = Category.WINE, minRating = 5)
        val vm = vm()
        vm.state.test {
            vm.reset()
            var s = awaitItem()
            while (s.draft != EntryFilter(category = Category.WINE)) s = awaitItem()
            assertEquals(EntryFilter(category = Category.WINE), s.draft)
        }
    }
}
