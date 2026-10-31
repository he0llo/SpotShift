package he.spotshift.llo.imports

import he.spotshift.llo.data.CoordSystem
import he.spotshift.llo.data.LatLng
import java.net.URLDecoder
import java.util.Locale
import kotlin.math.abs

/** A parsed import candidate, still in its original coordinate system. */
data class ImportDraft(
    val name: String,
    val rawPoint: LatLng,
    val source: CoordSystem,
    val raw: String,
    val note: String? = null,
)

data class ImportFailure(val raw: String, val reason: String)

data class ImportReport(
    val drafts: List<ImportDraft> = emptyList(),
    val failures: List<ImportFailure> = emptyList(),
) {
    val isEmpty: Boolean get() = drafts.isEmpty() && failures.isEmpty()
}

/**
 * Parses share texts and URI schemes from Amap / Baidu Maps (plus geo: URIs).
 * Everything is plain-string based so it can run on any thread without Android Uri.
 */
object SpotImport {

    private val URL_PATTERN = Regex(
        """(?i)\b(?:https?://|androidamap://|amapuri://|iosamap://|baidumap://|geo:)[^\s<>"']+"""
    )
    private val PAIR_PATTERN = Regex(
        """(?<![\d.])([-+]?\d{1,3}(?:\.\d+)?)\s*[,，]\s*([-+]?\d{1,3}(?:\.\d+)?)(?![\d.])"""
    )
    private val LABELED_PATTERN = Regex(
        """(?i)(?:location|position|center|latlng|destination|坐标|经纬度|位置)\s*[=:：]?\s*([-+]?\d{1,3}(?:\.\d+)?)\s*[,，]\s*([-+]?\d{1,3}(?:\.\d+)?)"""
    )
    private val LAT_LNG_PATTERN = Regex(
        """(?i)(?:lat|latitude)\s*[=:：]\s*([-+]?\d{1,3}(?:\.\d+)?)[^\d+-]+(?:lng|lon|long|longitude)\s*[=:：]\s*([-+]?\d{1,3}(?:\.\d+)?)"""
    )
    private val LNG_LAT_PATTERN = Regex(
        """(?i)(?:lng|lon|long|longitude)\s*[=:：]\s*([-+]?\d{1,3}(?:\.\d+)?)[^\d+-]+(?:lat|latitude)\s*[=:：]\s*([-+]?\d{1,3}(?:\.\d+)?)"""
    )
    private val NAME_LABEL = Regex(
        """^(?:地点名称|名称|地点|位置|地址|标题|poi|name|title)\s*[:：]?\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val NAME_NOISE = Regex(
        """(?i)(我在|高德地图|百度地图|腾讯地图|发现了一个?好地方|快来看看吧?|分享自?|的位置|的坐标|经纬度|坐标|分享|点击查看|打开地图|详情)"""
    )
    private val EDGE_PUNCT = Regex(
        """^[\s,，。;；:：、\-—_|/\\【】\[\]()（）"'“”]+|[\s,，。;；:：、\-—_|/\\【】\[\]()（）"'“”]+$"""
    )
    private val WHITESPACE = Regex("""\s+""")

    private val AMAP_SCHEMES = setOf("androidamap", "amapuri", "iosamap")
    private const val NAME_KEYS = "name,title,poiname,dname,sname,q,content"

    fun parse(text: String): ImportReport {
        val input = text.trim()
        if (input.isEmpty()) {
            return ImportReport(failures = listOf(ImportFailure("", "内容为空，请粘贴分享文本或链接")))
        }

        val urls = URL_PATTERN.findAll(input).map { it.value.trimEnd(',', '，', '。', ')', '）', ']', '】') }.toList()
        if (urls.size > 1) {
            return combine(urls.map { parseOne(it) })
        }

        val lines = input.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size > 1) {
            val parsed = lines.map { parseOne(it) }
            if (parsed.count { it.draft != null } >= 2) return combine(parsed)
        }

        return combine(listOf(parseOne(input)))
    }

    private fun combine(items: List<One>): ImportReport = ImportReport(
        drafts = items.mapNotNull { it.draft },
        failures = items.mapNotNull { item ->
            item.reason?.let { ImportFailure(item.raw, it) }
        },
    )

    private data class One(val raw: String, val draft: ImportDraft? = null, val reason: String? = null)

    private fun parseOne(rawInput: String): One {
        val raw = rawInput.trim()
        if (raw.isEmpty()) return One(raw, reason = "空行")

        var urlFailure: One? = null
        val urlMatch = URL_PATTERN.find(raw)
        if (urlMatch != null) {
            val url = urlMatch.value.trimEnd(',', '，', '。', ')', '）', ']', '】')
            val parsedUrl = parseUrl(url, raw)
            if (parsedUrl?.draft != null) return parsedUrl
            urlFailure = parsedUrl
        }

        val system = systemHint(raw)
        val point = pointFromText(raw)
            ?: return urlFailure
            ?: One(raw, reason = "未检测到经纬度。请粘贴包含坐标的分享文本或链接。")

        if (!point.isValid) return One(raw, reason = "坐标超出有效范围：${point.format()}")
        return One(
            raw,
            draft = ImportDraft(
                name = cleanName(raw) ?: defaultName(system),
                rawPoint = point,
                source = system,
                raw = raw,
                note = if (system == CoordSystem.WGS84) "未识别来源，按 WGS-84 解析；如来自高德/百度请在预览中切换坐标系" else null,
            ),
        )
    }

    private fun parseUrl(url: String, contextText: String): One? {
        val scheme = url.substringBefore(':').lowercase(Locale.ROOT)
        if (scheme == "geo") return parseGeo(url, contextText)
        if (scheme in AMAP_SCHEMES) return parseQueryUrl(url, CoordSystem.GCJ02, "高德", contextText)
        if (scheme == "baidumap") return parseQueryUrl(url, CoordSystem.BD09, "百度", contextText)

        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase(Locale.ROOT).orEmpty()
        val base = when {
            host.contains("amap") || host.contains("autonavi") -> CoordSystem.GCJ02 to "高德"
            host.contains("baidu") -> CoordSystem.BD09 to "百度"
            else -> return null
        }
        return parseQueryUrl(url, base.first, base.second, contextText)
    }

    private fun parseGeo(url: String, contextText: String): One {
        val body = url.removePrefix("geo:").substringBefore(';').substringBefore('?')
        val match = PAIR_PATTERN.find(body)
            ?: return One(contextText, reason = "geo: 链接格式不正确，应为 geo:纬度,经度")
        val point = latLngFrom(match.groupValues[1], match.groupValues[2], CoordSystem.WGS84, geoOrder = true)
        val name = cleanName(contextText) ?: "导入地点"
        return One(contextText, draft = ImportDraft(name, point, CoordSystem.WGS84, contextText, "来自 geo: 链接"))
    }

    private fun parseQueryUrl(
        url: String,
        defaultSystem: CoordSystem,
        label: String,
        contextText: String,
    ): One {
        val params = queryParams(url)
        val system = systemFromParams(params, defaultSystem)

        val named = namedPoint(params)
        val amapPoi = params["p"]?.let { parseAmapPoi(it) }
        val field = named ?: amapPoi?.point
            ?: firstParam(params, "position", "location", "center", "latlng", "dest", "destination")
                ?.substringBefore('|')
                ?.let { fieldPoint(it) }

        if (field == null) {
            return One(
                contextText,
                reason = "该${label}链接中未找到经纬度。请重新分享并包含坐标，或直接粘贴分享文本。",
            )
        }
        if (!field.isValid) {
            return One(contextText, reason = "链接中的坐标无效：${field.format()}")
        }

        val name = nameFromParams(params)
            ?: amapPoi?.name
            ?: nameFromPath(url)
            ?: cleanName(contextText)
            ?: defaultName(system)
        return One(
            contextText,
            draft = ImportDraft(
                name = name,
                rawPoint = field,
                source = system,
                raw = contextText,
                note = "已从${label}链接解析（${system.shortLabel}）",
            ),
        )
    }

    private fun pointFromText(text: String): LatLng? {
        LAT_LNG_PATTERN.find(text)?.let {
            return LatLng(it.groupValues[1].toDouble(), it.groupValues[2].toDouble())
        }
        LNG_LAT_PATTERN.find(text)?.let {
            return LatLng(it.groupValues[2].toDouble(), it.groupValues[1].toDouble())
        }
        LABELED_PATTERN.find(text)?.let {
            return latLngFrom(it.groupValues[1], it.groupValues[2], systemHint(text))
        }
        val pair = PAIR_PATTERN.findAll(text).lastOrNull() ?: return null
        val first = pair.groupValues[1].toDoubleOrNull() ?: return null
        val second = pair.groupValues[2].toDoubleOrNull() ?: return null
        return resolveOrder(first, second, systemHint(text))
    }

    private fun latLngFrom(a: String, b: String, fallback: CoordSystem, geoOrder: Boolean = false): LatLng {
        val first = a.toDoubleOrNull() ?: 0.0
        val second = b.toDoubleOrNull() ?: 0.0
        return if (geoOrder) LatLng(first, second) else resolveOrder(first, second, fallback)
    }

    /**
     * Map links rarely say whether a pair is "lat,lng" or "lng,lat".
     * In China one value is always > 54 (longitude), which disambiguates almost every case.
     */
    private fun resolveOrder(a: Double, b: Double, fallback: CoordSystem): LatLng = when {
        abs(a) > 54 && abs(b) <= 54 -> LatLng(b, a)
        abs(b) > 54 && abs(a) <= 54 -> LatLng(a, b)
        fallback == CoordSystem.BD09 -> LatLng(a, b)
        else -> LatLng(b, a)
    }

    private fun namedPoint(params: Map<String, String>): LatLng? {
        val lat = firstDouble(params, "lat", "latitude", "dlat", "slat")
        val lng = firstDouble(params, "lng", "lon", "long", "longitude", "dlon", "slon")
        if (lat == null || lng == null) return null
        val point = LatLng(lat, lng)
        return if (point.isValid) point else null
    }

    private fun fieldPoint(value: String): LatLng? {
        val match = PAIR_PATTERN.find(value) ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val b = match.groupValues[2].toDoubleOrNull() ?: return null
        return resolveOrder(a, b, CoordSystem.WGS84)
    }

    /**
     * Amap web links carry POI data as `p=poiId,lat,lng,name,address` (GCJ-02).
     * This is what surl.amap.com short links redirect to, and it keeps the POI name.
     */
    private fun parseAmapPoi(value: String): AmapPoi? {
        val parts = value.split(',', limit = 5)
        if (parts.size < 3) return null
        val lat = parts[1].toDoubleOrNull() ?: return null
        val lng = parts[2].toDoubleOrNull() ?: return null
        val point = resolveOrder(lat, lng, CoordSystem.GCJ02)
        if (!point.isValid) return null
        val name = parts.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }
        return AmapPoi(point, name)
    }

    private data class AmapPoi(val point: LatLng, val name: String?)

    private fun queryParams(url: String): Map<String, String> {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return emptyMap()
        return buildMap {
            query.split('&').forEach { segment ->
                val index = segment.indexOf('=')
                if (index <= 0) return@forEach
                val key = decode(segment.substring(0, index)).lowercase(Locale.ROOT)
                val value = decode(segment.substring(index + 1))
                if (key.isNotEmpty()) putIfAbsent(key, value)
            }
        }
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun firstParam(params: Map<String, String>, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { params[it]?.takeIf(String::isNotBlank) }

    private fun firstDouble(params: Map<String, String>, vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { params[it]?.toDoubleOrNull() }

    private fun systemFromParams(params: Map<String, String>, default: CoordSystem): CoordSystem {
        val hint = (params["coord_type"] ?: params["coordinate"] ?: params["coordinate_system"]
            ?: params["crs"]).orEmpty().lowercase(Locale.ROOT)
        return when {
            hint.contains("bd09") || hint.contains("baidu") -> CoordSystem.BD09
            hint.contains("gcj") || hint.contains("gaode") || hint.contains("amap") -> CoordSystem.GCJ02
            hint.contains("wgs") || hint.contains("gps") -> CoordSystem.WGS84
            else -> default
        }
    }

    private fun systemHint(text: String): CoordSystem {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("百度") || lower.contains("baidu") || lower.contains("bd09") -> CoordSystem.BD09
            lower.contains("高德") || lower.contains("amap") || lower.contains("gaode") ||
                lower.contains("gcj") -> CoordSystem.GCJ02
            else -> CoordSystem.WGS84
        }
    }

    private fun nameFromParams(params: Map<String, String>): String? {
        val raw = NAME_KEYS.split(',').firstNotNullOfOrNull { key ->
            params[key]?.takeIf { it.isNotBlank() && !PAIR_PATTERN.containsMatchIn(it) }
        } ?: return null
        return raw.replace(WHITESPACE, " ").trim().take(48).ifBlank { null }
    }

    private fun nameFromPath(url: String): String? {
        val path = runCatching { java.net.URI(url).path }.getOrNull() ?: return null
        val marker = path.indexOf("/poi/")
        if (marker < 0) return null
        val segment = path.substring(marker + 5).substringBefore('/')
        return decode(segment).trim().take(48).ifBlank { null }
    }

    private fun cleanName(text: String): String? {
        var candidate = URL_PATTERN.replace(text, " ")
        candidate = PAIR_PATTERN.replace(candidate, " ")
        candidate = LABELED_PATTERN.replace(candidate, " ")
        candidate = candidate.lines().firstOrNull { it.isNotBlank() } ?: return null
        candidate = candidate.replace(NAME_LABEL, "")
            .replace(NAME_NOISE, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .replace(EDGE_PUNCT, "")
            .trim()
        if (candidate.length > 48) candidate = candidate.take(48).trim()
        if (candidate.isEmpty() || candidate.all { it.isDigit() || it in ",.:- " }) return null
        return candidate
    }

    private fun defaultName(system: CoordSystem): String = when (system) {
        CoordSystem.WGS84 -> "导入地点"
        CoordSystem.GCJ02 -> "高德地点"
        CoordSystem.BD09 -> "百度地点"
    }
}
