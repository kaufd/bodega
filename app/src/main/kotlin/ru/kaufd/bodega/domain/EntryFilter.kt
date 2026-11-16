package ru.kaufd.bodega.domain

enum class SortOrder { RATING, PRICE, DATE }

data class EntryFilter(
    val category: Category? = null,
    val query: String = "",
    // true — запрос из OCR (поиск по фото): матчинг толерантен к опечаткам.
    // Любой ручной ввод сбрасывает флаг в false → строгий поиск.
    val queryFuzzy: Boolean = false,
    val types: Set<String> = emptySet(),        // SPIRIT — вид напитка
    val styles: Set<String> = emptySet(),       // BEER — стиль
    val colors: Set<String> = emptySet(),       // WINE — цвет
    val wineTypes: Set<String> = emptySet(),    // WINE — тихое/игристое/креплёное
    val sweetness: Set<String> = emptySet(),    // WINE — сладость
    val countries: Set<String> = emptySet(),
    val minRating: Int? = null,
    val maxRating: Int? = null,
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val sort: SortOrder = SortOrder.DATE,
    /**
     * Без категории (`category == null`) — только напитки: «Все» в коллекции рецептов
     * не показывает. Целиком журнал нужен лишь бэкапу, он и ставит флаг.
     */
    val includeRecipes: Boolean = false,
) {
    /** Число активных фильтров для бейджа. Категория (таб) и поиск не считаются. */
    val activeFilterCount: Int
        get() = listOf(
            types.isNotEmpty(),
            styles.isNotEmpty(),
            colors.isNotEmpty(),
            wineTypes.isNotEmpty(),
            sweetness.isNotEmpty(),
            countries.isNotEmpty(),
            minRating != null || maxRating != null,
            minPrice != null || maxPrice != null,
            sort != SortOrder.DATE,
        ).count { it }

    fun resetFilters(): EntryFilter = EntryFilter(category = category, query = query, queryFuzzy = queryFuzzy)

    /**
     * Смена категории: фасеты, значения которых живут только внутри прежней
     * категории (виды, стили, классификация вина, страна), сбрасываются.
     *
     * Страна — поле только вина ([Entry.country] обнуляется в `Entry.withCategory`,
     * редактор показывает поле лишь для WINE). Пережив переход WINE → BEER, она
     * дала бы заведомо пустой фильтр «Пиво + Италия».
     */
    fun withCategory(category: Category?): EntryFilter =
        if (category == this.category) this
        else copy(
            category = category,
            types = emptySet(),
            styles = emptySet(),
            colors = emptySet(),
            wineTypes = emptySet(),
            sweetness = emptySet(),
            countries = emptySet(),
            minPrice = if (category == Category.COCKTAIL) null else minPrice,
            maxPrice = if (category == Category.COCKTAIL) null else maxPrice,
            sort = if (category == Category.COCKTAIL && sort == SortOrder.PRICE) SortOrder.DATE else sort,
        )

    /**
     * Смена набора типов вина: сладость сужается до объединения их шкал — пара
     * «Игристое + Сухое» дала бы заведомо пустой результат. Пустой набор типов
     * ничего не сужает: шкала ещё не выбрана.
     */
    fun withWineTypes(wineTypes: Set<String>): EntryFilter {
        val allowed = wineTypes.flatMap { WineClassification.sweetnessFor(it) }.toSet()
        return copy(
            wineTypes = wineTypes,
            sweetness = if (wineTypes.isEmpty()) sweetness else sweetness.intersect(allowed),
        )
    }
}

/** Переключить значение в наборе фасета: было — снять, не было — добавить. */
fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
