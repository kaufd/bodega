package ru.kaufd.bodega.domain

enum class Category {
    WINE, BEER, SPIRIT, COCKTAIL;

    /**
     * Рецепт, а не напиток: живёт в своём разделе, без цены, крепости и даты
     * дегустации, в коллекцию и её счётчики не входит.
     */
    val isRecipe: Boolean get() = this == COCKTAIL

    companion object {
        /** Категории раздела «Коллекция» — сегменты, чипы фильтра, выбор в редакторе. */
        val drinks: List<Category> = entries.filterNot { it.isRecipe }

        /** Безопасный разбор имени enum: `null`/невалидное → `null`. */
        fun parseOrNull(raw: String?): Category? =
            raw?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
