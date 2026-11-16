package ru.kaufd.bodega.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Календарь Material отдаёт UTC-полночь, запись хранит локальную. Без перевода
 * между ними выбранное число уезжало бы на сутки в зонах западнее Гринвича.
 */
class TastedAtConversionTest {

    @Test
    fun `выбранный день остаётся тем же числом в локальной зоне`() {
        val utcNoon = Instant.parse("2026-03-15T00:00:00Z").toEpochMilli()

        val stored = fromPickerDay(utcNoon)

        val local = Instant.ofEpochMilli(stored).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals("2026-03-15", local.toString())
    }

    @Test
    fun `сохранённое время суток не сдвигает день в пикере`() {
        // Импорт из ВК кладёт в tastedAt момент публикации поста, а не полночь.
        val evening = Instant.parse("2026-03-15T21:40:00Z").toEpochMilli()

        val picked = toPickerDay(evening)

        val expectedDay = Instant.ofEpochMilli(evening).atZone(ZoneId.systemDefault()).toLocalDate()
        val pickedDay = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
        assertEquals(expectedDay, pickedDay)
    }

    @Test
    fun `круговой перевод сохраняет день`() {
        val stored = fromPickerDay(Instant.parse("2026-12-31T00:00:00Z").toEpochMilli())

        assertEquals(stored, fromPickerDay(toPickerDay(stored)))
    }

    @Test
    fun `сегодняшний день выбирается, завтрашний — нет`() {
        // Утро в локальной зоне: сегодняшний день календаря — тот же, что у часов.
        val now = Instant.parse("2026-03-15T09:00:00Z").toEpochMilli()
        val today = toPickerDay(now)

        assertTrue(isPastOrToday(today, now))
        assertTrue(isPastOrToday(today - DAY_MILLIS, now))
        assertFalse(isPastOrToday(today + DAY_MILLIS, now))
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
