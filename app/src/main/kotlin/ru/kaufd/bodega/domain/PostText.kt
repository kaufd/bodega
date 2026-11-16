package ru.kaufd.bodega.domain

/** Формирует текст записи для отправки в другие приложения. */
fun buildPostText(entry: Entry): String {
    val fields = listOfNotNull(
        entry.country?.let { "Страна происхождения $it" },
        entry.grapes.takeIf { it.isNotEmpty() }?.let { "Сорт винограда ${it.joinToString(", ")}" },
        entry.style?.let { "Стиль $it" },
        entry.type?.let { "Тип $it" },
        entry.color?.let { "Цвет ${it.lowercase()}" },
        entry.sweetness?.let { "Сладость ${it.lowercase()}" },
        entry.abvPercent?.let { "Крепость ${formatAbv(it)}%" },
        entry.servingTemp?.let { "Температура подачи $it" },
        entry.vintageYear?.let { "Урожай $it" },
        entry.bottlingYear?.let { "Год розлива $it" },
        entry.priceRub?.let { "Цена ${it}р" },
    ).joinToString("\n")

    return listOf(
        entry.name.uppercase(),
        fields,
        entry.ingredientsText.takeIf { it.isNotBlank() }?.let { "Ингредиенты\n$it" }.orEmpty(),
        entry.preparation?.let { "Приготовление\n$it" }.orEmpty(),
        if (entry.category == Category.COCKTAIL) "" else entry.note.orEmpty(),
        // «Цена» в подписи — про бутылку; у рецепта цены нет, оценивается только вкус.
        entry.rating?.let {
            val label = if (entry.category.isRecipe) "ОЦЕНКА" else "ОЦЕНКА (удовольствие/цена)"
            "$label $it из ${EntryLimits.RATING.last}"
        }.orEmpty(),
        hashtagsOf(entry).joinToString(" "),
    ).filter { it.isNotBlank() }.joinToString("\n\n")
}

/** `13.0` → «13», `13.5` → «13.5»: хвостовой ноль выглядит опечаткой. Общий для поста и карточки. */
fun formatAbv(abv: Double): String = abv.toString().trimEnd('0').trimEnd('.')

/** Теги категории и характеристики записи. */
private fun hashtagsOf(entry: Entry): List<String> =
    listOfNotNull("#дегустация".takeUnless { entry.category.isRecipe }) + when (entry.category) {
        Category.WINE -> listOf("#вино", "#винодегустация", "#люблювино", "#питьвино") +
            listOfNotNull(entry.color?.let { "#${it.lowercase()}вино" }) +
            listOfNotNull(wineKindTag(entry)) +
            listOfNotNull(entry.country?.let { tagOf(it) }) +
            entry.grapes.map { tagOf(it) }

        Category.BEER -> listOf("#пиво", "#люблюпиво", "#питьпиво", "#крафт") +
            listOfNotNull(entry.style?.let { tagOf(it) })

        Category.SPIRIT -> listOf("#алкоголь") + listOfNotNull(entry.type?.let { tagOf(it) })
        Category.COCKTAIL -> listOf("#коктейль", "#рецепт")
    }

/**
 * Игристое помечается типом (`#игристое_вино`), а тихое и креплёное —
 * парой «цвет + сладость» (`#красное_сухое_вино`).
 */
private fun wineKindTag(entry: Entry): String? =
    if (entry.wineType == "Игристое") "#игристое_вино"
    else entry.color?.let { color ->
        entry.sweetness?.let { "#${color.lowercase()}_${it.lowercase()}_вино" }
    }

/** Производный тег: нижний регистр без пробелов — «Каберне Совиньон» → `#кабернесовиньон`. */
private fun tagOf(value: String): String = "#${value.lowercase().replace(" ", "")}"
