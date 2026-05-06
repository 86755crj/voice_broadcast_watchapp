package com.crj.voicebroadcast.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 用 OkHttp 拉 RSS XML，stdlib XmlPullParser 解析。
 * 不引第三方 RSS 库（dependency 越少越好，APK 越小）。
 *
 * RSS 2.0 结构：<rss><channel><item><title/><pubDate/><guid/><enclosure url="..."/></item>...
 */
object RssClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // RFC 822: "Mon, 06 May 2024 06:30:00 +0800"
    private val rfc822 = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    suspend fun fetch(category: Category): List<Episode> = withContext(Dispatchers.IO) {
        val url = category.feedUrl ?: return@withContext emptyList()
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            parse(body, category.id)
        }
    }

    fun parse(xml: String, categoryId: String): List<Episode> {
        val out = mutableListOf<Episode>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var event = parser.eventType
        var inItem = false
        var title = ""
        var pubDate = 0L
        var guid = ""
        var enclosureUrl = ""
        var duration = 0
        var currentTag = ""

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag.lowercase(Locale.US)) {
                        "item" -> {
                            inItem = true
                            title = ""; pubDate = 0L; guid = ""; enclosureUrl = ""; duration = 0
                        }
                        "enclosure" -> if (inItem) {
                            enclosureUrl = parser.getAttributeValue(null, "url") ?: ""
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inItem) {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        when (currentTag.lowercase(Locale.US)) {
                            "title" -> if (title.isEmpty()) title = text
                            "pubdate" -> pubDate = parsePubDate(text)
                            "guid" -> guid = text
                            "itunes:duration", "duration" -> duration = parseDuration(text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", true) && inItem) {
                        val key = guid.ifEmpty { enclosureUrl }
                        if (key.isNotEmpty() && enclosureUrl.isNotEmpty()) {
                            out += Episode(
                                guid = key,
                                categoryId = categoryId,
                                title = title.ifEmpty { "(无标题)" },
                                pubDate = if (pubDate > 0) pubDate else System.currentTimeMillis(),
                                enclosureUrl = enclosureUrl,
                                durationSec = duration
                            )
                        }
                        inItem = false
                    }
                    currentTag = ""
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun parsePubDate(s: String): Long = try {
        rfc822.parse(s)?.time ?: 0L
    } catch (_: Throwable) {
        try { Date().time } catch (_: Throwable) { 0L }
    }

    /** "1234" 秒，或 "12:34" / "01:02:03" */
    private fun parseDuration(s: String): Int = try {
        if (s.contains(":")) {
            val parts = s.split(":").map { it.toInt() }
            when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                else -> 0
            }
        } else s.toInt()
    } catch (_: Throwable) { 0 }
}
