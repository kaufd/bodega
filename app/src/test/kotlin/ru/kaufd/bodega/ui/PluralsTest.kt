package ru.kaufd.bodega.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.kaufd.bodega.R

/**
 * Формы множественного числа. application = Application: иначе Robolectric поднял
 * бы @HiltAndroidApp. qualifiers = "ru" — правила берутся по локали конфигурации,
 * поэтому без него сработали бы английские (одна форма на 2..∞).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "ru")
class PluralsTest {

    private val resources = ApplicationProvider.getApplicationContext<Application>().resources

    private fun apply(count: Int) = resources.getQuantityString(R.plurals.filters_apply, count, count)

    private fun drinks(count: Int) = resources.getQuantityString(R.plurals.header_count_label, count)

    @Test
    fun `кнопка применения склоняет записи`() {
        assertEquals("Показать 1 запись", apply(1))
        assertEquals("Показать 2 записи", apply(2))
        assertEquals("Показать 5 записей", apply(5))
        assertEquals("Показать 11 записей", apply(11))
        assertEquals("Показать 21 запись", apply(21))
    }

    @Test
    fun `счётчик в шапке склоняет напитки`() {
        assertEquals("напиток", drinks(1))
        assertEquals("напитка", drinks(3))
        assertEquals("напитков", drinks(6))
        assertEquals("напитков", drinks(0))
    }
}
