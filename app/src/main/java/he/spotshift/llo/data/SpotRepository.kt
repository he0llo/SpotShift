package he.spotshift.llo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small, dependency-free persistence layer backed by SharedPreferences.
 * Every position is stored in WGS-84; [Spot.source] only records where it came from.
 */
class SpotRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _spots = MutableStateFlow(readSpots())
    val spots: StateFlow<List<Spot>> = _spots.asStateFlow()

    private val _selectedId = MutableStateFlow(prefs.getString(KEY_SELECTED, null))
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val _theme = MutableStateFlow(readTheme())
    val theme: StateFlow<ThemeSettings> = _theme.asStateFlow()

    private val _calibrationSeconds = MutableStateFlow(
        prefs.getInt(KEY_CALIBRATION_SECONDS, DEFAULT_CALIBRATION_SECONDS)
            .coerceIn(MIN_CALIBRATION_SECONDS, MAX_CALIBRATION_SECONDS)
    )
    val calibrationSeconds: StateFlow<Int> = _calibrationSeconds.asStateFlow()

    init {
        normalizeSelection()
    }

    fun add(spot: Spot) {
        _spots.value = _spots.value + spot
        persistSpots()
        if (_selectedId.value == null) select(spot.id)
    }

    fun addAll(spots: List<Spot>) {
        if (spots.isEmpty()) return
        _spots.value = _spots.value + spots
        persistSpots()
        if (_selectedId.value == null) select(spots.first().id)
    }

    fun update(spot: Spot) {
        val current = _spots.value
        val index = current.indexOfFirst { it.id == spot.id }
        if (index < 0) return
        _spots.value = current.toMutableList().also { it[index] = spot }
        persistSpots()
    }

    fun remove(id: String) {
        val remaining = _spots.value.filterNot { it.id == id }
        if (remaining.size == _spots.value.size) return
        _spots.value = remaining
        persistSpots()
        if (_selectedId.value == id) {
            select(remaining.firstOrNull()?.id)
        }
    }

    fun select(id: String?) {
        if (id != null && _spots.value.none { it.id == id }) return
        _selectedId.value = id
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    fun setTheme(settings: ThemeSettings) {
        _theme.value = settings
        prefs.edit()
            .putString(KEY_THEME_MODE, settings.mode.name)
            .putInt(KEY_SEED_COLOR, settings.seedArgb)
            .apply()
    }

    fun setCalibrationSeconds(seconds: Int) {
        val value = seconds.coerceIn(MIN_CALIBRATION_SECONDS, MAX_CALIBRATION_SECONDS)
        _calibrationSeconds.value = value
        prefs.edit().putInt(KEY_CALIBRATION_SECONDS, value).apply()
    }

    private fun normalizeSelection() {
        val spots = _spots.value
        val selected = _selectedId.value
        if (selected != null && spots.none { it.id == selected }) {
            select(spots.firstOrNull()?.id)
        } else if (selected == null && spots.isNotEmpty()) {
            select(spots.first().id)
        }
    }

    private fun readSpots(): List<Spot> {
        val raw = prefs.getString(KEY_SPOTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val lat = item.optDouble("lat", Double.NaN)
                    val lng = item.optDouble("lng", Double.NaN)
                    if (lat.isNaN() || lng.isNaN()) continue
                    add(
                        Spot(
                            id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                            name = item.optString("name").ifBlank { "未命名位置" },
                            lat = lat,
                            lng = lng,
                            source = CoordSystem.fromName(item.optString("source")),
                            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistSpots() {
        val array = JSONArray()
        _spots.value.forEach { spot ->
            array.put(
                JSONObject()
                    .put("id", spot.id)
                    .put("name", spot.name)
                    .put("lat", spot.lat)
                    .put("lng", spot.lng)
                    .put("source", spot.source.name)
                    .put("createdAt", spot.createdAt)
            )
        }
        prefs.edit().putString(KEY_SPOTS, array.toString()).apply()
    }

    private fun readTheme(): ThemeSettings = ThemeSettings(
        mode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null).orEmpty())
        }.getOrDefault(ThemeMode.SYSTEM),
        seedArgb = prefs.getInt(KEY_SEED_COLOR, ThemeSettings.DEFAULT_SEED),
    )

    private companion object {
        const val PREFS = "spotshift_prefs"
        const val KEY_SPOTS = "spots"
        const val KEY_SELECTED = "selected_spot"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SEED_COLOR = "theme_seed"
        const val KEY_CALIBRATION_SECONDS = "calibration_seconds"
        const val DEFAULT_CALIBRATION_SECONDS = 1
        const val MIN_CALIBRATION_SECONDS = 1
        const val MAX_CALIBRATION_SECONDS = 30
    }
}
