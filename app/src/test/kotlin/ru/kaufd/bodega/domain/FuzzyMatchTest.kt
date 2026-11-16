package ru.kaufd.bodega.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatchTest {

    @Test
    fun `лишняя буква в длинном терме матчится`() {
        // ротлу(5) vs ротл — dist 1, порог 1
        assertTrue(fuzzyTermMatches("ротлу", "ротл"))
    }

    @Test
    fun `точное совпадение матчится`() {
        assertTrue(fuzzyTermMatches("шираз", "шираз"))
    }

    @Test
    fun `опечатка в середине длинного слова матчится`() {
        // каберне(7) vs каберле — dist 1
        assertTrue(fuzzyTermMatches("каберне", "каберле"))
    }

    @Test
    fun `короткий терм требует точного совпадения`() {
        // порт(4) — порог 0, торт не проходит
        assertFalse(fuzzyTermMatches("порт", "торт"))
        assertTrue(fuzzyTermMatches("порт", "порт"))
    }

    @Test
    fun `числовой терм не матчит соседний год`() {
        assertFalse(fuzzyTermMatches("2019", "2018"))
        assertTrue(fuzzyTermMatches("2019", "2019"))
    }

    @Test
    fun `разные слова не матчатся`() {
        assertFalse(fuzzyTermMatches("мерло", "шираз"))
    }

    @Test
    fun `две опечатки допустимы только в очень длинном терме`() {
        // шардоне(7) — порог 1, две правки не проходят
        assertFalse(fuzzyTermMatches("шардоне", "шордоме"))
        // совиньонблан(12) — порог 2
        assertTrue(fuzzyTermMatches("совиньонблан", "савиньонблон"))
    }

    @Test
    fun `levenshteinWithin ранний выход по разнице длин`() {
        assertFalse(levenshteinWithin("аб", "абвгд", 1))
        assertTrue(levenshteinWithin("абвг", "абвд", 1))
    }
}
