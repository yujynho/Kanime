package com.example.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder

enum class KanimeSortOrder(val apiValue: String, val displayName: String) {
    AZ("az", "A-Z"),
    ZA("za", "Z-A"),
    RECENTLY_UPLOADED("recently-uploaded", "Recently Uploaded"),
    RECENTLY_RELEASED("recently-released", "Recently Released"),
    OLDEST_UPLOADS("oldest-uploads", "Oldest Uploads"),
    OLDEST_RELEASES("oldest-releases", "Oldest Releases"),
    VIEW_COUNT("view-count", "View Count")
}

data class KstreamMediaItem(
    val id: String,
    val title: String,
    val posterUrl: String,
    val episodeUrl: String,
    val views: String? = null,
    val likes: String? = null
)

data class KstreamResolvedStream(
    val title: String,
    val posterUrl: String,
    val qualities: List<StreamQuality>
)

object kstreamScraper {
    private const val TAG = "kstreamScraper"
    private const val BASE_URL = "https://hstream.moe"

    suspend fun getFeed(
        page: Int = 1,
        query: String = "",
        order: String = "recently-uploaded"
    ): List<KstreamMediaItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val url = if (cleanQuery.isNotEmpty()) {
            "$BASE_URL/search?view=poster&search=${URLEncoder.encode(cleanQuery, "UTF-8")}&order=$order&page=$page"
        } else {
            "$BASE_URL/search?view=poster&order=$order&page=$page"
        }

        try {
            val html = NetworkClient.getHtml(url, mapOf("Referer" to "$BASE_URL/"))
            if (html.isBlank()) return@withContext emptyList()
            parseCatalogHtml(html)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching feed page $page with order $order: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseCatalogHtml(html: String): List<KstreamMediaItem> {
        val items = mutableListOf<KstreamMediaItem>()
        val doc = Jsoup.parse(html, BASE_URL)
        val cardElements = doc.select("a[href*='/hentai/']")

        val seenUrls = mutableSetOf<String>()
        for (a in cardElements) {
            val href = a.attr("href").trim()
            if (href.isEmpty() || !href.contains("/hentai/")) continue
            val fullUrl = if (href.startsWith("http")) href else "$BASE_URL${if (href.startsWith("/")) "" else "/"}$href"
            if (!seenUrls.add(fullUrl)) continue

            val img = a.selectFirst("img")
            var title = a.selectFirst("h3")?.text()?.trim().orEmpty()
            if (title.isEmpty()) {
                title = img?.attr("alt")?.trim().orEmpty()
            }
            if (title.isEmpty()) {
                title = fullUrl.substringAfterLast("/").replace("-", " ")
            }

            var imgSrc = img?.attr("src")?.trim().orEmpty()
            if (imgSrc.isEmpty() || imgSrc.startsWith("data:")) {
                imgSrc = img?.attr("data-src")?.trim().orEmpty()
            }
            var posterUrl = if (imgSrc.startsWith("http")) imgSrc else "$BASE_URL${if (imgSrc.startsWith("/")) "" else "/"}$imgSrc"

            // Ensure we strictly capture the full poster image and not gallery thumbnails
            if (posterUrl.contains("gallery-ep-") && posterUrl.contains("-thumbnail.webp")) {
                posterUrl = posterUrl.replace(Regex("""gallery-ep-(\d+)-\d+-thumbnail\.webp"""), "cover-ep-$1.webp")
            }

            val views = a.select("span:has(i.fa-eye)").text().trim().takeIf { it.isNotEmpty() }
            val likes = a.select("span:has(i.fa-heart)").text().trim().takeIf { it.isNotEmpty() }

            val id = fullUrl.substringAfterLast("/hentai/").substringBefore("?").ifEmpty { java.util.UUID.randomUUID().toString() }

            items.add(
                KstreamMediaItem(
                    id = id,
                    title = title,
                    posterUrl = posterUrl,
                    episodeUrl = fullUrl,
                    views = views,
                    likes = likes
                )
            )
        }
        return items
    }

    suspend fun resolveStream(episodeUrl: String): KstreamResolvedStream? = withContext(Dispatchers.IO) {
        try {
            val html = NetworkClient.getHtml(episodeUrl, mapOf("Referer" to "$BASE_URL/"))
            val doc = Jsoup.parse(html, episodeUrl)

            val eId = doc.selectFirst("input#e_id, input[name='e_id']")?.attr("value")?.trim()
                ?: Regex("""id=["']e_id["'][^>]*value=["'](\d+)["']""").find(html)?.groupValues?.get(1)
                ?: Regex("""value=["'](\d+)["'][^>]*id=["']e_id["']""").find(html)?.groupValues?.get(1)

            if (eId.isNullOrEmpty()) {
                Log.w(TAG, "Could not find e_id in episode page: $episodeUrl")
                return@withContext null
            }

            // Extract CSRF token from cookies
            val baseHttpUrl = "https://hstream.moe/".toHttpUrlOrNull()
            val cookies = if (baseHttpUrl != null) {
                NetworkClient.okHttpClient.cookieJar.loadForRequest(baseHttpUrl)
            } else {
                emptyList()
            }
            val xsrfCookie = cookies.firstOrNull { it.name == "XSRF-TOKEN" }?.value
            val decodedXsrf = xsrfCookie?.let {
                try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
            } ?: ""

            val jsonBody = JSONObject().apply {
                put("episode_id", eId)
            }.toString()

            val reqBuilder = Request.Builder()
                .url("$BASE_URL/player/api")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", episodeUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")

            if (decodedXsrf.isNotEmpty()) {
                reqBuilder.header("X-XSRF-TOKEN", decodedXsrf)
            }

            val response = NetworkClient.okHttpClient.newCall(reqBuilder.build()).await()
            val respBody = response.body?.string().orEmpty()
            response.close()

            if (respBody.isBlank()) return@withContext null
            val json = JSONObject(respBody)

            val title = json.optString("title", "Episode Stream")
            val poster = json.optString("poster", "")
            val fullPoster = if (poster.startsWith("http")) poster else if (poster.startsWith("/")) "$BASE_URL$poster" else "$BASE_URL/$poster"

            val streamUrl = json.optString("stream_url", "")
            val streamDomains = mutableListOf<String>()
            val domainsArr = json.optJSONArray("stream_domains")
            if (domainsArr != null) {
                for (i in 0 until domainsArr.length()) {
                    streamDomains.add(domainsArr.getString(i))
                }
            }
            val asiaDomainsArr = json.optJSONArray("asia_stream_domains")
            if (asiaDomainsArr != null) {
                for (i in 0 until asiaDomainsArr.length()) {
                    streamDomains.add(asiaDomainsArr.getString(i))
                }
            }

            val domain = streamDomains.firstOrNull() ?: "https://imoto-str.ane-h.xyz"
            val interpolated = json.optInt("interpolated", 0)

            val qualities = mutableListOf<StreamQuality>()

            // 4K / 2160p DASH
            qualities.add(
                StreamQuality(
                    quality = "2160p (4K DASH)",
                    url = "$domain/$streamUrl/2160/manifest.mpd",
                    format = "DASH",
                    headers = mapOf("Referer" to "$BASE_URL/")
                )
            )

            if (interpolated == 1) {
                qualities.add(
                    StreamQuality(
                        quality = "1080p 48fps (DASH)",
                        url = "$domain/$streamUrl/1080i/manifest.mpd",
                        format = "DASH",
                        headers = mapOf("Referer" to "$BASE_URL/")
                    )
                )
            }

            // 1080p DASH
            qualities.add(
                StreamQuality(
                    quality = "1080p (DASH)",
                    url = "$domain/$streamUrl/1080/manifest.mpd",
                    format = "DASH",
                    headers = mapOf("Referer" to "$BASE_URL/")
                )
            )

            // 720p DASH
            qualities.add(
                StreamQuality(
                    quality = "720p (DASH)",
                    url = "$domain/$streamUrl/720/manifest.mpd",
                    format = "DASH",
                    headers = mapOf("Referer" to "$BASE_URL/"),
                    isDefault = true
                )
            )

            // 720p MP4 Direct Fallback
            qualities.add(
                StreamQuality(
                    quality = "720p (MP4 Direct)",
                    url = "$domain/$streamUrl/x264.720p.mp4",
                    format = "MP4",
                    headers = mapOf("Referer" to "$BASE_URL/")
                )
            )

            KstreamResolvedStream(
                title = title,
                posterUrl = fullPoster,
                qualities = qualities
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving stream for $episodeUrl: ${e.message}", e)
            null
        }
    }
}
