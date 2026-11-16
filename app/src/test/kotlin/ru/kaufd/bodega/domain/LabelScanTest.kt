package ru.kaufd.bodega.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelScanTest {

    @Test
    fun `разбирает ответ модели в структуру`() {
        val scan = parseLabelScan(
            """
            {
              "rawText": "ВЕДЕРНИКОВЪ Красностоп Золотовский 2019",
              "name": "Красностоп Золотовский",
              "wineType": "Тихое"
            }
            """.trimIndent()
        )

        assertEquals("ВЕДЕРНИКОВЪ Красностоп Золотовский 2019", scan.rawText)
        assertEquals("Красностоп Золотовский", scan.name)
        assertEquals("Тихое", scan.wineType)
    }

    @Test
    fun `поисковый запрос собирается из полей, а не из всей простыни`() {
        val scan = LabelScan(
            rawText = "ВЕДЕРНИКОВЪ\nКрасностоп Золотовский\n2019\nСУХОЕ\n13,5% об.\n0,75 л",
            name = "Красностоп Золотовский",
            wineType = "Тихое",
        )

        // Год не идёт в запрос: он отсекал бы ту же бутылку другого урожая.
        assertEquals("Красностоп Золотовский Тихое", searchQueryOf(scan))
    }

    @Test
    fun `пустое название - запрос берётся из текста этикетки`() {
        // Модель просят вернуть null, но она нередко отдаёт "": без этой проверки
        // запрос выходил бы пустым при годном rawText.
        val scan = LabelScan(rawText = "BAROLO RISERVA 2015", name = "", wineType = "")

        assertEquals("BAROLO RISERVA 2015", searchQueryOf(scan))
    }

    @Test
    fun `тип без названия в запрос не идёт`() {
        // «Стаут» с queryFuzzy выдал бы всё пиво этого стиля как найденную бутылку.
        val scan = LabelScan(rawText = "GUINNESS DRAUGHT", style = "Стаут")

        assertEquals("GUINNESS DRAUGHT", searchQueryOf(scan))
    }

    @Test
    fun `без разобранных полей запрос берётся из текста одной строкой`() {
        val scan = LabelScan(rawText = "  Бароло\n\n  Ризерва  ")

        assertEquals("Бароло Ризерва", searchQueryOf(scan))
    }

    @Test
    fun `JSON в markdown-ограждении разбирается`() {
        val scan = parseLabelScan("```json\n{\"name\": \"Шардоне\"}\n```")

        assertEquals("Шардоне", scan.name)
    }

    @Test
    fun `мусор вместо JSON даёт пустой результат, а не исключение`() {
        assertTrue(parseLabelScan("Извините, я не могу прочитать эту этикетку.").isEmpty)
    }

    @Test
    fun `неизвестные поля и null не роняют разбор`() {
        val scan = parseLabelScan(
            """{"name": "Каберне", "region": "Кубань", "wineType": null, "rawText": null}"""
        )

        assertEquals("Каберне", scan.name)
        assertNull(scan.wineType)
        // coerceInputValues: null в поле с дефолтом заменяется дефолтом, а не падает.
        assertEquals("", scan.rawText)
    }
}
