package fr.epure.launcher.domain

/** Grandes familles de temps, pour choisir une icône. */
enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM, UNKNOWN }

/** Codes météo WMO utilisés par Open-Meteo. */
object WeatherCodes {

    fun kindOf(code: Int): WeatherKind = when (code) {
        0, 1 -> WeatherKind.CLEAR
        2 -> WeatherKind.PARTLY_CLOUDY
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        51, 53, 55, 56, 57 -> WeatherKind.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
        95, 96, 99 -> WeatherKind.STORM
        else -> WeatherKind.UNKNOWN
    }

    fun labelOf(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "Ensoleillé" else "Ciel dégagé"
        1 -> "Plutôt dégagé"
        2 -> "Partiellement nuageux"
        3 -> "Couvert"
        45, 48 -> "Brouillard"
        51, 53, 55 -> "Bruine"
        56, 57 -> "Bruine verglaçante"
        61, 63 -> "Pluie"
        65 -> "Forte pluie"
        66, 67 -> "Pluie verglaçante"
        71, 73 -> "Neige"
        75 -> "Fortes chutes de neige"
        77 -> "Grains de neige"
        80, 81 -> "Averses"
        82 -> "Violentes averses"
        85, 86 -> "Averses de neige"
        95 -> "Orage"
        96, 99 -> "Orage et grêle"
        else -> "Météo inconnue"
    }
}

/** Météo prête à afficher. */
data class WeatherReport(
    val temperature: Double,
    val code: Int,
    val isDay: Boolean,
    val days: List<DayForecast>,
    val fetchedAtMillis: Long,
)

data class DayForecast(
    /** Date au format ISO (AAAA-MM-JJ), dans le fuseau du lieu. */
    val date: String,
    val code: Int,
    val min: Double,
    val max: Double,
)
