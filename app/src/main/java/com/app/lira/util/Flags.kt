package com.app.lira.util

import java.util.Locale

/**
 * Country flags for trip creation: recognises countries (and big cities) named in a trip's
 * name ("Japan & Seoul" → 🇯🇵 🇰🇷) and the currency each one uses.
 */
object Flags {
    private val countries: Set<String> by lazy { Locale.getISOCountries().toSet() }

    // ───────────── Countries from a trip name ("Japan & Seoul" → JP, KR) ─────────────

    /** Extra names people type: short forms, other spellings, and big cities. */
    private val aliases = mapOf(
        "usa" to "US", "america" to "US", "etats unis" to "US", "new york" to "US", "nyc" to "US",
        "los angeles" to "US", "miami" to "US", "san francisco" to "US", "las vegas" to "US", "chicago" to "US",
        "uk" to "GB", "england" to "GB", "angleterre" to "GB", "scotland" to "GB", "ecosse" to "GB", "london" to "GB", "londres" to "GB",
        "korea" to "KR", "coree" to "KR", "seoul" to "KR", "busan" to "KR",
        "tokyo" to "JP", "kyoto" to "JP", "osaka" to "JP",
        "paris" to "FR", "lyon" to "FR", "marseille" to "FR",
        "beirut" to "LB", "beyrouth" to "LB", "liban" to "LB", "lebanon" to "LB",
        "rome" to "IT", "milan" to "IT", "venice" to "IT", "venise" to "IT", "florence" to "IT", "naples" to "IT",
        "madrid" to "ES", "barcelona" to "ES", "barcelone" to "ES", "ibiza" to "ES", "seville" to "ES",
        "berlin" to "DE", "munich" to "DE", "lisbon" to "PT", "lisbonne" to "PT", "porto" to "PT",
        "amsterdam" to "NL", "holland" to "NL", "hollande" to "NL", "brussels" to "BE", "bruxelles" to "BE",
        "istanbul" to "TR", "dubai" to "AE", "abu dhabi" to "AE", "emirates" to "AE", "emirats" to "AE",
        "bangkok" to "TH", "phuket" to "TH", "bali" to "ID", "jakarta" to "ID", "hanoi" to "VN", "saigon" to "VN",
        "ho chi minh" to "VN", "singapore" to "SG", "singapour" to "SG", "hong kong" to "HK", "beijing" to "CN",
        "shanghai" to "CN", "pekin" to "CN", "taipei" to "TW", "sydney" to "AU", "melbourne" to "AU",
        "montreal" to "CA", "toronto" to "CA", "vancouver" to "CA", "mexico city" to "MX", "cancun" to "MX",
        "rio" to "BR", "sao paulo" to "BR", "buenos aires" to "AR", "marrakech" to "MA", "casablanca" to "MA",
        "cairo" to "EG", "le caire" to "EG", "athens" to "GR", "athenes" to "GR", "santorini" to "GR", "mykonos" to "GR",
        "prague" to "CZ", "vienna" to "AT", "vienne" to "AT", "budapest" to "HU", "zurich" to "CH", "geneva" to "CH",
        "geneve" to "CH", "dublin" to "IE", "reykjavik" to "IS", "copenhagen" to "DK", "stockholm" to "SE",
        "oslo" to "NO", "helsinki" to "FI", "warsaw" to "PL", "krakow" to "PL", "moscow" to "RU", "kyiv" to "UA",
        "tbilisi" to "GE", "yerevan" to "AM", "amman" to "JO", "petra" to "JO", "doha" to "QA", "riyadh" to "SA",
        "mumbai" to "IN", "delhi" to "IN", "goa" to "IN", "kathmandu" to "NP", "colombo" to "LK", "maldives" to "MV",
        "cape town" to "ZA", "nairobi" to "KE", "zanzibar" to "TZ", "tunis" to "TN", "djerba" to "TN", "dakar" to "SN"
    )

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /** normalized name → region, longest names first so "south korea" wins over "korea". */
    private val nameIndex: List<Pair<String, String>> by lazy {
        val map = HashMap<String, String>()
        val locales = listOf(Locale.ENGLISH, Locale.FRENCH, Locale.getDefault())
        countries.forEach { cc ->
            val loc = Locale("", cc)
            locales.forEach { l -> normalize(loc.getDisplayCountry(l)).takeIf { it.length >= 3 }?.let { map.putIfAbsent(it, cc) } }
        }
        aliases.forEach { (k, v) -> map[normalize(k)] = v }
        map.entries.map { it.key to it.value }.sortedByDescending { it.first.length }
    }

    /** Regions named in [text], in the order they appear ("Japan & Seoul" → [JP, KR]). */
    fun countriesIn(text: String): List<String> {
        var hay = " " + normalize(text) + " "
        if (hay.isBlank()) return emptyList()
        val found = mutableListOf<Pair<Int, String>>()
        for ((name, cc) in nameIndex) {
            val needle = " $name "
            val at = hay.indexOf(needle)
            if (at >= 0) {
                found += at to cc
                // Blank it out so "korea" doesn't match again inside "south korea".
                hay = hay.replaceRange(at + 1, at + 1 + name.length, " ".repeat(name.length))
            }
        }
        return found.sortedBy { it.first }.map { it.second }.distinct()
    }

    /** Flags of the countries in a trip name ("Japan & Seoul" → "🇯🇵🇰🇷"), or null. Cached: names repeat. */
    fun forTripName(name: String): String? = tripCache.getOrPut(name) {
        countriesIn(name).joinToString("") { emoji(it) }
    }.ifEmpty { null }

    private val tripCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** "🇯🇵 Japan" — the trip name with its flag on the left (unchanged when no country is recognised). */
    fun tripLabel(name: String): String {
        val flags = forTripName(name) ?: return name
        return if (name.firstOrNull()?.let { Character.isSurrogate(it) } == true) name else "$flags $name"
    }

    /** Currency used in [region] ("JP" → "JPY"). */
    fun currencyOf(region: String): String? =
        runCatching { java.util.Currency.getInstance(Locale("", region))?.currencyCode }.getOrNull()

    /** Country name for display ("JP" → "Japan"). */
    fun countryName(region: String): String = Locale("", region).getDisplayCountry(Locale.getDefault()).ifBlank { region }

    /** Regional-indicator pair for a two-letter region code. */
    fun emoji(region: String): String {
        val r = region.uppercase()
        val base = 0x1F1E6 - 'A'.code
        return String(Character.toChars(base + r[0].code)) + String(Character.toChars(base + r[1].code))
    }
}
