package he.spotshift.llo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import he.spotshift.llo.data.Spot
import he.spotshift.llo.data.SpotRepository
import he.spotshift.llo.data.ThemeSettings
import he.spotshift.llo.mock.MockLocationService
import he.spotshift.llo.mock.MockState

/**
 * Holds the repository for the activity lifetime so rotation and process death
 * never lose the selected spot or the theme.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpotRepository(application)

    val spots = repository.spots
    val selectedId = repository.selectedId
    val theme = repository.theme

    val calibrationSeconds = repository.calibrationSeconds

    fun addSpot(spot: Spot) = repository.add(spot)

    fun addSpots(spots: List<Spot>) = repository.addAll(spots)

    fun updateSpot(spot: Spot) = repository.update(spot)

    fun removeSpot(id: String) = repository.remove(id)

    fun selectSpot(id: String) = repository.select(id)

    fun setTheme(settings: ThemeSettings) = repository.setTheme(settings)

    fun setCalibrationSeconds(seconds: Int) {
        repository.setCalibrationSeconds(seconds)
        if (MockState.session.value != null) {
            MockLocationService.updateInterval(getApplication(), seconds)
        }
    }
}
