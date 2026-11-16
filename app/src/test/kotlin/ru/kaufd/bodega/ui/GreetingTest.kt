package ru.kaufd.bodega.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.kaufd.bodega.ui.util.greetingRes

/** Границы суток: проверяем сами тексты, а не идентификаторы ресурсов. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "ru")
class GreetingTest {

    private val resources = ApplicationProvider.getApplicationContext<Application>().resources

    private fun greeting(hour: Int) = resources.getString(greetingRes(hour))

    @Test
    fun `приветствие меняется на границах суток`() {
        assertEquals("Доброй ночи", greeting(0))
        assertEquals("Доброй ночи", greeting(4))
        assertEquals("Доброе утро", greeting(5))
        assertEquals("Доброе утро", greeting(11))
        assertEquals("Добрый день", greeting(12))
        assertEquals("Добрый день", greeting(17))
        assertEquals("Добрый вечер", greeting(18))
        assertEquals("Добрый вечер", greeting(23))
    }
}
