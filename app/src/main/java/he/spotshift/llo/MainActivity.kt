package he.spotshift.llo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import he.spotshift.llo.data.ThemeMode
import he.spotshift.llo.ui.AppViewModel
import he.spotshift.llo.ui.SpotShiftApp
import he.spotshift.llo.ui.theme.SpotShiftTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private val incomingShare = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingShare.value = extractShare(intent)
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val darkTheme = when (theme.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SpotShiftTheme(
                seedColor = Color(theme.seedArgb),
                darkTheme = darkTheme,
            ) {
                SpotShiftApp(
                    viewModel = viewModel,
                    incomingShare = incomingShare.value,
                    onShareConsumed = { incomingShare.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractShare(intent)?.let { incomingShare.value = it }
    }

    /** Reads a shared location from ACTION_SEND text or an amap / baidu / geo URI. */
    private fun extractShare(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return null
                val scheme = uri.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    val host = uri.host?.lowercase() ?: return null
                    val isMapHost = host == "amap.com" || host.endsWith(".amap.com") ||
                        host == "map.baidu.com" || host.endsWith(".baidu.com")
                    if (isMapHost) uri.toString() else null
                } else {
                    uri.toString()
                }
            }
            else -> null
        }
    }
}
