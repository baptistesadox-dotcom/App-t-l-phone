package fr.epure.launcher.data.weather

import fr.epure.launcher.domain.WeatherCodes
import fr.epure.launcher.domain.WeatherKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoParserTest {

    private val forecast = """
        {"latitude":48.86,"longitude":2.34,"timezone":"Europe/Paris",
         "current_units":{"temperature_2m":"°C"},
         "current":{"time":"2026-11-14T10:00","interval":900,"temperature_2m":11.6,"weather_code":3,"is_day":1},
         "daily":{"time":["2026-11-14","2026-11-15","2026-11-16"],
                  "weather_code":[3,61,null],
                  "temperature_2m_max":[13.1,12.0,10.0],
                  "temperature_2m_min":[7.2,8.4,5.0]}}
    """.trimIndent()

    @Test
    fun `lecture de la prevision`() {
        val report = OpenMeteoParser.parseForecast(forecast, nowMillis = 1234L)!!
        assertEquals(11.6, report.temperature, 0.001)
        assertEquals(3, report.code)
        assertTrue(report.isDay)
        assertEquals(1234L, report.fetchedAtMillis)
        // Le troisième jour, incomplet, est ignoré.
        assertEquals(listOf("2026-11-14", "2026-11-15"), report.days.map { it.date })
        assertEquals(61, report.days[1].code)
        assertEquals(8.4, report.days[1].min, 0.001)
    }

    @Test
    fun `prevision sans donnees actuelles`() {
        assertNull(OpenMeteoParser.parseForecast("""{"latitude":1.0}""", 0L))
    }

    @Test
    fun `lecture des villes`() {
        val body = """{"results":[{"id":2988507,"name":"Paris","latitude":48.85341,"longitude":2.3488,
            "country":"France","admin1":"Île-de-France"},{"name":"Paris","latitude":33.66,"longitude":-95.55,
            "country":"United States","admin1":"Texas"}],"generationtime_ms":0.5}"""
        val cities = OpenMeteoParser.parseLocations(body)
        assertEquals(2, cities.size)
        assertEquals("Île-de-France, France", cities[0].region)
        assertEquals(48.85341, cities[0].latitude, 0.00001)
        assertTrue(OpenMeteoParser.parseLocations("""{"generationtime_ms":0.3}""").isEmpty())
    }

    @Test
    fun `codes meteo WMO`() {
        assertEquals(WeatherKind.CLEAR, WeatherCodes.kindOf(0))
        assertEquals(WeatherKind.RAIN, WeatherCodes.kindOf(81))
        assertEquals(WeatherKind.SNOW, WeatherCodes.kindOf(73))
        assertEquals(WeatherKind.STORM, WeatherCodes.kindOf(99))
        assertEquals(WeatherKind.UNKNOWN, WeatherCodes.kindOf(1234))
        assertEquals("Ciel dégagé", WeatherCodes.labelOf(0, isDay = false))
        assertEquals("Couvert", WeatherCodes.labelOf(3))
    }
}
