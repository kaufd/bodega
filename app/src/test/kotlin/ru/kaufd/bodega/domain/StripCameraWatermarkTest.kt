package ru.kaufd.bodega.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StripCameraWatermarkTest {

    @Test
    fun `убирает строку Shot on вместе с моделью телефона`() {
        val ocr = "Barolo Riserva\n2016\nShot on Galaxy S23 Ultra"
        assertEquals("Barolo Riserva\n2016", stripCameraWatermark(ocr))
    }

    @Test
    fun `убирает Triple camera и мегапиксели`() {
        val ocr = "Chianti Classico\n50 MP | Triple camera"
        assertEquals("Chianti Classico", stripCameraWatermark(ocr))
    }

    @Test
    fun `убирает Leica`() {
        assertEquals("Rioja", stripCameraWatermark("Rioja\nLEICA"))
    }

    @Test
    fun `нечувствителен к регистру`() {
        assertEquals("Rioja", stripCameraWatermark("Rioja\nSHOT ON PIXEL 8"))
    }

    @Test
    fun `не трогает обычную этикетку`() {
        val label = "Champagne Brut\nGran Reserva 2014"
        assertEquals(label, stripCameraWatermark(label))
    }

    @Test
    fun `пустой ввод остаётся пустым`() {
        assertEquals("", stripCameraWatermark(""))
    }
}
