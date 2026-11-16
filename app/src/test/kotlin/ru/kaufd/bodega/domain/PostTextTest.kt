package ru.kaufd.bodega.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostTextTest {

    @Test
    fun `пост рецепта содержит приготовление, но не скрытую старую заметку`() {
        val entry = Entry(category = Category.COCKTAIL, name = "Негрони", preparation = "Смешать", note = "Старая заметка", rating = 8)
        val text = buildPostText(entry)
        assertTrue(text.contains("Смешать"))
        assertTrue(text.contains("ОЦЕНКА 8 из"))
        assertFalse(text.contains("Старая заметка"))
        assertFalse("рецепт — не дегустация: $text", text.contains("#дегустация"))
        assertFalse("у рецепта нет цены: $text", text.contains("удовольствие/цена"))
    }

    @Test
    fun `вино собирается в текст для публикации`() {
        val entry = Entry(
            category = Category.WINE,
            name = "Cape Art Pinotage",
            country = "ЮАР",
            grapes = listOf("Пинотаж"),
            wineType = "Тихое",
            color = "Красное",
            sweetness = "Сухое",
            abvPercent = 13.0,
            vintageYear = 2025,
            priceRub = 600,
            note = "ДЕГУСТАЦИЯ. Вишня и слива.",
            rating = 8,
        )

        assertEquals(
            """
            CAPE ART PINOTAGE

            Страна происхождения ЮАР
            Сорт винограда Пинотаж
            Цвет красное
            Сладость сухое
            Крепость 13%
            Урожай 2025
            Цена 600р

            ДЕГУСТАЦИЯ. Вишня и слива.

            ОЦЕНКА (удовольствие/цена) 8 из 10

            #дегустация #вино #винодегустация #люблювино #питьвино #красноевино #красное_сухое_вино #юар #пинотаж
            """.trimIndent(),
            buildPostText(entry),
        )
    }

    @Test
    fun `температура подачи и год розлива идут вокруг урожая`() {
        val entry = Entry(
            category = Category.WINE,
            name = "Cape Art Pinotage",
            abvPercent = 13.0,
            servingTemp = "16-18°С",
            vintageYear = 2023,
            bottlingYear = 2024,
            priceRub = 600,
        )

        // Порядок полей: крепость → температура → урожай → розлив → цена.
        assertEquals(
            """
            CAPE ART PINOTAGE

            Крепость 13%
            Температура подачи 16-18°С
            Урожай 2023
            Год розлива 2024
            Цена 600р

            #дегустация #вино #винодегустация #люблювино #питьвино
            """.trimIndent(),
            buildPostText(entry),
        )
    }

    @Test
    fun `у пива свой стиль и свои теги`() {
        val entry = Entry(
            category = Category.BEER,
            name = "Таркос Лагер",
            style = "Лагер",
            abvPercent = 4.5,
            priceRub = 180,
            note = "Освежающий добротный лагерок.",
            rating = 10,
        )

        assertEquals(
            """
            ТАРКОС ЛАГЕР

            Стиль Лагер
            Крепость 4.5%
            Цена 180р

            Освежающий добротный лагерок.

            ОЦЕНКА (удовольствие/цена) 10 из 10

            #дегустация #пиво #люблюпиво #питьпиво #крафт #лагер
            """.trimIndent(),
            buildPostText(entry),
        )
    }

    @Test
    fun `у крепкого тип вместо сорта`() {
        val entry = Entry(
            category = Category.SPIRIT,
            name = "Don Julio 1942",
            type = "Текила",
            abvPercent = 38.0,
            priceRub = 8900,
        )

        assertEquals(
            """
            DON JULIO 1942

            Тип Текила
            Крепость 38%
            Цена 8900р

            #дегустация #алкоголь #текила
            """.trimIndent(),
            buildPostText(entry),
        )
    }

    @Test
    fun `у игристого тег по типу, а не по сладости`() {
        val entry = Entry(
            category = Category.WINE,
            name = "Мысхако Брют",
            wineType = "Игристое",
            color = "Белое",
            sweetness = "Brut",
            grapes = listOf("Совиньон Блан"),
        )

        assertEquals(
            """
            МЫСХАКО БРЮТ

            Сорт винограда Совиньон Блан
            Цвет белое
            Сладость brut

            #дегустация #вино #винодегустация #люблювино #питьвино #белоевино #игристое_вино #совиньонблан
            """.trimIndent(),
            buildPostText(entry),
        )
    }

    @Test
    fun `запись без полей не даёт пустых блоков`() {
        val entry = Entry(category = Category.WINE, name = "Без полей")

        assertEquals(
            """
            БЕЗ ПОЛЕЙ

            #дегустация #вино #винодегустация #люблювино #питьвино
            """.trimIndent(),
            buildPostText(entry),
        )
    }
}
