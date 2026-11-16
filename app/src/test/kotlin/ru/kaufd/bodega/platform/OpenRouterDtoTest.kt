package ru.kaufd.bodega.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenRouterDtoTest {

    @Test
    fun `достаёт содержимое ответа`() {
        val content = extractContent(
            """{"choices":[{"message":{"role":"assistant","content":"{\"name\":\"Бароло\"}"}}]}"""
        )

        assertEquals("""{"name":"Бароло"}""", content)
    }

    @Test
    fun `null вместо содержимого даёт пустую строку`() {
        // Reasoning-модель отдаёт content = null, когда лимит кончился на размышлении.
        val json = """{"choices":[{"message":{"role":"assistant","content":null,"reasoning":"думал"}}]}"""

        assertEquals("", extractContent(json))
    }

    @Test
    fun `обрыв по лимиту токенов даёт пустую строку, а не обрезок`() {
        // Обрезанный черновик прошёл бы проверку на пустоту и заменил бы заметку
        // половиной фразы; обрезанный JSON этикетки всё равно не разобрать.
        val json = """
            {"choices":[{"message":{"content":"Цвет рубиновый, аромат виш"},"finish_reason":"length"}]}
        """.trimIndent()

        assertEquals("", extractContent(json))
    }

    @Test
    fun `ответ без choices даёт пустую строку`() {
        assertEquals("", extractContent("""{"choices":[]}"""))
    }

    @Test
    fun `ответ с ошибкой вместо choices даёт пустую строку`() {
        assertEquals("", extractContent("""{"error":{"message":"no credits","code":402}}"""))
    }
}
