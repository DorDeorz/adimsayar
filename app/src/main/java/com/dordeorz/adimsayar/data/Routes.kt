package com.dordeorz.adimsayar.data

import java.time.LocalDate
import kotlin.math.cos

class RouteStop(val name: String, val km: Int, val lat: Double, val lon: Double)

class VirtualRoute(val id: String, val stops: List<RouteStop>) {
    val totalKm: Int get() = stops.last().km
}

class RouteProgress(
    val route: VirtualRoute,
    val km: Double,
    val start: LocalDate,
    val finishedOn: LocalDate?,
) {
    val fraction: Float get() = (km / route.totalKm).toFloat().coerceIn(0f, 1f)
    val reached: Int get() = route.stops.indexOfLast { it.km <= km }
    val next: RouteStop? get() = route.stops.firstOrNull { it.km > km }
}

object Routes {

    val all = listOf(
        VirtualRoute(
            "istanbul_ankara",
            listOf(
                RouteStop("İstanbul", 0, 41.01, 28.98),
                RouteStop("İzmit", 111, 40.77, 29.92),
                RouteStop("Sakarya", 150, 40.78, 30.40),
                RouteStop("Düzce", 210, 40.84, 31.16),
                RouteStop("Bolu", 260, 40.74, 31.61),
                RouteStop("Gerede", 305, 40.80, 32.20),
                RouteStop("Ankara", 450, 39.93, 32.86),
            ),
        ),
        VirtualRoute(
            "izmir_istanbul",
            listOf(
                RouteStop("İzmir", 0, 38.42, 27.14),
                RouteStop("Manisa", 35, 38.61, 27.43),
                RouteStop("Balıkesir", 175, 39.65, 27.89),
                RouteStop("Bursa", 325, 40.19, 29.06),
                RouteStop("Yalova", 395, 40.65, 29.27),
                RouteStop("İstanbul", 480, 41.01, 28.98),
            ),
        ),
        VirtualRoute(
            "ankara_antalya",
            listOf(
                RouteStop("Ankara", 0, 39.93, 32.86),
                RouteStop("Konya", 260, 37.87, 32.48),
                RouteStop("Beyşehir", 355, 37.68, 31.72),
                RouteStop("Manavgat", 500, 36.79, 31.44),
                RouteStop("Antalya", 575, 36.90, 30.70),
            ),
        ),
        VirtualRoute(
            "black_sea",
            listOf(
                RouteStop("Samsun", 0, 41.29, 36.33),
                RouteStop("Ordu", 160, 40.98, 37.88),
                RouteStop("Giresun", 205, 40.91, 38.39),
                RouteStop("Trabzon", 335, 41.00, 39.72),
                RouteStop("Rize", 410, 41.02, 40.52),
                RouteStop("Hopa", 500, 41.39, 41.43),
                RouteStop("Artvin", 570, 41.18, 41.82),
            ),
        ),
        VirtualRoute(
            "lycian_way",
            listOf(
                RouteStop("Ölüdeniz", 0, 36.55, 29.12),
                RouteStop("Patara", 160, 36.26, 29.31),
                RouteStop("Kalkan", 175, 36.27, 29.41),
                RouteStop("Kaş", 210, 36.20, 29.64),
                RouteStop("Demre", 290, 36.24, 29.98),
                RouteStop("Finike", 330, 36.30, 30.15),
                RouteStop("Olimpos", 400, 36.40, 30.47),
                RouteStop("Antalya", 540, 36.90, 30.70),
            ),
        ),
        VirtualRoute(
            "camino",
            listOf(
                RouteStop("Saint-Jean-Pied-de-Port", 0, 43.16, -1.24),
                RouteStop("Pamplona", 68, 42.81, -1.65),
                RouteStop("Logroño", 160, 42.47, -2.45),
                RouteStop("Burgos", 280, 42.34, -3.70),
                RouteStop("León", 460, 42.60, -5.57),
                RouteStop("Astorga", 510, 42.46, -6.06),
                RouteStop("Ponferrada", 565, 42.55, -6.60),
                RouteStop("Sarria", 665, 42.78, -7.41),
                RouteStop("Santiago de Compostela", 780, 42.88, -8.54),
            ),
        ),
        VirtualRoute(
            "edirne_kars",
            listOf(
                RouteStop("Edirne", 0, 41.68, 26.56),
                RouteStop("İstanbul", 235, 41.01, 28.98),
                RouteStop("Bolu", 495, 40.74, 31.61),
                RouteStop("Ankara", 685, 39.93, 32.86),
                RouteStop("Yozgat", 905, 39.82, 34.81),
                RouteStop("Sivas", 1130, 39.75, 37.02),
                RouteStop("Erzincan", 1375, 39.75, 39.49),
                RouteStop("Erzurum", 1565, 39.90, 41.27),
                RouteStop("Kars", 1770, 40.60, 43.10),
            ),
        ),
    )

    fun byId(id: String): VirtualRoute? = all.firstOrNull { it.id == id }

    fun progress(route: VirtualRoute, history: Map<LocalDate, Long>, start: LocalDate, today: LocalDate, heightCm: Int): RouteProgress {
        var km = 0.0
        var finishedOn: LocalDate? = null
        var date = start
        while (!date.isAfter(today)) {
            km += Distance.km(history[date] ?: 0L, heightCm)
            if (finishedOn == null && km >= route.totalKm) finishedOn = date
            date = date.plusDays(1)
        }
        return RouteProgress(route, km, start, finishedOn)
    }

    fun projector(route: VirtualRoute, width: Float, height: Float, padding: Float): (Double, Double) -> Pair<Float, Float> {
        val lats = route.stops.map { it.lat }
        val lons = route.stops.map { it.lon }
        val scaleX = cos(Math.toRadians((lats.min() + lats.max()) / 2))
        val minX = lons.min() * scaleX
        val maxX = lons.max() * scaleX
        val minY = lats.min()
        val maxY = lats.max()
        val spanX = (maxX - minX).coerceAtLeast(0.01)
        val spanY = (maxY - minY).coerceAtLeast(0.01)
        val scale = minOf((width - padding * 2) / spanX, (height - padding * 2) / spanY)
        val offsetX = (width - spanX * scale) / 2
        val offsetY = (height - spanY * scale) / 2
        return { lat, lon ->
            (offsetX + (lon * scaleX - minX) * scale).toFloat() to (offsetY + (maxY - lat) * scale).toFloat()
        }
    }

    fun position(route: VirtualRoute, km: Double): Pair<Double, Double> {
        val stops = route.stops
        if (km <= 0.0) return stops.first().lat to stops.first().lon
        val nextIndex = stops.indexOfFirst { it.km > km }
        if (nextIndex <= 0) return stops.last().lat to stops.last().lon
        val from = stops[nextIndex - 1]
        val to = stops[nextIndex]
        val t = (km - from.km) / (to.km - from.km)
        return (from.lat + (to.lat - from.lat) * t) to (from.lon + (to.lon - from.lon) * t)
    }
}
