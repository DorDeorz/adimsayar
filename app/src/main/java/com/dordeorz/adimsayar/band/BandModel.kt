package com.dordeorz.adimsayar.band

enum class BandShape { Band, SquareWatch, RoundWatch }

enum class BandLink { Ble, Classic, Both }

class BandModel(val name: String, val shape: BandShape, val link: BandLink, val tested: Boolean, private val pattern: Regex) {

    fun matches(deviceName: String): Boolean = pattern.matches(deviceName.trim())
}

object BandModels {

    private fun model(name: String, shape: BandShape, link: BandLink, pattern: String, tested: Boolean = false) =
        BandModel(name, shape, link, tested, Regex(pattern))

    private const val SUFFIX = "( ?[0-9A-Za-z]{4})?"

    val ALL: List<BandModel> = listOf(
        model("Xiaomi Smart Band 8 Active", BandShape.Band, BandLink.Ble, "^Xiaomi( Smart)? Band 8 Active$SUFFIX$"),
        model("Xiaomi Smart Band 8 Pro", BandShape.SquareWatch, BandLink.Classic, "^Xiaomi Smart Band 8 Pro$SUFFIX$"),
        model("Xiaomi Smart Band 8", BandShape.Band, BandLink.Ble, "^Xiaomi Smart Band 8$SUFFIX$", tested = true),
        model("Xiaomi Smart Band 9 Pro", BandShape.SquareWatch, BandLink.Classic, "^Xiaomi Smart Band 9 Pro$SUFFIX$"),
        model("Xiaomi Smart Band 9", BandShape.Band, BandLink.Classic, "^Xiaomi Smart Band 9$SUFFIX$"),
        model("Xiaomi Smart Band 7 Pro", BandShape.SquareWatch, BandLink.Ble, "^Xiaomi Smart Band 7 Pro.*$"),
        model("Redmi Smart Band 2", BandShape.Band, BandLink.Ble, "^Redmi Smart Band 2$SUFFIX$"),
        model("Redmi Smart Band Pro", BandShape.SquareWatch, BandLink.Ble, "^Redmi Band Pro$SUFFIX$"),
        model("Redmi Watch 2 Lite", BandShape.SquareWatch, BandLink.Ble, "^Redmi Watch 2 Lite$SUFFIX$"),
        model("Redmi Watch 2", BandShape.SquareWatch, BandLink.Ble, "^Redmi Watch 2$SUFFIX$"),
        model("Redmi Watch 3 Active", BandShape.SquareWatch, BandLink.Both, "^Redmi Watch 3 Active$SUFFIX$"),
        model("Redmi Watch 3", BandShape.SquareWatch, BandLink.Both, "^Redmi Watch 3$SUFFIX$"),
        model("Redmi Watch 4", BandShape.SquareWatch, BandLink.Classic, "^Redmi Watch 4$SUFFIX$"),
        model("Redmi Watch 5 Active", BandShape.SquareWatch, BandLink.Classic, "^Redmi Watch 5 Active$SUFFIX$"),
        model("Redmi Watch 5 Lite", BandShape.SquareWatch, BandLink.Classic, "^Redmi Watch 5 Lite$SUFFIX$"),
        model("Mi Watch Lite", BandShape.SquareWatch, BandLink.Ble, "^Mi Watch Lite_[0-9A-Za-z]{4}$"),
        model("Redmi Watch", BandShape.SquareWatch, BandLink.Ble, "^Redmi Watch$SUFFIX$"),
        model("Mi Watch Color Sport", BandShape.RoundWatch, BandLink.Ble, "^Mi ColorS$SUFFIX$"),
        model("Xiaomi Watch S1 Active", BandShape.RoundWatch, BandLink.Both, "^XiaomiWatchS1Active$SUFFIX$"),
        model("Xiaomi Watch S1 Pro", BandShape.RoundWatch, BandLink.Classic, "^(Xiaomi Watch S1 Pro$SUFFIX|[Ll]61.*[0-9A-F]{4})$"),
        model("Xiaomi Watch S1", BandShape.RoundWatch, BandLink.Both, "^Xiaomi Watch S1$SUFFIX$"),
        model("Xiaomi Watch S3", BandShape.RoundWatch, BandLink.Classic, "^Xiaomi Watch S3( eSIM)?$SUFFIX$"),
    )

    fun find(deviceName: String?): BandModel? {
        val name = deviceName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ALL.firstOrNull { it.matches(name) }
    }

    fun displayName(deviceName: String?): String? = find(deviceName)?.name ?: deviceName?.trim()?.takeIf { it.isNotEmpty() }

    fun shapeOf(deviceName: String?): BandShape {
        find(deviceName)?.let { return it.shape }
        val name = deviceName.orEmpty().lowercase()
        return when {
            "watch" in name -> BandShape.SquareWatch
            else -> BandShape.Band
        }
    }
}
