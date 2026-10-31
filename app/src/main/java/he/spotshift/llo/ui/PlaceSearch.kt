package he.spotshift.llo.ui

import android.content.Context
import android.location.Geocoder
import he.spotshift.llo.data.LatLng
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class PlaceResult(
    val name: String,
    val point: LatLng,
    val detail: String?,
)

/**
 * Photon (OpenStreetMap) is the primary provider: it needs no API key, understands
 * Chinese names and returns WGS-84, which is exactly what the app stores internally.
 * The platform geocoder is kept as a fallback.
 */
object PlaceSearch {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun search(context: Context, query: String): List<PlaceResult> =
        withContext(Dispatchers.IO) {
            val keyword = query.trim()
            if (keyword.isEmpty()) return@withContext emptyList()
            photon(keyword).takeIf { it.isNotEmpty() } ?: geocoder(context, keyword)
        }

    private fun photon(keyword: String): List<PlaceResult> = runCatching {
        val url = "https://photon.komoot.io/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", keyword)
            .addQueryParameter("limit", "8")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SpotShift/1.0 (Android)")
            .header("Accept", "application/json")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string().orEmpty()
        }
        parsePhoton(body, keyword)
    }.getOrDefault(emptyList())

    internal fun parsePhoton(body: String, keyword: String): List<PlaceResult> {
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        return buildList {
            for (index in 0 until features.length()) {
                val feature = features.optJSONObject(index) ?: continue
                val props = feature.optJSONObject("properties")
                val coordinates = feature.optJSONObject("geometry")
                    ?.optJSONArray("coordinates") ?: continue
                if (coordinates.length() < 2) continue
                val point = LatLng(coordinates.optDouble(1), coordinates.optDouble(0))
                if (!point.isValid) continue

                val name = props?.optString("name").orEmpty()
                    .ifBlank { props?.optString("street").orEmpty() }
                    .ifBlank { keyword }
                val detail = listOf(
                    props?.optString("housenumber").orEmpty(),
                    props?.optString("street").orEmpty(),
                    props?.optString("district").orEmpty(),
                    props?.optString("city").orEmpty(),
                    props?.optString("state").orEmpty(),
                ).filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" ")
                add(PlaceResult(name, point, detail.ifBlank { null }))
            }
        }.distinctBy { "${it.name}|${it.point.lat},${it.point.lng}" }
    }

    @Suppress("DEPRECATION")
    private fun geocoder(context: Context, keyword: String): List<PlaceResult> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = runCatching { geocoder.getFromLocationName(keyword, 8) }
            .getOrNull()
            .orEmpty()
        return addresses.mapNotNull { address ->
            val point = LatLng(address.latitude, address.longitude)
            if (!point.isValid) return@mapNotNull null
            PlaceResult(
                name = address.featureName
                    ?: address.locality
                    ?: address.subAdminArea
                    ?: keyword,
                point = point,
                detail = address.getAddressLine(0),
            )
        }.distinctBy { it.point }
    }
}
