package com.redowan

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.ResponseParser
import kotlin.reflect.KClass

fun getSearchQuality(check: String): SearchQuality? {
    return when(check.lowercase()){
        in "webrip" -> SearchQuality.WebRip
        in "web-dl" -> SearchQuality.WebRip
        in "bluray" -> SearchQuality.BlueRay
        in "hdts" -> SearchQuality.HdCam
        in "dvd" -> SearchQuality.DVD
        in "cam" -> SearchQuality.Cam
        in "camrip" -> SearchQuality.CamRip
        in "hdcam" -> SearchQuality.HdCam
        in "hdtc" -> SearchQuality.HdCam
        in "hdrip" -> SearchQuality.HD
        in "hd" -> SearchQuality.HD
        in "hdtv" -> SearchQuality.HD
        in "rip" -> SearchQuality.CamRip
        in "telecine" -> SearchQuality.Telecine
        in "telesync" -> SearchQuality.Telesync
        else -> null
    }
}

fun getVideoQuality(str: String?): Int {
    return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Qualities.Unknown.value
}


val parser = object : ResponseParser {
    val mapper: ObjectMapper = jacksonObjectMapper().configure(
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        false
    )

    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        return mapper.readValue(text, kClass.java)
    }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return try {
            mapper.readValue(text, kClass.java)
        } catch (e: Exception) {
            null
        }
    }

    override fun writeValueAsString(obj: Any): String {
        return mapper.writeValueAsString(obj)
    }
}