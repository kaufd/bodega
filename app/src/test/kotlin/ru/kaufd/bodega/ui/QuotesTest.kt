package ru.kaufd.bodega.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QuotesTest {

    @Test
    fun `ресурсы цитат и авторов не повторяются`() {
        assertEquals(Quotes.size, Quotes.map { it.text }.distinct().size)
        assertEquals(Quotes.size, Quotes.map { it.author }.distinct().size)
    }
}
