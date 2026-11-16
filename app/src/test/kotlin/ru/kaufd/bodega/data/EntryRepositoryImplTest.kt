package ru.kaufd.bodega.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.EntryFilter
import ru.kaufd.bodega.domain.EntryLimits
import ru.kaufd.bodega.domain.PhotoStorage
import ru.kaufd.bodega.domain.PhotoSource
import ru.kaufd.bodega.domain.SortOrder
import ru.kaufd.bodega.domain.StoredPhoto

private class FakePhotoStorage : PhotoStorage {
    val deleted = mutableListOf<String>()
    override suspend fun delete(relativePath: String): Boolean { deleted += relativePath; return true }
    override fun absolutePath(relativePath: String) = "/abs/$relativePath"
    override fun open(relativePath: String): java.io.InputStream? = null
    override suspend fun store(input: java.io.InputStream): String = error("not used")
    override suspend fun importImage(source: PhotoSource): String = error("not used")
    override suspend fun list(): List<StoredPhoto> = emptyList()
}

// Robolectric — как в EntryDaoTest: Room на JVM без эмулятора.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class EntryRepositoryImplTest {
    private lateinit var db: BodegaDatabase
    private lateinit var photos: FakePhotoStorage
    private lateinit var repo: EntryRepositoryImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder<BodegaDatabase>(
            ApplicationProvider.getApplicationContext()
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        photos = FakePhotoStorage()
        repo = EntryRepositoryImpl(db.entryDao(), photos)
    }

    @After
    fun teardown() = db.close()

    @Test
    fun `upsert и чтение через фильтр`() = runTest {
        repo.upsert(Entry(category = Category.WINE, name = "Бароло", createdAt = 1))
        val list = repo.observeEntries(EntryFilter(category = Category.WINE)).first()
        assertEquals("Бароло", list.single().name)
    }

    @Test
    fun `«Все» без рецептов, бэкап и раздел рецептов — с ними`() = runTest {
        repo.upsert(Entry(category = Category.WINE, name = "Бароло", createdAt = 1))
        repo.upsert(Entry(category = Category.COCKTAIL, name = "Негрони", createdAt = 2))
        assertEquals(listOf("Бароло"), repo.observeEntries(EntryFilter()).first().map { it.name })
        assertEquals(setOf("Бароло", "Негрони"), repo.observeEntries(EntryFilter(includeRecipes = true)).first().map { it.name }.toSet())
        assertEquals(listOf("Негрони"), repo.observeEntries(EntryFilter(category = Category.COCKTAIL)).first().map { it.name })
    }

    @Test
    fun `delete не трогает фото, purgePhotos удаляет файлы`() = runTest {
        val id = repo.upsert(
            Entry(category = Category.BEER, name = "X", photos = listOf("photos/a.jpg"), createdAt = 1)
        )
        val saved = repo.getById(id)!!
        assertEquals(saved, repo.delete(id))
        assertTrue(photos.deleted.isEmpty())
        assertNull(repo.getById(id))
        repo.purgePhotos(saved.photos)
        assertEquals(listOf("photos/a.jpg"), photos.deleted)
    }

    @Test
    fun `usedPhotoPaths разворачивает фотографии всех записей в плоский список`() = runTest {
        repo.upsert(
            Entry(
                category = Category.WINE, name = "A",
                photos = listOf("photos/a1.jpg", "photos/a2.jpg"), createdAt = 1,
            )
        )
        repo.upsert(
            Entry(category = Category.BEER, name = "B", photos = listOf("photos/b1.jpg"), createdAt = 2)
        )
        repo.upsert(Entry(category = Category.BEER, name = "Без фото", createdAt = 3))

        assertEquals(
            setOf("photos/a1.jpg", "photos/a2.jpg", "photos/b1.jpg"),
            repo.usedPhotoPaths().toSet(),
        )
    }

    @Test
    fun `restore возвращает запись с прежним id`() = runTest {
        val id = repo.upsert(Entry(category = Category.SPIRIT, name = "Лагавулин", createdAt = 1))
        val saved = repo.getById(id)!!
        repo.delete(id)
        repo.restore(saved)
        assertEquals("Лагавулин", repo.getById(id)?.name)
    }

    @Test
    fun `типы группируются по категориям`() = runTest {
        repo.upsert(Entry(category = Category.BEER, name = "A", type = "IPA", createdAt = 1))
        repo.upsert(Entry(category = Category.BEER, name = "B", type = "Стаут", createdAt = 2))
        repo.upsert(Entry(category = Category.WINE, name = "C", type = "Красное, сухое", createdAt = 3))

        val grouped = repo.observeUsedTypesGrouped(null).first()
        assertEquals(
            mapOf(
                Category.WINE to listOf("Красное, сухое"),
                Category.BEER to listOf("IPA", "Стаут"),
            ),
            grouped,
        )
    }

    @Test
    fun `группировка типов уважает выбранную категорию`() = runTest {
        repo.upsert(Entry(category = Category.BEER, name = "A", type = "IPA", createdAt = 1))
        repo.upsert(Entry(category = Category.WINE, name = "C", type = "Красное, сухое", createdAt = 2))

        val grouped = repo.observeUsedTypesGrouped(Category.BEER).first()
        assertEquals(mapOf(Category.BEER to listOf("IPA")), grouped)
    }

    @Test
    fun `upsert отклоняет рейтинг и крепость вне границ EntryLimits`() = runTest {
        val repo = EntryRepositoryImpl(db.entryDao(), photos)
        val base = Entry(category = Category.WINE, name = "X")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.upsert(base.copy(rating = EntryLimits.RATING.last + 1)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.upsert(base.copy(abvPercent = EntryLimits.ABV.endInclusive + 1)) }
        }
    }

    @Test
    fun `observeWineFacets сортирует по каноническому порядку, неизвестное в конец`() = runTest {
        val repo = EntryRepositoryImpl(db.entryDao(), photos)
        repo.upsert(Entry(category = Category.WINE, name = "b", color = "Белое", createdAt = 1))
        repo.upsert(Entry(category = Category.WINE, name = "r", color = "Красное", createdAt = 2))
        val facets = repo.observeWineFacets().first()
        // WineClassification.colors = [Красное, Белое, Розовое]
        assertEquals(listOf("Красное", "Белое"), facets.colors)
    }

    // --- Перенесённые из DAO тесты текстового поиска ---

    @Test
    fun `поиск по стране находит запись`() = runTest {
        repo.upsert(Entry(category = Category.WINE, name = "Бароло", type = "Красное, сухое", country = "Италия", createdAt = 1))
        repo.upsert(Entry(category = Category.WINE, name = "Рислинг", type = "Белое, сухое", country = "Германия", createdAt = 2))
        val found = repo.observeEntries(EntryFilter(query = "итал")).first()
        assertEquals(listOf("Бароло"), found.map { it.name })
    }

    @Test
    fun `поиск по сорту из нескольких находит запись`() = runTest {
        repo.upsert(Entry(category = Category.WINE, name = "Бленд", grapes = listOf("Неббиоло", "Сира"), createdAt = 1))
        val found = repo.observeEntries(EntryFilter(query = "сира")).first()
        assertEquals(listOf("Бленд"), found.map { it.name })
    }

    // --- Новые тесты токенизированного матчинга/ранжирования ---

    @Test
    fun `порог больше нуля - запись без совпадений отфильтрована`() = runTest {
        repo.upsert(Entry(category = Category.WINE, name = "Бароло", country = "Италия", createdAt = 1))
        repo.upsert(Entry(category = Category.WINE, name = "Кьянти", country = "Италия", createdAt = 2))
        val found = repo.observeEntries(EntryFilter(query = "рислинг")).first()
        assertTrue(found.isEmpty())
    }

    @Test
    fun `ранжирование - запись с большим числом совпадений выше`() = runTest {
        // «Бароло» содержит только «бароло»; «Бароло Рислинг» содержит и «бароло», и «рислинг»
        repo.upsert(Entry(category = Category.WINE, name = "Бароло", createdAt = 1))
        repo.upsert(Entry(category = Category.WINE, name = "Бароло Рислинг", createdAt = 2))
        val found = repo.observeEntries(EntryFilter(query = "бароло рислинг")).first()
        assertEquals(listOf("Бароло Рислинг", "Бароло"), found.map { it.name })
    }

    @Test
    fun `стабильный tie-break - равная релевантность сохраняет SQL-порядок`() = runTest {
        // Обе записи содержат «бароло», релевантность 1; sort=DATE → по createdAt DESC
        repo.upsert(Entry(category = Category.WINE, name = "Бароло старое", createdAt = 1))
        repo.upsert(Entry(category = Category.WINE, name = "Бароло новое", createdAt = 2))
        val found = repo.observeEntries(EntryFilter(query = "бароло", sort = SortOrder.DATE)).first()
        assertEquals(listOf("Бароло новое", "Бароло старое"), found.map { it.name })
    }

    @Test
    fun `пустой query - прежнее поведение без пост-обработки`() = runTest {
        repo.upsert(Entry(category = Category.WINE, name = "Бароло", createdAt = 1))
        repo.upsert(Entry(category = Category.WINE, name = "Кьянти", createdAt = 2))
        val all = repo.observeEntries(EntryFilter(query = "", sort = SortOrder.DATE)).first()
        assertEquals(listOf("Кьянти", "Бароло"), all.map { it.name })
    }

    @Test
    fun `короткий числовой query - все записи отфильтрованы`() = runTest {
        // «40» — короткое число (крепость/объём), шум; порог ≥ 1 не пройден даже с fallback.
        repo.upsert(Entry(category = Category.WINE, name = "Бароло 40", createdAt = 1))
        val found = repo.observeEntries(EntryFilter(query = "40")).first()
        assertTrue(found.isEmpty())
    }

    @Test
    fun `короткий нечисловой query - fallback на подстроку находит запись`() = runTest {
        // Инкрементальный ввод «ба» (2 символа): токенов нет, но fallback ищет подстроку.
        repo.upsert(Entry(category = Category.WINE, name = "Бароло", createdAt = 1))
        repo.upsert(Entry(category = Category.WINE, name = "Кьянти", createdAt = 2))
        val found = repo.observeEntries(EntryFilter(query = "ба")).first()
        assertEquals(listOf("Бароло"), found.map { it.name })
    }

    // --- Fuzzy-поиск по фото (queryFuzzy) ---

    @Test
    fun `строгий поиск не прощает опечатку OCR`() = runTest {
        repo.upsert(Entry(category = Category.WINE, name = "Ротлбергер", createdAt = 1))
        val found = repo.observeEntries(EntryFilter(query = "ротлбергир")).first()
        assertTrue(found.isEmpty())
    }

    @Test
    fun `fuzzy-поиск находит запись с опечаткой OCR`() = runTest {
        repo.upsert(Entry(category = Category.WINE, name = "Ротлбергер", createdAt = 1))
        val found = repo.observeEntries(EntryFilter(query = "ротлбергир", queryFuzzy = true)).first()
        assertEquals(listOf("Ротлбергер"), found.map { it.name })
    }

    @Test
    fun `fuzzy не подтягивает соседний год`() = runTest {
        repo.upsert(Entry(category = Category.WINE, name = "Бароло 2018", createdAt = 1))
        val found = repo.observeEntries(EntryFilter(query = "2019", queryFuzzy = true)).first()
        assertTrue(found.isEmpty())
    }
}
