package ru.kaufd.bodega.domain

/** Предзаполненные подсказки; свободный ввод всегда разрешён. */
object Suggestions {
    private val types: Map<Category, List<String>> = mapOf(
        Category.SPIRIT to listOf(
            "Виски", "Бурбон", "Ром", "Текила",
            "Кальвадос", "Коньяк",
        ),
    )

    /** Подсказки вида для категории; у категорий без своего списка — пусто. */
    fun typesFor(category: Category): List<String> = types[category].orEmpty()

    val grapes: List<String> = listOf(
        "Каберне Совиньон", "Мерло", "Пино Нуар",
        "Сира", "Темпранильо", "Шардоне", "Совиньон Блан", "Рислинг", "Примитиво",
    )

    val countries: List<String> = listOf(
        "Италия", "Франция", "Испания", "Португалия", "Германия",
        "Австрия", "Россия", "Чили", "Аргентина", "ЮАР", "Австралия", "США",
    )
}
