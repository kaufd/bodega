package ru.kaufd.bodega.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizeQueryTest {

    @Test
    fun `пустой ввод - пустой список`() {
        assertTrue(tokenizeQuery("").isEmpty())
    }

    @Test
    fun `только разделители - пустой список`() {
        assertTrue(tokenizeQuery("   ,.!?-—").isEmpty())
    }

    @Test
    fun `отброс токенов короче 3 символов`() {
        assertEquals(listOf("barolo"), tokenizeQuery("a ab barolo"))
    }

    @Test
    fun `отброс коротких чисел - крепость и объём`() {
        // «40» (крепость), «700» (объём) — короче 4, отбрасываем
        assertEquals(emptyList<String>(), tokenizeQuery("40 700"))
    }

    @Test
    fun `сохранение 4-значного числа - винтажный год`() {
        assertEquals(listOf("rioja", "2014"), tokenizeQuery("rioja 2014"))
    }

    @Test
    fun `дубли убираются`() {
        assertEquals(listOf("barolo"), tokenizeQuery("barolo barolo BAROLO"))
    }

    @Test
    fun `кириллица lowercase`() {
        assertEquals(listOf("рислинг"), tokenizeQuery("Рислинг"))
    }

    @Test
    fun `смешанные латиница и кириллица`() {
        assertEquals(listOf("rioja", "рислинг"), tokenizeQuery("Rioja Рислинг"))
    }

    @Test
    fun `split по не-буквенно-цифровым разделителям`() {
        assertEquals(
            listOf("rioja", "gran", "reserva", "2014"),
            tokenizeQuery("Rioja, Gran Reserva! 2014"),
        )
    }

    @Test
    fun `3-значный токен сохраняется если не чисто числовой`() {
        assertEquals(listOf("abc"), tokenizeQuery("abc"))
    }

    @Test
    fun `unicode-aware - кириллица не разделитель`() {
        assertEquals(listOf("рислинг"), tokenizeQuery("рислинг"))
    }
}
