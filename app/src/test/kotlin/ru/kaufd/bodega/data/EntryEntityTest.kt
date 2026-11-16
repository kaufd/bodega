package ru.kaufd.bodega.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryEntityTest {

    @Test
    fun `пустой список фотографий кодируется в null`() {
        assertNull(encodeStrings(emptyList()))
    }

    @Test
    fun `список путей переживает кодирование и декодирование`() {
        val photos = listOf("photos/a.jpg", "photos/b.jpg", "photos/c.jpg")

        assertEquals(photos, decodeStrings(encodeStrings(photos)))
    }

    @Test
    fun `битая строка декодируется в пустой список, а не роняет чтение записи`() {
        assertEquals(emptyList<String>(), decodeStrings("не json"))
    }

    @Test
    fun `битая строка отличима от пустой колонки`() {
        // По этому различию EntryRepositoryImpl.usedPhotoPaths решает, можно ли
        // отдавать уборке список используемых файлов: битую колонку, понятую как
        // «фотографий нет», уборка приняла бы за сирот и стёрла снимки с диска.
        assertNull(decodeStringsOrNull("не json"))
        assertEquals(emptyList<String>(), decodeStringsOrNull(null))
    }

    @Test
    fun `отсутствие фотографий в колонке декодируется в пустой список`() {
        assertEquals(emptyList<String>(), decodeStrings(null))
    }
}
