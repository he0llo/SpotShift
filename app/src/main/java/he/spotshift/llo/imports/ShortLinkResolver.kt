package he.spotshift.llo.imports

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ShortLinkResult(val text: String, val failures: List<ImportFailure>)

/**
 * Amap / Baidu share sheets often produce a short link that only redirects to the
 * real URL. The redirect target carries the coordinates and POI name, so expand
 * short links before handing the text to [SpotImport].
 */
object ShortLinkResolver {

    private val SHORT_LINK = Regex(
        """(?i)https?://(?:surl\.amap\.com|j\.map\.baidu\.com)/[A-Za-z0-9_-]+"""
    )
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun expand(text: String): ShortLinkResult = withContext(Dispatchers.IO) {
        val links = SHORT_LINK.findAll(text).map { it.value }.distinct().toList()
        val resolved = links.associateWith { link -> resolve(link) }

        var expanded = text
        resolved.forEach { (short, long) ->
            if (long != null) expanded = expanded.replace(short, long)
        }
        val failures = resolved.filterValues { it == null }.keys.map { link ->
            ImportFailure(link, "短链接解析失败，请检查网络或粘贴高德/百度地图的完整分享文本")
        }
        ShortLinkResult(expanded, failures)
    }

    private fun resolve(link: String): String? {
        var current = link
        repeat(4) {
            val request = Request.Builder()
                .url(current)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) SpotShift")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val next = response.header("Location")
                    if (!response.isRedirect || next.isNullOrBlank()) {
                        val finalUrl = response.request.url.toString()
                        return if (SHORT_LINK.matches(finalUrl)) null else finalUrl
                    }
                    val resolved = response.request.url.resolve(next)?.toString() ?: return null
                    // The first redirect already points at the real page; don't fetch it
                    // again just to read a URL we already have.
                    if (!SHORT_LINK.matches(resolved)) return resolved
                    current = resolved
                }
            } catch (_: IOException) {
                return null
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
        return null
    }
}
