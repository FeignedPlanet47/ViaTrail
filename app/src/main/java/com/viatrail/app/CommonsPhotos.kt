package com.viatrail.app

import com.viatrail.domain.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/** Only show Commons media with verified CC0 or public-domain metadata. No unsourced scraping. */
data class LicensedPhoto(val thumbUrl: String, val descriptionUrl: String, val license: String, val artist: String)

object CommonsPhotos {
    private val client = OkHttpClient()
    suspend fun find(place: Place): LicensedPhoto? = withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode(place.name, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search" +
                "&gsrsearch=${query}&gsrnamespace=6&gsrlimit=6&prop=imageinfo" +
                "&iiprop=url%7Cextmetadata&iiurlwidth=720&format=json&formatversion=2"
            val request = Request.Builder().url(url)
                .header("User-Agent", "ViaTrail/0.3 (https://github.com/FeignedPlanet47/ViaTrail; map photo attribution)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val root = JSONObject(response.body?.string().orEmpty())
                val pages = root.optJSONObject("query")?.optJSONArray("pages") ?: return@withContext null
                for (i in 0 until pages.length()) {
                    val obj = pages.optJSONObject(i) ?: continue
                    val info = obj.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                    val md = info.optJSONObject("extmetadata") ?: continue
                    fun field(name: String): String = md.optJSONObject(name)?.optString("value").orEmpty()
                    val license = field("LicenseShortName")
                    val allowed = license.equals("CC0",true) || license.equals("Public domain",true) ||
                        license.equals("PD",true) || license.equals("CC0 1.0",true)
                    if (!allowed) continue
                    val thumb = info.optString("thumburl")
                    val pageUrl = info.optString("descriptionurl")
                    if (!thumb.startsWith("https://") || !pageUrl.startsWith("https://commons.wikimedia.org/")) continue
                    val artist = field("Artist").replace(Regex("<[^>]*>"), "").take(120).ifBlank { "Автор указан на Wikimedia Commons" }
                    return@withContext LicensedPhoto(thumb,pageUrl,license,artist)
                }
                null
            }
        } catch (_: Exception) { null }
    }
}
