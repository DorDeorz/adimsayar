package com.dordeorz.adimsayar.band

enum class BandShape { Band, SquareWatch, RoundWatch }

enum class BandLink { Ble, Classic, Both }

/**
 * Xiaomi: protobuf protocol (Mi Fitness devices). Huami: older Mi Band 2–6 and Amazfit protocol (Zepp Life devices).
 * WearFit: WearFit Pro clone watches (HK series), no key needed.
 */
enum class BandFamily { Xiaomi, Huami, WearFit }

class BandModel(
    val name: String,
    val shape: BandShape,
    val link: BandLink,
    val tested: Boolean,
    private val pattern: Regex,
    val family: BandFamily = BandFamily.Xiaomi,
    /** Huami only: authentication crypt flags and bytes per minute in the activity data. */
    val huamiCrypt: Int = 0,
    val huamiSampleSize: Int = 4,
) {

    val needsKey: Boolean get() = family != BandFamily.WearFit

    fun matches(deviceName: String): Boolean = pattern.matches(deviceName.trim())
}

object BandModels {

    private fun model(name: String, shape: BandShape, link: BandLink, pattern: String, tested: Boolean = false) =
        BandModel(name, shape, link, tested, Regex(pattern))

    private fun huami(name: String, shape: BandShape, pattern: String, crypt: Int = 0x80, sampleSize: Int = 4) =
        BandModel(name, shape, BandLink.Ble, false, Regex(pattern, RegexOption.IGNORE_CASE), BandFamily.Huami, crypt, sampleSize)

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
        huami("Mi Band 2", BandShape.Band, "^MI Band 2$", crypt = 0),
        huami("Mi Band 3", BandShape.Band, "^Mi Band 3$", crypt = 0),
        huami("Mi Smart Band 4", BandShape.Band, "^Mi Smart Band 4$"),
        huami("Mi Smart Band 5", BandShape.Band, "^Mi Smart Band 5$"),
        huami("Mi Smart Band 6", BandShape.Band, "^Mi Smart Band 6$"),
        huami("Amazfit Band 5", BandShape.Band, "^Amazfit Band 5$"),
        huami("Amazfit Band 2", BandShape.Band, "^Amazfit (Band 2|Cor 2)$", crypt = 0),
        huami("Amazfit Band", BandShape.Band, "^Amazfit (Band|Cor)$", crypt = 0),
        huami("Amazfit Neo", BandShape.SquareWatch, "^Amazfit Neo$"),
        huami("Amazfit X", BandShape.SquareWatch, "^Amazfit X$"),
        huami("Amazfit Bip", BandShape.SquareWatch, "^Amazfit Bip Watch$", crypt = 0),
        huami("Amazfit Bip Lite", BandShape.SquareWatch, "^Amazfit Bip Lite$"),
        huami("Amazfit Bip S Lite", BandShape.SquareWatch, "^(Amazfit )?Bip S Lite$"),
        huami("Amazfit Bip S", BandShape.SquareWatch, "^Amazfit Bip S$"),
        huami("Amazfit Bip U Pro", BandShape.SquareWatch, "^Amazfit Bip U Pro$"),
        huami("Amazfit Bip U", BandShape.SquareWatch, "^Amazfit Bip U$"),
        huami("Amazfit Bip 3 Pro", BandShape.SquareWatch, "^Amazfit Bip 3 Pro$"),
        huami("Amazfit Bip 3", BandShape.SquareWatch, "^Amazfit Bip 3$"),
        huami("Amazfit Pop Pro", BandShape.SquareWatch, "^Amazfit Pop Pro$"),
        huami("Amazfit Pop", BandShape.SquareWatch, "^Amazfit Pop$"),
        huami("Amazfit GTS 2 mini", BandShape.SquareWatch, "^Amazfit GTS ?2 mini$", sampleSize = 8),
        huami("Amazfit GTS 2e", BandShape.SquareWatch, "^Amazfit GTS 2e$", sampleSize = 8),
        huami("Amazfit GTS 2", BandShape.SquareWatch, "^Amazfit GTS 2$", sampleSize = 8),
        huami("Amazfit GTS", BandShape.SquareWatch, "^Amazfit GTS$", sampleSize = 8),
        huami("Amazfit GTR 2e", BandShape.RoundWatch, "^Amazfit GTR 2e$", sampleSize = 8),
        huami("Amazfit GTR 2", BandShape.RoundWatch, "^Amazfit GTR 2$", sampleSize = 8),
        huami("Amazfit GTR Lite", BandShape.RoundWatch, "^Amazfit GTR L$", sampleSize = 8),
        huami("Amazfit GTR", BandShape.RoundWatch, "^Amazfit GTR$", sampleSize = 8),
        huami("Amazfit T-Rex Pro", BandShape.RoundWatch, "^Amazfit T-Rex Pro$", sampleSize = 8),
        huami("Amazfit T-Rex", BandShape.RoundWatch, "^Amazfit T-Rex$", sampleSize = 8),
        huami("Amazfit Verge Lite", BandShape.RoundWatch, "^Amazfit Verge L$"),
        huami("Zepp E", BandShape.RoundWatch, "^Zepp E$", sampleSize = 8),
        BandModel("WearFit Pro (HK)", BandShape.SquareWatch, BandLink.Ble, false, Regex("^HK.*"), BandFamily.WearFit),
    )

    /** Used for a band whose key came from the Zepp account before its Bluetooth name is known. */
    val GENERIC_HUAMI: BandModel = huami("Mi Band / Amazfit", BandShape.Band, "^$")

    fun find(deviceName: String?): BandModel? {
        val name = deviceName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ALL.firstOrNull { it.matches(name) }
    }

    fun displayName(deviceName: String?): String? {
        val name = deviceName?.trim()?.takeIf { it.isNotEmpty() }
        val model = find(name)
        // Clone watches share one protocol under many names; their own name says more.
        return if (model == null || model.family == BandFamily.WearFit) name else model.name
    }

    fun shapeOf(deviceName: String?): BandShape {
        find(deviceName)?.let { return it.shape }
        val name = deviceName.orEmpty().lowercase()
        return when {
            "watch" in name -> BandShape.SquareWatch
            else -> BandShape.Band
        }
    }
}
