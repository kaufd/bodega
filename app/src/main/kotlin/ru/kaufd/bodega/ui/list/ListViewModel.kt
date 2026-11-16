package ru.kaufd.bodega.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.DeleteEntryUseCase
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.LabelRecognizer
import ru.kaufd.bodega.domain.RecognitionNotices
import ru.kaufd.bodega.domain.searchQueryOf
import ru.kaufd.bodega.domain.RecognitionException
import ru.kaufd.bodega.domain.RecognitionError
import ru.kaufd.bodega.domain.PhotoSource
import ru.kaufd.bodega.domain.rememberCollection
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.domain.stripCameraWatermark
import ru.kaufd.bodega.ui.state.FilterStateHolder
import javax.inject.Inject

enum class EmptyKind { COLLECTION, CATEGORY, SEARCH }

enum class LibrarySection { DRINKS, COCKTAILS }

data class SectionListSnapshot(val filter: EntryFilter, val entries: List<Entry>)

internal fun EntryFilter.librarySection(): LibrarySection =
    if (category?.isRecipe == true) LibrarySection.COCKTAILS else LibrarySection.DRINKS

/**
 * Что показать, пока Room догоняет быстрый [filter]; null — данных выбранного раздела
 * ещё нет, нужен индикатор.
 *
 * Подмена нужна только при смене раздела, иначе напитки мелькнули бы под вкладкой
 * рецептов; смена категории внутри раздела, как и прежде, держит старый список до
 * ответа Room. Отказ загрузки важнее вкладки: подписка после него завершена, данных
 * ждать неоткуда, и вместо вечного индикатора нужен LoadFailed с повтором.
 */
internal fun sectionView(
    state: ListState,
    filter: EntryFilter,
    cache: Map<LibrarySection, SectionListSnapshot>,
): ListState? {
    val section = filter.librarySection()
    if (state.loadFailed || state.filter.librarySection() == section) return state
    return cache[section]?.takeIf { it.filter == filter }?.let { state.copy(entries = it.entries, filter = filter) }
}

/** Ошибка, о которой стоит сказать пользователю. */
sealed interface ListError {
    /** Отказ облачного распознавания — причина приходит как есть, без перекодировки. */
    data class Recognition(val error: RecognitionError) : ListError
    /** Распознать не вышло ни облаком, ни офлайн — причина неизвестна. */
    data object Scan : ListError
    /** Распознавание отработало, но текста на этикетке не нашло. */
    data object ScanEmpty : ListError
    data object Undo : ListError
}

/** Пауза перед запросом к БД при наборе поискового запроса. */
private const val QUERY_DEBOUNCE_MS = 250L

data class ListState(
    val entries: List<Entry> = emptyList(),
    val filter: EntryFilter = EntryFilter(),
    val totalCount: Int = -1,          // -1 = ещё не загружено
    val categoryCounts: Map<Category, Int> = emptyMap(),  // тотал по категориям, не зависит от поиска/фильтров
    val pendingDelete: Entry? = null,
    /**
     * Счётчик неудавшихся undo. Экран использует его как часть ключа снекбара:
     * pending при отказе не меняется, и без счётчика предложение отменить удаление
     * больше не появлялось бы — повторить было бы нечем.
     */
    val undoFailures: Int = 0,
    val scanning: Boolean = false,
    /** Поток Room оборвался ошибкой — список показать нечем, нужен повтор. */
    val loadFailed: Boolean = false,
) {
    val emptyKind: EmptyKind?
        get() = when {
            loadFailed || totalCount < 0 || entries.isNotEmpty() -> null
            filter.query.isNotEmpty() || filter.activeFilterCount > 0 -> EmptyKind.SEARCH
            // Пустой журнал напитков — повод для стартовой заглушки, а не для «в разделе
            // пусто». Рецептам своя заглушка нужна всегда: напитки в их тотал не входят.
            filter.category?.isRecipe == true || (filter.category != null && totalCount > 0) -> EmptyKind.CATEGORY
            totalCount == 0 -> EmptyKind.COLLECTION
            else -> EmptyKind.SEARCH
        }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ListViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val settingsStore: SettingsStore,
    private val filterHolder: FilterStateHolder,
    private val deleteUseCase: DeleteEntryUseCase,
    private val labelRecognizer: LabelRecognizer,
    private val recognitionNotices: RecognitionNotices,
) : ViewModel() {

    private val scanning = MutableStateFlow(false)
    private val undoFailures = MutableStateFlow(0)

    // Одноразовые сообщения об ошибках — экран показывает Toast.
    private val _error = MutableSharedFlow<ListError>(extraBufferCapacity = 1)
    val error: SharedFlow<ListError> = _error.asSharedFlow()

    /**
     * Быстрый источник фильтра для поля ввода. В отличие от [state], не завязан на
     * Room-пересчёт списка (flatMapLatest → observeEntries), поэтому значение в поле
     * не отстаёт от набора и курсор не прыгает назад.
     */
    val filter: StateFlow<EntryFilter> = filterHolder.filter

    /** Счётчик попыток перечитать список после отказа Room. */
    private val retry = MutableStateFlow(0)

    /** Последний применённый фильтр каждого раздела и его последний результат из Room. */
    private val sectionFilters = mutableMapOf(
        LibrarySection.DRINKS to EntryFilter(),
        LibrarySection.COCKTAILS to EntryFilter(category = Category.COCKTAIL),
    ).apply {
        val initial = filterHolder.filter.value
        this[initial.librarySection()] = initial
    }
    private val _sectionCache = MutableStateFlow<Map<LibrarySection, SectionListSnapshot>>(emptyMap())
    val sectionCache: StateFlow<Map<LibrarySection, SectionListSnapshot>> = _sectionCache

    /**
     * Вид коллекции: компактный список или плитка. Источник правды — DataStore,
     * поэтому выбор переживает перезапуск. Отказ чтения — дефолтный компактный вид.
     */
    val compactList: StateFlow<Boolean> = settingsStore.compactList
        .catch { emit(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun onCompactChange(compact: Boolean) {
        // Best effort, как и остальные настройки: отказ DataStore стоит забытого
        // вида на следующем запуске, но не падения приложения.
        viewModelScope.launch { runCatching { settingsStore.saveCompactList(compact) } }
    }

    /**
     * Пользователь уже трогал фильтр. Монотонный флаг, а не сравнение значений:
     * выбор уже выбранной категории или правка с возвратом к прежнему значению
     * дают равные фильтры, и восстановленные настройки затёрли бы намеренный выбор.
     */
    private var userTouchedFilter = false
    private var sectionRevision = 0

    init {
        // Восстановить последнюю категорию и сортировку.
        val initial = filterHolder.filter.value
        viewModelScope.launch {
            // Настройки — не повод падать: при отказе DataStore остаёмся на дефолтах.
            val saved = runCatching { settingsStore.settings.first() }.getOrNull() ?: return@launch
            // Пользователь мог переключить категорию раньше, чем прочитались
            // настройки; его выбор новее — восстановленное значение его не затирает.
            if (userTouchedFilter || filterHolder.filter.value != initial) return@launch
            // null lastCategory == «Все» (дефолт) — не затираем уже выбранную
            // пользователем категорию, если восстанавливать нечего.
            // withCategory, а не copy: FilterStateHolder переживает пересоздание
            // ViewModel, и восстановленная категория может не сочетаться с уже
            // выбранными фасетами — список оказался бы ложно пустым.
            val restored = initial
                .withCategory(saved.lastCategory ?: initial.category)
                .copy(sort = saved.sort)
            sectionFilters[restored.librarySection()] = restored
            filterHolder.filter.value = restored
        }

        // Облако отказало, но офлайн выручил: результат показан, а причину — мёртвый
        // ключ или пустой счёт — иначе никто бы не увидел (см. [RecognitionNotices]).
        viewModelScope.launch {
            recognitionNotices.notices.collect { _error.tryEmit(ListError.Recognition(it)) }
        }

        // Advanced filters are edited on a separate screen through the shared
        // FilterStateHolder, so observe it directly instead of relying only on
        // callbacks owned by this screen. That keeps each tab's snapshot current.
        viewModelScope.launch {
            filterHolder.filter.collect { value ->
                sectionFilters[value.librarySection()] = value
            }
        }
    }

    // Тотал по каждой категории (не зависит от поиска/фильтров) — обобщённо по всем категориям.
    private val categoryCounts: Flow<Map<Category, Int>> =
        combine(Category.entries.map { c -> repository.observeCount(c).map { c to it } }) { it.toMap() }

    /**
     * Фильтр для запроса к репозиторию: текст поиска задебаунсен, остальное
     * применяется сразу. Без этого каждый символ порождал новый запрос к БД и
     * полный пересчёт списка; смену категории задерживать, наоборот, незачем.
     */
    private val effectiveFilter: Flow<EntryFilter> = combine(
        filterHolder.filter.map { it.copy(query = "", queryFuzzy = false) }.distinctUntilChanged(),
        filterHolder.filter.map { it.query to it.queryFuzzy }.distinctUntilChanged()
            // Очистка запроса и результат скана должны срабатывать мгновенно.
            .debounce { (query, fuzzy) -> if (query.isEmpty() || fuzzy) 0L else QUERY_DEBOUNCE_MS },
    ) { rest, (query, fuzzy) -> rest.copy(query = query, queryFuzzy = fuzzy) }
        .distinctUntilChanged()

    // Отказ Room внутри Flow завершает сбор: без catch исключение вылетало бы из
    // корутины stateIn и роняло приложение, а экран застывал бы на прежних данных.
    // Повтор пересоздаёт всю цепочку подписок — Flow сам после ошибки не оживает.
    val state: StateFlow<ListState> = retry.flatMapLatest {
        combine(
            effectiveFilter.flatMapLatest { f -> repository.observeEntries(f).map { f to it } },
            deleteUseCase.pending,
            categoryCounts,
            scanning,
            undoFailures,
        ) { (f, entries), pending, counts, scanning, undoFailures ->
            ListState(
                entries = entries,
                filter = f,
                // «Все» — журнал напитков; рецепты живут в отдельной категории.
                totalCount = counts.filterKeys { !it.isRecipe }.values.sum(),
                categoryCounts = counts,
                pendingDelete = pending,
                undoFailures = undoFailures,
                scanning = scanning,
            )
        }.catch { e ->
            if (e is CancellationException) throw e
            emit(ListState(filter = filterHolder.filter.value, loadFailed = true))
        }
    }.onEach(::cacheSectionState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState())

    private fun cacheSectionState(value: ListState) {
        if (value.loadFailed || value.totalCount < 0 || value.filter != filterHolder.filter.value) return
        val snapshot = SectionListSnapshot(value.filter, value.entries)
        _sectionCache.update { it + (value.filter.librarySection() to snapshot) }
    }

    /** Повторная подписка на список после отказа Room. */
    fun onRetryLoad() { retry.value++ }

    fun onQueryChange(q: String) {
        userTouchedFilter = true
        // Ручной ввод — всегда строгий поиск (сбрасываем fuzzy от предыдущего скана).
        filterHolder.filter.value = filterHolder.filter.value.copy(query = q, queryFuzzy = false)
    }

    /** Переключает раздел, восстанавливая его последний фильтр. */
    fun onSectionSelect(recipes: Boolean) {
        val current = filterHolder.filter.value
        val currentSection = current.librarySection()
        val targetSection = if (recipes) LibrarySection.COCKTAILS else LibrarySection.DRINKS
        if (currentSection == targetSection) return
        sectionFilters[currentSection] = current
        userTouchedFilter = true
        sectionRevision++
        filterHolder.filter.value = sectionFilters.getValue(targetSection)
        rememberChoice()
    }

    fun onCategorySelect(c: Category?) {
        userTouchedFilter = true
        // Категорийные фасеты сбрасывает withCategory — единая точка нормализации,
        // общая с экраном фильтров.
        filterHolder.filter.value = filterHolder.filter.value.withCategory(c)
        rememberChoice()
    }

    fun onResetFilters() {
        userTouchedFilter = true
        filterHolder.filter.value = filterHolder.filter.value.resetFilters().copy(query = "", queryFuzzy = false)
        rememberChoice()
    }

    /** Сброс продвинутых фильтров: сохраняет категорию и поисковый запрос. */
    fun onClearFilters() {
        userTouchedFilter = true
        filterHolder.filter.value = filterHolder.filter.value.resetFilters()
        rememberChoice()
    }

    /**
     * Кладёт текущие категорию и сортировку в настройки — их восстанавливает `init`.
     *
     * Зовётся отовсюду, где меняется хоть одно из двух: `resetFilters` возвращает
     * сортировку к дефолту, и без записи `init` восстановил бы прежнюю сортировку
     * по рейтингу, которую пользователь только что сбросил.
     *
     * Best effort: отказ DataStore стоит забытого выбора на следующем запуске, но не
     * падения приложения на текущем.
     */
    private fun rememberChoice() {
        val f = filterHolder.filter.value
        viewModelScope.launch { runCatching { settingsStore.rememberCollection(f) } }
    }

    fun onUndoDelete() {
        viewModelScope.launch {
            try {
                deleteUseCase.undo()
            } catch (t: CancellationException) {
                throw t
            } catch (_: Exception) {
                // Восстановление не удалось — pending остаётся, но молчать нельзя:
                // пользователь считает, что запись вернулась. Счётчик возвращает
                // снекбар с действием, иначе повторить было бы нечем.
                _error.tryEmit(ListError.Undo)
                undoFailures.value++
            }
        }
    }

    fun onUndoExpired() {
        // Истечение undo — фоновая уборка файла: сообщать пользователю нечего,
        // при отказе pending останется и будет очищен при следующем удалении.
        viewModelScope.launch { runCatching { deleteUseCase.expire() } }
    }

    /**
     * OCR этикетки → текст в строку поиска.
     * Guard от двойного запуска. Исключение → query не трогаем.
     *
     * [onFinished] вызывается, когда файл источника больше не нужен, — по нему
     * вызывающая сторона удаляет временный снимок (см. rememberCameraCapture).
     */
    fun onScanLabel(source: PhotoSource, onFinished: () -> Unit = {}) {
        if (scanning.value) {
            onFinished()
            return
        }
        scanning.value = true
        val scanSection = sectionRevision
        viewModelScope.launch {
            try {
                // Водяные знаки камеры («Shot on …», «Triple camera») впечатаны в
                // пиксели и иначе засоряют поиск. Чистим до склейки: она может
                // взять именно rawText, если модель полей не разобрала.
                val scan = labelRecognizer.recognize(source)
                if (scanSection != sectionRevision) return@launch
                val text = searchQueryOf(scan.copy(rawText = stripCameraWatermark(scan.rawText)))
                if (text.isEmpty()) {
                    // ML Kit успешно вернул пустоту (нечитаемая или кириллическая
                    // этикетка) — прежний запрос сохраняем, молчать нельзя: иначе
                    // снимок выглядел бы как сброс поиска.
                    _error.tryEmit(ListError.ScanEmpty)
                    return@launch
                }
                // queryFuzzy = true — поиск по фото толерантен к опечаткам OCR
                // (сброс при ручной правке).
                filterHolder.filter.value = filterHolder.filter.value.copy(
                    query = text,
                    queryFuzzy = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: RecognitionException) {
                // Причина дошла сюда, только когда и офлайн распознать не вышло:
                // у ключа, кредитов и сети разные действия пользователя.
                if (scanSection == sectionRevision) _error.tryEmit(ListError.Recognition(e.error))
            } catch (_: Exception) {
                // query не трогаем; OCR упал — оставляем прежний поиск и сообщаем,
                // иначе снимок этикетки просто «ничего не делает».
                if (scanSection == sectionRevision) _error.tryEmit(ListError.Scan)
            } finally {
                scanning.value = false
                // Даже если распознавание отменили — файл дочитан, он больше не нужен.
                withContext(NonCancellable) { onFinished() }
            }
        }
    }
}
