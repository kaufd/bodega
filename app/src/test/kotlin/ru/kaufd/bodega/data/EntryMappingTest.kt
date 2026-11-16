package ru.kaufd.bodega.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.CocktailIngredient

/**
 * Маппинг domain ↔ Room. Забытое поле в [toEntity] или [toDomain] компиляцию не ломает —
 * запись просто молча теряет значение при сохранении, и заметно это только на устройстве.
 */
class EntryMappingTest {

    @Test
    fun `состав, записанный первой сборкой с префиксом, читается`() {
        val entity = Entry(category = Category.COCKTAIL, name = "Негрони", preparation = "Смешать со льдом").toEntity()
            .copy(ingredients = """@cocktail-ingredients:[{"name":"Джин","amount":"30 мл"}]""")
        assertEquals(listOf(CocktailIngredient("Джин", "30 мл")), entity.toDomain().ingredientItems)
    }

    @Test
    fun `пустые строки формы в базу не попадают`() {
        val entry = Entry(category = Category.COCKTAIL, name = "Негрони", ingredientItems = listOf(
            CocktailIngredient("Джин", "30 мл"), CocktailIngredient(" ", ""),
        ))
        assertEquals(listOf(CocktailIngredient("Джин", "30 мл")), entry.toEntity().toDomain().ingredientItems)
        assertEquals(null, entry.copy(ingredientItems = listOf(CocktailIngredient())).toEntity().ingredients)
    }

    @Test
    fun `напиток без состава не добавляет пробел в searchText`() {
        assertEquals("бароло", buildSearchText(Entry(category = Category.WINE, name = "Бароло")))
    }

    @Test
    fun `строки ингредиентов сохраняют количество и доступны поиску`() {
        val entry = Entry(category = Category.COCKTAIL, name = "Негрони", ingredientItems = listOf(
            CocktailIngredient("Джин", "30 мл"), CocktailIngredient("Кампари", "30 мл"),
        ))
        assertEquals(entry, entry.toEntity().toDomain())
        assertEquals(true, buildSearchText(entry).contains("кампари"))
        assertEquals("Джин — 30 мл\nКампари — 30 мл", entry.toEntity().toDomain().ingredientsText)
    }

    @Test
    fun `запись переживает путь в базу и обратно без потерь`() {
        val entry = Entry(
            id = 7,
            category = Category.WINE,
            name = "Cape Art Pinotage",
            photos = listOf("photos/a.jpg"),
            priceRub = 600,
            rating = 8,
            abvPercent = 13.5,
            note = "Вишня и слива",
            tastedAt = 1_700_000_000_000,
            createdAt = 1_600_000_000_000,
            grapes = listOf("Пинотаж"),
            wineType = "Тихое",
            color = "Красное",
            sweetness = "Сухое",
            country = "ЮАР",
            vintageYear = 2023,
            bottlingYear = 2024,
            servingTemp = "16-18°С",
        )

        assertEquals(entry, entry.toEntity().toDomain())
    }
}
