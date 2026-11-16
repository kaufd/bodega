package ru.kaufd.bodega.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryTest {

    private val spirit = Entry(
        category = Category.SPIRIT, name = "Лагавулин 16",
        type = "Виски (скотч)", country = "Шотландия", abvPercent = 43.0,
        priceRub = 7500, rating = 9, note = "Торф и йод",
    )

    @Test
    fun `withCategory очищает категорийные поля, включая type`() {
        val wine = spirit.withCategory(Category.WINE)

        assertEquals(Category.WINE, wine.category)
        // «Виски (скотч)» иначе попадал в searchText вина.
        assertNull(wine.type)
        assertNull(wine.country)
        assertNull(wine.style)
        assertTrue(wine.grapes.isEmpty())
    }

    @Test
    fun `withCategory очищает винные температуру подачи и год розлива`() {
        val wine = Entry(
            category = Category.WINE, name = "Cape Art Pinotage",
            servingTemp = "16-18°С", bottlingYear = 2024, vintageYear = 2023,
        )

        val beer = wine.withCategory(Category.BEER)

        assertNull(beer.servingTemp)
        assertNull(beer.bottlingYear)
        assertNull(beer.vintageYear)
    }

    @Test
    fun `withCategory сохраняет общие поля`() {
        val beer = spirit.withCategory(Category.BEER)

        assertEquals("Лагавулин 16", beer.name)
        assertEquals(43.0, beer.abvPercent!!, 0.0)
        assertEquals(7500, beer.priceRub)
        assertEquals(9, beer.rating)
        assertEquals("Торф и йод", beer.note)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `напиток не превращается в рецепт`() {
        spirit.withCategory(Category.COCKTAIL)
    }

    @Test
    fun `состав рецепта пропускает пустые строки формы`() {
        val entry = Entry(category = Category.COCKTAIL, name = "Тест", ingredientItems = listOf(
            CocktailIngredient("Джин", "30 мл"), CocktailIngredient(), CocktailIngredient("Лёд по вкусу", ""),
        ))
        assertEquals("Джин — 30 мл\nЛёд по вкусу", entry.ingredientsText)
    }

    @Test
    fun `withCategory на той же категории возвращает исходную запись`() {
        assertEquals(spirit, spirit.withCategory(Category.SPIRIT))
    }

    @Test
    fun `violations ловит пустое имя, рейтинг и крепость вне границ`() {
        val bad = Entry(category = Category.WINE, name = "  ", rating = 11, abvPercent = 97.0)

        assertEquals(
            setOf(EntryViolation.NAME_BLANK, EntryViolation.RATING, EntryViolation.ABV),
            bad.violations(),
        )
    }

    @Test
    fun `корректная запись без нарушений`() {
        assertTrue(spirit.violations().isEmpty())
    }

    @Test
    fun `границы включительно`() {
        val edge = spirit.copy(
            rating = EntryLimits.RATING.last,
            abvPercent = EntryLimits.ABV.endInclusive,
        )
        assertTrue(edge.violations().isEmpty())
    }
}
