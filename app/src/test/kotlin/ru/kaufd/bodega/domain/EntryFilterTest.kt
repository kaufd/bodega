package ru.kaufd.bodega.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryFilterTest {
    @Test
    fun `категория коктейлей сбрасывает фильтр и сортировку по цене`() {
        val f = EntryFilter(category = Category.WINE, minPrice = 100, maxPrice = 500, sort = SortOrder.PRICE, minRating = 7).withCategory(Category.COCKTAIL)
        assertEquals(null, f.minPrice)
        assertEquals(null, f.maxPrice)
        assertEquals(SortOrder.DATE, f.sort)
        assertEquals(7, f.minRating)
    }

    @Test
    fun `пустой фильтр не активен`() {
        assertEquals(0, EntryFilter().activeFilterCount)
    }

    @Test
    fun `категория и поиск не считаются фильтрами`() {
        val f = EntryFilter(category = Category.WINE, query = "неббиоло")
        assertEquals(0, f.activeFilterCount)
    }

    @Test
    fun `каждый заданный фильтр считается один раз`() {
        val f = EntryFilter(
            types = setOf("IPA", "Стаут"),      // 1 (группа типов)
            countries = setOf("Италия"),          // 1
            minRating = 7, maxRating = 10,        // 1 (диапазон рейтинга)
            minPrice = 500,                        // 1 (диапазон цены)
        )
        assertEquals(4, f.activeFilterCount)
    }

    @Test
    fun `сортировка по умолчанию DATE не считается фильтром`() {
        assertEquals(0, EntryFilter(sort = SortOrder.DATE).activeFilterCount)
        assertEquals(1, EntryFilter(sort = SortOrder.RATING).activeFilterCount)
    }

    @Test
    fun `withCategory чистит категорийные фасеты, но не запрос и диапазоны`() {
        val f = EntryFilter(
            category = Category.WINE,
            colors = setOf("Красное"), wineTypes = setOf("Тихое"), sweetness = setOf("Сухое"),
            types = setOf("Виски"), styles = setOf("Ламбик"),
            countries = setOf("Италия"), minRating = 8, query = "бароло",
        ).withCategory(Category.BEER)

        assertEquals(Category.BEER, f.category)
        assertEquals(emptySet<String>(), f.colors)
        assertEquals(emptySet<String>(), f.wineTypes)
        assertEquals(emptySet<String>(), f.sweetness)
        assertEquals(emptySet<String>(), f.types)
        assertEquals(emptySet<String>(), f.styles)
        // Страна есть только у вина: «Пиво + Италия» — заведомо пустой фильтр.
        assertEquals(emptySet<String>(), f.countries)
        // Запрос и диапазоны от категории не зависят и сохраняются.
        assertEquals(8, f.minRating)
        assertEquals("бароло", f.query)
    }
}
