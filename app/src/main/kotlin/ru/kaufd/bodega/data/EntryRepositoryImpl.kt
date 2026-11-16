package ru.kaufd.bodega.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import ru.kaufd.bodega.domain.BeerClassification
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryLimits
import ru.kaufd.bodega.domain.EntryRepository
import ru.kaufd.bodega.domain.PhotoStorage
import ru.kaufd.bodega.domain.WineClassification
import ru.kaufd.bodega.domain.WineFacets
import ru.kaufd.bodega.domain.fuzzyTermMatches
import ru.kaufd.bodega.domain.isShortNumericNoise
import ru.kaufd.bodega.domain.splitWords
import ru.kaufd.bodega.domain.tokenizeQuery
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepositoryImpl @Inject constructor(
    private val dao: EntryDao,
    private val photoStorage: PhotoStorage,
) : EntryRepository {

    override fun observeEntries(filter: EntryFilter): Flow<List<Entry>> =
        dao.observeFiltered(
            category = filter.category?.name,
            includeRecipes = filter.includeRecipes,
            typesEmpty = filter.types.isEmpty(),
            types = filter.types.toList(),
            stylesEmpty = filter.styles.isEmpty(),
            styles = filter.styles.toList(),
            colorsEmpty = filter.colors.isEmpty(),
            colors = filter.colors.toList(),
            wineTypesEmpty = filter.wineTypes.isEmpty(),
            wineTypes = filter.wineTypes.toList(),
            sweetnessEmpty = filter.sweetness.isEmpty(),
            sweetness = filter.sweetness.toList(),
            countriesEmpty = filter.countries.isEmpty(),
            countries = filter.countries.toList(),
            minRating = filter.minRating,
            maxRating = filter.maxRating,
            minPrice = filter.minPrice,
            maxPrice = filter.maxPrice,
            sort = filter.sort.name,
        ).map { list ->
            // Текстовый поиск — в памяти: токенизированный матчинг по searchText.
            // При пустом query — без пост-обработки, как прежде.
            val query = filter.query.trim()
            if (query.isEmpty()) {
                list.map { it.toDomain() }
            } else {
                val terms = searchTerms(query)
                // Порог ≥ 1: оставить записи, где searchText содержит хотя бы один терм.
                // Ранжирование — по числу совпавших термов ↓, tie-break — SQL-сортировка
                // (sortedWith стабилен, сохраняет порядок из SQL).
                list.map {
                    // Длинный OCR-текст × большая коллекция — заметная работа;
                    // отменённый (устаревший) запрос не должен её доделывать.
                    currentCoroutineContext().ensureActive()
                    it to matchCount(it.searchText, terms, filter.queryFuzzy)
                }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                    .map { it.first.toDomain() }
            }
        }
            // Поток собирается в viewModelScope (main thread), а фильтрация,
            // сортировка и fuzzy-сравнение — не для UI-потока.
            .flowOn(Dispatchers.Default)

    override fun observeCount(category: Category?): Flow<Int> =
        dao.observeCount(category?.name)

    override fun observeById(id: Long): Flow<Entry?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: Long): Entry? = dao.getById(id)?.toDomain()

    override suspend fun upsert(entry: Entry): Long {
        validate(entry)
        val rowId = dao.upsert(entry.toEntity())
        return if (rowId > 0) rowId else entry.id
    }

    override suspend fun insertAll(entries: List<Entry>) {
        // Проверяем всю пачку до первой вставки: транзакция всё равно откатит
        // половину, а падение до записи оставляет журнал нетронутым.
        entries.forEach(::validate)
        dao.insertAll(entries.map { it.toEntity() })
    }

    /**
     * Доменные лимиты на границе записи в БД — единая точка для всех источников.
     *
     * Проверка живёт здесь, а не в редакторе: форма не единственный автор записей,
     * импорт приносит их из файла, который писали не мы (см. `ImportBackupUseCase`).
     */
    private fun validate(entry: Entry) {
        entry.rating?.let { require(it in EntryLimits.RATING) { "Рейтинг должен быть от ${EntryLimits.RATING.first} до ${EntryLimits.RATING.last}, получено $it" } }
        entry.abvPercent?.let { require(it in EntryLimits.ABV) { "Крепость должна быть от ${EntryLimits.ABV.start.toInt()} до ${EntryLimits.ABV.endInclusive.toInt()}%, получено $it" } }
        require(entry.photos.size <= EntryLimits.PHOTOS_MAX) { "Фотографий не больше ${EntryLimits.PHOTOS_MAX}, получено ${entry.photos.size}" }
    }

    override suspend fun delete(id: Long): Entry? = dao.takeById(id)?.toDomain()

    override suspend fun restore(entry: Entry) = dao.insert(entry.toEntity())

    // fold, а не all/any: удалить нужно каждый файл, а не остановиться на первом отказе.
    override suspend fun purgePhotos(paths: List<String>): Boolean =
        paths.fold(true) { ok, path -> photoStorage.delete(path) && ok }

    /**
     * Строже, чем маппинг записи: по этому списку `OrphanPhotoSweeper` решает, какие
     * файлы на диске никому не нужны. Битая колонка, понятая как «фотографий нет»,
     * превратила бы живые снимки записи в сирот и молча стёрла их с диска — поэтому
     * здесь отказ разбора, а не пустой список. Уборка исключение ловит и в этот
     * проход не удаляет ничего.
     */
    override suspend fun usedPhotoPaths(): List<String> =
        dao.usedPhotoJson().flatMap {
            decodeStringsOrNull(it) ?: error("Не прочитать список фотографий записи: $it")
        }

    override suspend fun recentNotes(category: Category, limit: Int): List<String> =
        dao.recentNotes(category.name, limit)

    override fun observeUsedTypesGrouped(category: Category?): Flow<Map<Category, List<String>>> =
        dao.observeUsedTypesGrouped(category?.name).map { rows ->
            rows.groupBy({ Category.valueOf(it.category) }, { it.type })
                // Порядок категорий — как в enum (Вино, Пиво, Крепкое), не алфавитный.
                .toSortedMap(compareBy { it.ordinal })
        }

    override fun observeUsedCountries(): Flow<List<String>> =
        dao.observeUsedCountries()

    override fun observeWineFacets(): Flow<WineFacets> = combine(
        dao.observeUsedWineColors(),
        dao.observeUsedWineTypes(),
        dao.observeUsedWineSweetness(),
    ) { colors, types, sweetness ->
        WineFacets(
            colors = colors.sortByCanonical(WineClassification.colors),
            types = types.sortByCanonical(WineClassification.types),
            sweetness = sweetness.sortByCanonical(WineClassification.allSweetness),
        )
    }

    override fun observeUsedStyles(): Flow<List<String>> =
        dao.observeUsedStyles().map { it.sortByCanonical(BeerClassification.styles) }
}

/** Сортирует значения по каноническому [order]; отсутствующие в справочнике — в конец. */
private fun List<String>.sortByCanonical(order: List<String>): List<String> =
    sortedBy { order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }

/**
 * Термы для матчинга запроса [query] (уже trim). Обычно — токены [tokenizeQuery].
 * Если токенизация дала пусто (запрос из коротких символов, напр. набор по буквам
 * «ба»), падаем на подстрочный поиск по всему запросу — чтобы инкрементальный ввод
 * не показывал пустой экран. Исключение: чисто числовой короткий запрос («40», «700»)
 * — шум крепости/объёма, остаётся без совпадений, как задумано.
 */
private fun searchTerms(query: String): List<String> {
    val tokens = tokenizeQuery(query)
    if (tokens.isNotEmpty()) return tokens
    val q = query.lowercase()
    return if (isShortNumericNoise(q)) emptyList() else listOf(q)
}

/**
 * Число термов из [terms], которые содержатся в [searchText] как подстрока
 * (консистентно с прежним SQL `LIKE '%term%'`). `searchText` в нижнем регистре.
 *
 * При [fuzzy] (поиск по фото) терм, не совпавший строго, дополнительно проверяется
 * толерантно ([fuzzyTermMatches]) против слов `searchText` — ловит опечатки OCR.
 * Строгое совпадение имеет приоритет, поэтому fuzzy — только fallback.
 */
private fun matchCount(searchText: String, terms: List<String>, fuzzy: Boolean): Int {
    if (!fuzzy) return terms.count { searchText.contains(it) }
    val words by lazy { splitWords(searchText) }
    return terms.count { term ->
        searchText.contains(term) || words.any { fuzzyTermMatches(term, it) }
    }
}
