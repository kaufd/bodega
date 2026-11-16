package ru.kaufd.bodega.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry

// Robolectric даёт Context на JVM (без эмулятора): Android-артефакт Room
// умеет строить БД только через Context. manifest = NONE — чтобы не поднимать
// @HiltAndroidApp BodegaApp. Реальная БД — BundledSQLiteDriver.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class EntryDaoTest {
    private lateinit var db: BodegaDatabase
    private lateinit var dao: EntryDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder<BodegaDatabase>(
            ApplicationProvider.getApplicationContext()
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dao = db.entryDao()
    }

    @After
    fun teardown() = db.close()

    private fun entity(
        name: String,
        category: String = "WINE",
        type: String? = null,
        style: String? = null,
        country: String? = null,
        rating: Int? = null,
        priceRub: Int? = null,
        createdAt: Long = 0,
        note: String? = null,
    ) = Entry(
        id = 0, category = Category.valueOf(category), name = name, type = type,
        priceRub = priceRub, rating = rating, createdAt = createdAt,
        country = country, style = style, note = note,
    ).toEntity()

    @Test
    fun `фильтр по категории`() = runTest {
        dao.upsert(entity("Бароло", category = "WINE"))
        dao.upsert(entity("Гиннесс", category = "BEER"))
        val wines = dao.observeFiltered(
            category = "WINE", includeRecipes = false, typesEmpty = true, types = emptyList(),
            stylesEmpty = true, styles = emptyList(),
            colorsEmpty = true, colors = emptyList(),
            wineTypesEmpty = true, wineTypes = emptyList(),
            sweetnessEmpty = true, sweetness = emptyList(),
            countriesEmpty = true, countries = emptyList(),
            minRating = null, maxRating = null, minPrice = null, maxPrice = null,
            sort = "DATE",
        ).first()
        assertEquals(listOf("Бароло"), wines.map { it.name })
    }

    @Test
    fun `фильтр по списку типов и диапазону рейтинга`() = runTest {
        dao.upsert(entity("A", type = "IPA", rating = 8))
        dao.upsert(entity("B", type = "Стаут", rating = 5))
        dao.upsert(entity("C", type = "IPA", rating = 4))
        val result = dao.observeFiltered(
            category = null, includeRecipes = false, typesEmpty = false, types = listOf("IPA"),
            stylesEmpty = true, styles = emptyList(),
            colorsEmpty = true, colors = emptyList(),
            wineTypesEmpty = true, wineTypes = emptyList(),
            sweetnessEmpty = true, sweetness = emptyList(),
            countriesEmpty = true, countries = emptyList(),
            minRating = 5, maxRating = 10, minPrice = null, maxPrice = null,
            sort = "DATE",
        ).first()
        assertEquals(listOf("A"), result.map { it.name })
    }

    @Test
    fun `фильтр по стилю пива`() = runTest {
        dao.upsert(entity("A", category = "BEER", style = "IPA"))
        dao.upsert(entity("B", category = "BEER", style = "Стаут"))
        dao.upsert(entity("C", category = "BEER", style = "IPA"))
        val result = dao.observeFiltered(
            category = "BEER", includeRecipes = false, typesEmpty = true, types = emptyList(),
            stylesEmpty = false, styles = listOf("IPA"),
            colorsEmpty = true, colors = emptyList(),
            wineTypesEmpty = true, wineTypes = emptyList(),
            sweetnessEmpty = true, sweetness = emptyList(),
            countriesEmpty = true, countries = emptyList(),
            minRating = null, maxRating = null, minPrice = null, maxPrice = null,
            sort = "DATE",
        ).first()
        assertEquals(listOf("A", "C"), result.map { it.name }.sorted())
    }

    @Test
    fun `сортировка по рейтингу убывание`() = runTest {
        dao.upsert(entity("низкий", rating = 3, createdAt = 3))
        dao.upsert(entity("высокий", rating = 9, createdAt = 1))
        val sorted = dao.observeFiltered(
            category = null, includeRecipes = false, typesEmpty = true, types = emptyList(),
            stylesEmpty = true, styles = emptyList(),
            colorsEmpty = true, colors = emptyList(),
            wineTypesEmpty = true, wineTypes = emptyList(),
            sweetnessEmpty = true, sweetness = emptyList(),
            countriesEmpty = true, countries = emptyList(),
            minRating = null, maxRating = null, minPrice = null, maxPrice = null,
            sort = "RATING",
        ).first()
        assertEquals(listOf("высокий", "низкий"), sorted.map { it.name })
    }

    @Test
    fun `типы с категориями - без фильтра отдаёт все, дедуплицируя`() = runTest {
        dao.upsert(entity("A", category = "BEER", type = "IPA"))
        dao.upsert(entity("B", category = "BEER", type = "IPA"))
        dao.upsert(entity("C", category = "WINE", type = "Красное, сухое"))
        dao.upsert(entity("D", category = "WINE", type = null))
        val rows = dao.observeUsedTypesGrouped(null).first()
        assertEquals(
            listOf("BEER" to "IPA", "WINE" to "Красное, сухое"),
            rows.map { it.category to it.type },
        )
    }

    @Test
    fun `типы с категориями - фильтр по категории`() = runTest {
        dao.upsert(entity("A", category = "BEER", type = "IPA"))
        dao.upsert(entity("C", category = "WINE", type = "Красное, сухое"))
        val rows = dao.observeUsedTypesGrouped("WINE").first()
        assertEquals(listOf("WINE" to "Красное, сухое"), rows.map { it.category to it.type })
    }

    @Test
    fun `сортировка по рейтингу — null в конце`() = runTest {
        dao.upsert(entity("A", rating = 5, createdAt = 1))
        dao.upsert(entity("B", rating = null, createdAt = 2))
        dao.upsert(entity("C", rating = 9, createdAt = 3))
        val sorted = dao.observeFiltered(
            category = null, includeRecipes = false, typesEmpty = true, types = emptyList(),
            stylesEmpty = true, styles = emptyList(),
            colorsEmpty = true, colors = emptyList(),
            wineTypesEmpty = true, wineTypes = emptyList(),
            sweetnessEmpty = true, sweetness = emptyList(),
            countriesEmpty = true, countries = emptyList(),
            minRating = null, maxRating = null, minPrice = null, maxPrice = null,
            sort = "RATING",
        ).first()
        assertEquals(listOf("C", "A", "B"), sorted.map { it.name })
    }

    @Test
    fun `сортировка по цене — null в конце`() = runTest {
        dao.upsert(entity("A", priceRub = 500, createdAt = 1))
        dao.upsert(entity("B", priceRub = null, createdAt = 2))
        dao.upsert(entity("C", priceRub = 1200, createdAt = 3))
        val sorted = dao.observeFiltered(
            category = null, includeRecipes = false, typesEmpty = true, types = emptyList(),
            stylesEmpty = true, styles = emptyList(),
            colorsEmpty = true, colors = emptyList(),
            wineTypesEmpty = true, wineTypes = emptyList(),
            sweetnessEmpty = true, sweetness = emptyList(),
            countriesEmpty = true, countries = emptyList(),
            minRating = null, maxRating = null, minPrice = null, maxPrice = null,
            sort = "PRICE",
        ).first()
        assertEquals(listOf("C", "A", "B"), sorted.map { it.name })
    }

    @Test
    fun `сорта винограда сохраняются и восстанавливаются через JSON`() = runTest {
        val entry = Entry(
            category = Category.WINE, name = "Бленд",
            grapes = listOf("Неббиоло", "Сира", "Мерло"), country = "Италия",
        )
        val id = dao.upsert(entry.toEntity())
        val restored = dao.getById(id)!!.toDomain()
        assertEquals(listOf("Неббиоло", "Сира", "Мерло"), restored.grapes)
    }

    @Test
    fun `пустой список сортов хранится как null`() = runTest {
        val entry = Entry(category = Category.WINE, name = "Без сорта")
        val id = dao.upsert(entry.toEntity())
        val stored = dao.getById(id)!!
        assertNull(stored.grape)
        assertTrue(stored.toDomain().grapes.isEmpty())
    }

    /**
     * Примеры для черновика заметки: своей категории, непустые и свежие первыми —
     * модель подражает тому, что ей показали, поэтому образцы должны быть
     * настоящими заметками автора, а не случайными строками.
     */
    @Test
    fun `последние заметки категории - непустые и свежие первыми`() = runTest {
        dao.upsert(entity("Старое", createdAt = 1, note = "старая заметка"))
        dao.upsert(entity("Новое", createdAt = 100, note = "свежая заметка"))
        dao.upsert(entity("Без заметки", createdAt = 50))
        dao.upsert(entity("Пустая", createdAt = 60, note = "   "))
        dao.upsert(entity("Пиво", category = "BEER", createdAt = 200, note = "пивная заметка"))

        val notes = dao.recentNotes("WINE", limit = 10)

        assertEquals(listOf("свежая заметка", "старая заметка"), notes)
    }
}
