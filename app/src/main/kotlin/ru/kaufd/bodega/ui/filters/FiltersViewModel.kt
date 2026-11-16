package ru.kaufd.bodega.ui.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.rememberCollection
import ru.kaufd.bodega.domain.SettingsStore
import ru.kaufd.bodega.domain.WineClassification
import ru.kaufd.bodega.domain.WineFacets
import ru.kaufd.bodega.ui.state.FilterStateHolder
import javax.inject.Inject

data class FiltersState(
    val draft: EntryFilter = EntryFilter(),
    /** Использованные типы, сгруппированные по категориям (порядок — как в enum). */
    val availableTypes: Map<Category, List<String>> = emptyMap(),
    /** Использованные цвет/тип/сладость среди вин. */
    val wineFacets: WineFacets = WineFacets(),
    /** Использованные стили среди пива. */
    val availableStyles: List<String> = emptyList(),
    val availableCountries: List<String> = emptyList(),
    val resultCount: Int = 0,
    /** Отказ Room: справочники и счётчик показать нечем, но черновик фильтра жив. */
    val loadFailed: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class FiltersViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val filterHolder: FilterStateHolder,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val draft = MutableStateFlow(filterHolder.filter.value)

    /** Счётчик попыток пересобрать подписки после отказа Room. */
    private val retry = MutableStateFlow(0)

    // Отказ Room завершает поток: без пересборки экран замер бы на fallback-состоянии
    // и перестал реагировать на правку черновика. flatMapLatest по retry
    // пересоздаёт всю цепочку — Flow после ошибки сам не оживает.
    // Слайдер рейтинга шлёт onValueChange на каждый пиксель перетаскивания. Без
    // паузы каждый такой шаг перезапускал бы запрос по всей таблице ради одного
    // числа; сам черновик (чипы, поля) при этом рисуется без задержки.
    //
    // Первое значение проходит без паузы: debounce задерживает и его, а две ветки
    // combine ниже висят именно на settled — экран открывался бы на SETTLE_MS
    // пустым (нулевой счётчик, нет секций), показывая seed-состояние stateIn.
    private val settled = draft.withIndex()
        .debounce { (i, _) -> if (i == 0) 0L else SETTLE_MS }
        .map { it.value }

    val state: StateFlow<FiltersState> = retry.flatMapLatest { combine(
        draft,
        settled.flatMapLatest { repository.observeEntries(it).map { l -> l.size } },
        // Вместе с результатом несём категорию, под которую он запрошен: она
        // отстаёт от черновика на SETTLE_MS, и без неё список видов не с чем сверить.
        settled.flatMapLatest { s -> repository.observeUsedTypesGrouped(s.category).map { s.category to it } },
        repository.observeWineFacets(),
        // Страны и стили — одним потоком: у combine выше уже предельная арность.
        combine(repository.observeUsedCountries(), repository.observeUsedStyles(), ::Pair),
    ) { d, count, (typesFor, types), facets, (countries, styles) ->
        // Спец-секции строго по категории: «Все» — без них.
        val wineVisible = d.category == Category.WINE
        // Справочник видов запрашивается по задебаунсенной категории, а чип
        // переключается мгновенно. Показываем список, только когда он запрошен под
        // ту же категорию, что выбрана сейчас: иначе под вкладкой «Крепкое» на
        // SETTLE_MS оставались винные и пивные виды из запроса для «Все» (и наоборот).
        val spiritVisible = d.category == Category.SPIRIT && typesFor == Category.SPIRIT
        // «Сладость» сужается до шкал выбранных типов; тип не выбран — все используемые.
        val allowedSweetness = d.wineTypes
            .takeIf { it.isNotEmpty() }
            ?.flatMap { WineClassification.sweetnessFor(it) }
            ?.toSet()
        val shownFacets = when {
            !wineVisible -> WineFacets()
            allowedSweetness == null -> facets
            else -> facets.copy(sweetness = facets.sweetness.filter { it in allowedSweetness })
        }
        FiltersState(
            draft = d,
            availableTypes = if (spiritVisible) types else emptyMap(),
            wineFacets = shownFacets,
            // Стиль есть только у пива — и только тот, что уже встречается в погребе:
            // фильтр по неиспользованному стилю дал бы гарантированно пустой результат.
            availableStyles = if (d.category == Category.BEER) styles else emptyList(),
            // Страна есть только у вина: в разделах пива и крепкого фильтр по ней
            // дал бы гарантированно пустой результат.
            availableCountries = if (wineVisible || d.category == null) countries else emptyList(),
            resultCount = count,
        )
    }
        // Отказ Room не должен уносить корутину stateIn вместе с приложением.
        // После ошибки продолжаем отдавать состояние по черновику: иначе внутренний
        // combine завершался бы, экран замирал на снимке, и пользователь правил бы
        // фильтры вслепую — вплоть до применения невидимых изменений.
        .catch { e ->
            if (e is CancellationException) throw e
            emitAll(draft.map { FiltersState(draft = it, loadFailed = true) })
        }
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        FiltersState(draft = filterHolder.filter.value),
    )

    /** Повторная подписка на справочники и счётчик после отказа Room. */
    fun onRetryLoad() { retry.value++ }

    /**
     * Фильтр применён, экран пора закрывать. Отдельный поток, а не поле FiltersState:
     * combine выше уже на предельной арности, а состояние вместо колбэка нужно по той
     * же причине, что в редакторе и карточке — ViewModel переживает пересоздание
     * Activity, и переданная навигация указывала бы на уничтоженный NavController.
     */
    private val _applied = MutableStateFlow(false)
    val applied: StateFlow<Boolean> = _applied.asStateFlow()

    fun onDraftChange(update: (EntryFilter) -> EntryFilter) {
        draft.value = update(draft.value)
    }

    /**
     * Имя не `apply`: у метода без аргументов вызов `vm.apply { … }` молча
     * разрешается в одноимённую функцию стандартной библиотеки.
     */
    fun applyDraft() {
        filterHolder.filter.value = draft.value
        // Фильтр уже применён; запоминание — best effort. Отказ DataStore не должен
        // ни ронять приложение, ни оставлять экран открытым поверх применённого фильтра.
        viewModelScope.launch {
            runCatching { settingsStore.rememberCollection(draft.value) }
            _applied.value = true
        }
    }

    fun reset() {
        draft.value = draft.value.resetFilters()
    }

    private companion object {
        /** Пауза перед пересчётом счётчика и справочников при перетаскивании слайдера. */
        const val SETTLE_MS = 120L
    }
}
