package he.spotshift.llo.data

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class Spot(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lat: Double,
    val lng: Double,
    /** The coordinate system this spot was entered/imported with. Stored coordinates are WGS-84. */
    val source: CoordSystem = CoordSystem.WGS84,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val position: LatLng get() = LatLng(lat, lng)

    fun positionIn(system: CoordSystem): LatLng =
        CoordConverter.convert(position, CoordSystem.WGS84, system)

    companion object {
        fun fromInput(
            name: String,
            point: LatLng,
            system: CoordSystem,
            id: String = UUID.randomUUID().toString(),
        ): Spot {
            val wgs = CoordConverter.convert(point, system, CoordSystem.WGS84)
            return Spot(id = id, name = name, lat = wgs.lat, lng = wgs.lng, source = system)
        }
    }
}

@Immutable
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

@Immutable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val seedArgb: Int = DEFAULT_SEED,
) {
    companion object {
        const val DEFAULT_SEED: Int = 0xFF2563EB.toInt()
    }
}
