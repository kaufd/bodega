package ru.kaufd.bodega.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryTest {
    @Test fun `parseOrNull возвращает enum для валидного имени`() {
        assertEquals(Category.WINE, Category.parseOrNull("WINE"))
    }
    @Test fun `parseOrNull возвращает null для мусора и null`() {
        assertNull(Category.parseOrNull("nope"))
        assertNull(Category.parseOrNull(null))
    }
}
