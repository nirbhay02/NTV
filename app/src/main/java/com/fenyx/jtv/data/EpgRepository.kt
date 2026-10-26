package com.fenyx.jtv.data

import java.text.SimpleDateFormat

/**
 * Legacy compatibility bridge for unit tests targeting com.fenyx.jtv.data.EpgRepository.
 */
object EpgRepository {
    fun parseXmltvMillis(raw: String?, fmt: SimpleDateFormat? = null): Long =
        com.jiotv.androidtv.data.repository.EpgRepository.parseXmltvMillis(raw, fmt)

    fun shortCast(raw: String?): String =
        com.jiotv.androidtv.data.repository.EpgRepository.shortCast(raw)

    fun shortNames(raw: String?, max: Int = 3): String =
        com.jiotv.androidtv.data.repository.EpgRepository.shortNames(raw, max)
}
