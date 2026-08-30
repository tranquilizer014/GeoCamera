package com.geocamera.app

/**
 * Compact local implementation of the Open Location Code ("Plus Code") algorithm.
 * Produces the standard 10-character code with the '+' separator after the 8th digit,
 * e.g. "7JVQJ46R+8X". Runs fully offline, no network or third-party library required.
 */
object PlusCode {

    private const val CODE_ALPHABET = "23456789CFGHJMPQRVWX"
    private const val SEPARATOR = '+'
    private const val SEPARATOR_POSITION = 8
    private val PAIR_RESOLUTIONS = doubleArrayOf(20.0, 1.0, 0.05, 0.0025, 0.000125)

    fun encode(latitude: Double, longitude: Double): String {
        var lat = latitude.coerceIn(-90.0, 90.0)
        if (lat == 90.0) lat = 89.999999

        var lng = longitude
        while (lng < -180.0) lng += 360.0
        while (lng >= 180.0) lng -= 360.0

        var latRem = lat + 90.0
        var lngRem = lng + 180.0

        val sb = StringBuilder()
        for (i in PAIR_RESOLUTIONS.indices) {
            val res = PAIR_RESOLUTIONS[i]

            val latDigit = Math.floor(latRem / res).toInt().coerceIn(0, CODE_ALPHABET.length - 1)
            latRem -= latDigit * res
            sb.append(CODE_ALPHABET[latDigit])

            val lngDigit = Math.floor(lngRem / res).toInt().coerceIn(0, CODE_ALPHABET.length - 1)
            lngRem -= lngDigit * res
            sb.append(CODE_ALPHABET[lngDigit])
        }
        sb.insert(SEPARATOR_POSITION, SEPARATOR)
        return sb.toString()
    }
}
