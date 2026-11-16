package ru.kaufd.bodega.domain

import kotlinx.serialization.Serializable

@Serializable
data class CocktailIngredient(val name: String = "", val amount: String = "")

@Serializable
data class Entry(
    val id: Long = 0,
    val category: Category,
    val name: String,
    /** Относительные пути внутри files/. Первый — обложка (список, карточка, пост). */
    val photos: List<String> = emptyList(),
    val type: String? = null,        // SPIRIT — «Виски (скотч)» и т.п. (вино использует wineType/color/sweetness)
    val priceRub: Int? = null,
    val rating: Int? = null,         // 1..10
    val abvPercent: Double? = null,
    val note: String? = null,
    val tastedAt: Long? = null,      // epoch millis
    val createdAt: Long = 0,
    // категорийные
    val grapes: List<String> = emptyList(),  // WINE — один или несколько сортов
    val wineType: String? = null,    // WINE — Тихое / Игристое / Креплёное
    val color: String? = null,       // WINE — Красное / Белое / Розовое
    val sweetness: String? = null,   // WINE — шкала зависит от wineType
    val country: String? = null,     // WINE
    val vintageYear: Int? = null,    // WINE — год урожая винограда
    /** WINE — год бутилирования; в постах паблика идёт отдельной строкой от урожая. */
    val bottlingYear: Int? = null,
    /** WINE — диапазон строкой («16-18°С»), а не число: в постах он всегда «от-до». */
    val servingTemp: String? = null,
    val style: String? = null,       // BEER
    val preparation: String? = null, // COCKTAIL — способ приготовления
    /** COCKTAIL — состав. Пустые строки формы живут только в редакторе: при записи в базу их отбрасывает маппинг. */
    val ingredientItems: List<CocktailIngredient> = emptyList(),
) {
    /** Состав по строке на ингредиент: «Джин — 30 мл». Общий для карточки, поста и поиска. */
    val ingredientsText: String
        get() = ingredientItems.mapNotNull { item ->
            listOf(item.name.trim(), item.amount.trim()).filter { it.isNotEmpty() }
                .joinToString(" — ").takeIf { it.isNotEmpty() }
        }.joinToString("\n")

    /**
     * Смена категории напитка: общие поля остаются, категорийные обнуляются.
     * Единственная точка нормализации инварианта — размазанная по UI, она
     * забывает поля (например, «Виски» от SPIRIT утекало в searchText вина).
     * Рецепт в напиток и обратно не превращается: у них разные разделы и формы.
     */
    fun withCategory(category: Category): Entry {
        require(category.isRecipe == this.category.isRecipe) { "Рецепт и напиток не меняются местами: $this → $category" }
        return if (category == this.category) this
        else copy(
            category = category,
            type = null,
            grapes = emptyList(), wineType = null, color = null, sweetness = null,
            country = null, vintageYear = null, bottlingYear = null, servingTemp = null,
            style = null,
        )
    }

    /**
     * Смена типа вина: сладость принадлежит шкале своего типа
     * ([WineClassification.sweetnessByType]), и несовместимая — снимается. Иначе
     * «Сухое» от тихого переезжает в игристое и уходит в searchText и в хэштег поста.
     */
    fun withWineType(wineType: String?): Entry =
        copy(
            wineType = wineType,
            sweetness = sweetness?.takeIf { it in WineClassification.sweetnessFor(wineType) },
        )
}
