package he.spotshift.llo.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Coordinate systems used when importing and displaying locations. */
enum class CoordSystem(val label: String, val shortLabel: String) {
    WGS84("WGS-84 国际标准", "WGS-84"),
    GCJ02("GCJ-02 高德 / 腾讯", "GCJ-02"),
    BD09("BD-09 百度", "BD-09");

    companion object {
        fun fromName(name: String?): CoordSystem =
            entries.firstOrNull { it.name == name } ?: WGS84
    }
}

data class LatLng(val lat: Double, val lng: Double) {
    val isValid: Boolean
        get() = lat in -90.0..90.0 && lng in -180.0..180.0

    fun format(): String = "${lat.coord6()}, ${lng.coord6()}"
}

fun Double.coord6(): String = String.format(Locale.US, "%.6f", this)

/**
 * Conversions between WGS-84, GCJ-02 (Mars coordinates) and BD-09.
 * The app stores every position as WGS-84 and converts only for display / import.
 */
object CoordConverter {

    private const val PI = Math.PI
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val X_PI = PI * 3000.0 / 180.0

    fun convert(point: LatLng, from: CoordSystem, to: CoordSystem): LatLng {
        if (from == to || !point.isValid) return point
        val wgs = when (from) {
            CoordSystem.WGS84 -> point
            CoordSystem.GCJ02 -> gcjToWgs(point)
            CoordSystem.BD09 -> gcjToWgs(bdToGcj(point))
        }
        return when (to) {
            CoordSystem.WGS84 -> wgs
            CoordSystem.GCJ02 -> wgsToGcj(wgs)
            CoordSystem.BD09 -> gcjToBd(wgsToGcj(wgs))
        }
    }

    private fun wgsToGcj(point: LatLng): LatLng {
        if (outOfChina(point)) return point
        val dLat = transformLat(point.lng - 105.0, point.lat - 35.0)
        val dLng = transformLng(point.lng - 105.0, point.lat - 35.0)
        val radLat = point.lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        val lat = point.lat + (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        val lng = point.lng + (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return LatLng(lat, lng)
    }

    private fun gcjToWgs(point: LatLng): LatLng {
        if (outOfChina(point)) return point
        var lat = point.lat
        var lng = point.lng
        repeat(4) {
            val projected = wgsToGcj(LatLng(lat, lng))
            lat -= projected.lat - point.lat
            lng -= projected.lng - point.lng
        }
        return LatLng(lat, lng)
    }

    private fun gcjToBd(point: LatLng): LatLng {
        val z = sqrt(point.lng * point.lng + point.lat * point.lat) +
            0.00002 * sin(point.lat * X_PI)
        val theta = atan2(point.lat, point.lng) + 0.000003 * cos(point.lng * X_PI)
        return LatLng(z * sin(theta) + 0.006, z * cos(theta) + 0.0065)
    }

    private fun bdToGcj(point: LatLng): LatLng {
        val x = point.lng - 0.0065
        val y = point.lat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        return LatLng(z * sin(theta), z * cos(theta))
    }

    private fun atan2(y: Double, x: Double): Double = Math.atan2(y, x)

    private fun outOfChina(point: LatLng): Boolean =
        point.lng < 72.004 || point.lng > 137.8347 || point.lat < 0.8293 || point.lat > 55.8271

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
