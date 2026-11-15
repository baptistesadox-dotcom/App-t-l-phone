package fr.epure.launcher.data.weather

import android.content.Context
import android.util.Log
import fr.epure.launcher.data.config.WeatherLocation
import fr.epure.launcher.domain.DayForecast
import fr.epure.launcher.domain.WeatherReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Météo fournie gratuitement et sans clé par Open-Meteo (open-meteo.com),
 * sous licence CC BY 4.0 : la mention de la source est affichée dans « À propos ».
 */

@Serializable
internal data class GeocodingResponse(val results: List<GeocodingResult> = emptyList())

@Serializable
internal data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)

@Serializable
internal data class ForecastResponse(
    val current: Current? = null,
    val daily: Daily? = null,
) {
    @Serializable
    data class Current(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val weatherCode: Int,
        @SerialName("is_day") val isDay: Int = 1,
        @SerialName("relative_humidity_2m") val humidity: Double? = null,
        @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    )

    @Serializable
    data class Daily(
        val time: List<String> = emptyList(),
        @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
        @SerialName("temperature_2m_max") val max: List<Double?> = emptyList(),
        @SerialName("temperature_2m_min") val min: List<Double?> = emptyList(),
    )
}

/** Conversion des réponses JSON d'Open-Meteo (séparée du réseau pour être testée). */
object OpenMeteoParser {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun parseLocations(body: String): List<WeatherLocation> =
        json.decodeFromString(GeocodingResponse.serializer(), body).results.map {
            WeatherLocation(
                name = it.name,
                latitude = it.latitude,
                longitude = it.longitude,
                region = listOfNotNull(it.admin1, it.country).distinct().joinToString(", ").ifEmpty { null },
            )
        }

    fun parseForecast(body: String, nowMillis: Long): WeatherReport? {
        val response = json.decodeFromString(ForecastResponse.serializer(), body)
        val current = response.current ?: return null
        val daily = response.daily
        val days = daily?.time?.indices?.mapNotNull { i ->
            val code = daily.weatherCode.getOrNull(i)
            val max = daily.max.getOrNull(i)
            val min = daily.min.getOrNull(i)
            if (code == null || max == null || min == null) null
            else DayForecast(date = daily.time[i], code = code, min = min, max = max)
        }.orEmpty()
        return WeatherReport(
            temperature = current.temperature,
            code = current.weatherCode,
            isDay = current.isDay != 0,
            days = days,
            fetchedAtMillis = nowMillis,
            humidity = current.humidity?.roundToInt(),
            windKmh = current.windSpeed?.roundToInt(),
        )
    }
}

/** Récupère la météo et garde la dernière réponse pour l'afficher immédiatement au démarrage. */
class WeatherRepository(context: Context) {

    private val cacheFile = File(context.filesDir, "weather_cache.json")

    @Serializable
    private data class CachedForecast(val latitude: Double, val longitude: Double, val body: String, val fetchedAt: Long)

    private val cacheJson = Json { ignoreUnknownKeys = true }

    suspend fun searchLocations(query: String): List<WeatherLocation> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val lang = Locale.getDefault().language.ifEmpty { "fr" }
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=8&format=json" +
            "&language=$lang&name=" + URLEncoder.encode(q, "UTF-8")
        OpenMeteoParser.parseLocations(httpGet(url))
    }

    /** Dernière météo connue pour ce lieu (sans réseau), si elle date de moins de 12 h. */
    suspend fun cached(location: WeatherLocation): WeatherReport? = withContext(Dispatchers.IO) {
        runCatching {
            val cached = cacheJson.decodeFromString(CachedForecast.serializer(), cacheFile.readText())
            val sameLocation = cached.latitude == location.latitude && cached.longitude == location.longitude
            if (!sameLocation || System.currentTimeMillis() - cached.fetchedAt > 12 * HOUR) null
            else OpenMeteoParser.parseForecast(cached.body, cached.fetchedAt)
        }.getOrNull()
    }

    suspend fun forecast(location: WeatherLocation): WeatherReport? = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.ROOT,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,is_day,relative_humidity_2m,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=4",
            location.latitude,
            location.longitude,
        )
        try {
            val body = httpGet(url)
            val now = System.currentTimeMillis()
            OpenMeteoParser.parseForecast(body, now)?.also {
                runCatching {
                    cacheFile.writeText(
                        cacheJson.encodeToString(
                            CachedForecast.serializer(),
                            CachedForecast(location.latitude, location.longitude, body, now),
                        ),
                    )
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "Météo indisponible (réseau)", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Réponse météo illisible", e)
            null
        }
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "Weather"
        const val HOUR = 60 * 60 * 1000L

        /** Durée au-delà de laquelle la météo est rafraîchie. */
        const val REFRESH_AFTER_MILLIS = 30 * 60 * 1000L
    }
}
