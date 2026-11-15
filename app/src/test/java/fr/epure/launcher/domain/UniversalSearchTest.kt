package fr.epure.launcher.domain

import fr.epure.launcher.data.config.SearchConfig
import fr.epure.launcher.ui.SearchExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalSearchTest {

    private fun calc(text: String) = Calculator.evaluate(text)

    @Test
    fun `calculs courants`() {
        assertEquals(84.0, calc("12×7")!!, 1e-9)
        assertEquals(84.0, calc("12 x 7")!!, 1e-9)
        assertEquals(3.5, calc("(3+4)/2")!!, 1e-9)
        assertEquals(1024.0, calc("2^10")!!, 1e-9)
        assertEquals(12.0, calc("80*15%")!!, 1e-9)
        assertEquals(2.5, calc("1,5 + 1")!!, 1e-9)
        assertEquals(-2.0, calc("-5+3")!!, 1e-9)
        assertEquals(14.0, calc("2+3×4")!!, 1e-9)
        assertEquals(2.0 / 3.0, calc("2÷3")!!, 1e-9)
    }

    @Test
    fun `ce qui n est pas un calcul`() {
        assertNull(calc("12"))
        assertNull(calc("-5"))
        assertNull(calc("Netflix"))
        assertNull(calc("x"))
        assertNull(calc("10:30"))
        assertNull(calc("06 12 34 56 78"))
        assertNull(calc("1/0"))
        assertNull(calc("(1+2"))
        assertNull(calc("1,234.5 + 1"))
    }

    @Test
    fun `resultat a la francaise`() {
        assertEquals("84", Calculator.format(84.0))
        assertEquals("3,5", Calculator.format(3.5))
        assertEquals("1 234,5", Calculator.format(1234.5))
        assertEquals("1234,5", Calculator.format(1234.5, grouping = false))
        assertEquals("0,6666666667", Calculator.format(2.0 / 3.0))
    }

    @Test
    fun `reglages Android trouves sans accents ni majuscules`() {
        assertEquals("wifi", SystemShortcuts.search("wifi").first().id)
        assertEquals("bluetooth", SystemShortcuts.search("Blue").first().id)
        assertEquals("battery", SystemShortcuts.search("batt").first().id)
        assertEquals("location", SystemShortcuts.search("localisa").first().id)
        assertEquals(SystemShortcuts.TORCH, SystemShortcuts.search("lampe").first().id)
        assertEquals("display", SystemShortcuts.search("luminosité").first().id)
        assertEquals("hotspot", SystemShortcuts.search("point d’acc").first().id)
        assertTrue(SystemShortcuts.search("w").isEmpty())
        assertTrue(SystemShortcuts.search("netflix").isEmpty())
        assertTrue(SystemShortcuts.search("son").size <= 3)
    }

    @Test
    fun `un calcul remplace les autres resultats et les reglages se desactivent`() {
        val all = SearchConfig()
        val calcul = SearchExtras.instant("12*7", all)
        assertEquals("84", calcul.calculation!!.result)
        assertTrue(calcul.shortcuts.isEmpty())
        assertFalse(SearchExtras.wantsContacts("12*7", all, calcul))

        val wifi = SearchExtras.instant("wifi", all)
        assertNull(wifi.calculation)
        assertEquals("wifi", wifi.shortcuts.first().id)
        assertTrue(SearchExtras.wantsContacts("wifi", all, wifi))

        val off = SearchConfig(contacts = false, calculator = false, systemSettings = false)
        assertTrue(SearchExtras.instant("12*7", off).isEmpty)
        assertTrue(SearchExtras.instant("wifi", off).isEmpty)
        assertFalse(SearchExtras.wantsContacts("lea", off, SearchExtras()))
        // Une seule lettre : pas de recherche dans les contacts.
        assertFalse(SearchExtras.wantsContacts("l", all, SearchExtras()))
    }
}
