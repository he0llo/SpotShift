package he.spotshift.llo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import he.spotshift.llo.mock.MockLocationService
import he.spotshift.llo.mock.MockState
import kotlinx.coroutines.launch

private object Route {
    const val Home = "home"
    const val Manage = "manage"
    const val Editor = "editor"
    const val Import = "import"
    const val Settings = "settings"
    const val About = "about"

    fun depth(route: String): Int = when (route) {
        Home -> 0
        Editor, About -> 2
        else -> 1
    }
}

@Composable
fun SpotShiftApp(
    viewModel: AppViewModel,
    incomingShare: String?,
    onShareConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val spots by viewModel.spots.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val calibrationSeconds by viewModel.calibrationSeconds.collectAsStateWithLifecycle()
    val session by MockState.session.collectAsStateWithLifecycle()
    val mockError by MockState.error.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }

    var route by rememberSaveable { mutableStateOf(Route.Home) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorStartWithMap by rememberSaveable { mutableStateOf(false) }
    var importSeed by rememberSaveable { mutableStateOf<String?>(null) }
    var importBackTo by rememberSaveable { mutableStateOf(Route.Home) }

    LaunchedEffect(incomingShare) {
        if (incomingShare != null) {
            importSeed = incomingShare
            importBackTo = Route.Home
            route = Route.Import
            onShareConsumed()
        }
    }

    LaunchedEffect(mockError) {
        mockError?.let {
            snackbar.showSnackbar(it)
            MockState.clearError()
        }
    }

    BackHandler(enabled = route != Route.Home) {
        route = when (route) {
            Route.Editor -> Route.Manage
            Route.Import -> importBackTo
            Route.About -> Route.Settings
            Route.Settings, Route.Manage -> Route.Home
            else -> Route.Home
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val forward = Route.depth(targetState) > Route.depth(initialState)
                val slideSpec = tween<IntOffset>(durationMillis = 300, easing = FastOutSlowInEasing)
                val fadeSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
                if (forward) {
                    (slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec)) togetherWith
                        (slideOutHorizontally(slideSpec) { -it / 4 } + fadeOut(fadeSpec))
                } else {
                    (slideInHorizontally(slideSpec) { -it / 4 } + fadeIn(fadeSpec)) togetherWith
                        (slideOutHorizontally(slideSpec) { it } + fadeOut(fadeSpec))
                }
            },
            label = "screen",
        ) { current ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                when (current) {
                    Route.Home -> HomeScreen(
                    spots = spots,
                    selectedId = selectedId,
                    session = session,
                    onSelect = viewModel::selectSpot,
                    onAdd = {
                        editorId = null
                        editorStartWithMap = false
                        route = Route.Editor
                    },
                    onManage = { route = Route.Manage },
                    onImport = {
                        importSeed = ""
                        importBackTo = Route.Home
                        route = Route.Import
                    },
                    onSettings = { route = Route.Settings },
                    onTeleport = { spot ->
                        runCatching {
                            MockLocationService.start(context, spot, calibrationSeconds)
                        }
                            .onFailure { notify(it.message ?: "无法启动模拟定位") }
                    },
                    onStop = { MockLocationService.stop(context) },
                    onMessage = notify,
                )

                Route.Manage -> ManageScreen(
                    spots = spots,
                    selectedId = selectedId,
                    onBack = { route = Route.Home },
                    onAdd = {
                        editorId = null
                        editorStartWithMap = false
                        route = Route.Editor
                    },
                    onMapPick = {
                        editorId = null
                        editorStartWithMap = true
                        route = Route.Editor
                    },
                    onEdit = { spot ->
                        editorId = spot.id
                        editorStartWithMap = false
                        route = Route.Editor
                    },
                    onDelete = { viewModel.removeSpot(it.id) },
                    onSelect = viewModel::selectSpot,
                    onImport = {
                        importSeed = ""
                        importBackTo = Route.Manage
                        route = Route.Import
                    },
                )

                Route.Editor -> EditorScreen(
                    spot = spots.firstOrNull { it.id == editorId },
                    startWithMap = editorStartWithMap,
                    onBack = { route = Route.Manage },
                    onSave = { spot ->
                        if (spots.any { it.id == spot.id }) {
                            viewModel.updateSpot(spot)
                        } else {
                            viewModel.addSpot(spot)
                        }
                        route = Route.Manage
                    },
                )

                Route.Import -> key(importSeed) {
                    ImportScreen(
                        initialText = importSeed.orEmpty(),
                        onBack = { route = importBackTo },
                        onImported = { imported ->
                            viewModel.addSpots(imported)
                            route = Route.Home
                            notify("已导入 ${imported.size} 个传送点")
                        },
                    )
                }

                Route.Settings -> SettingsScreen(
                    theme = theme,
                    onThemeChange = viewModel::setTheme,
                    calibrationSeconds = calibrationSeconds,
                    onCalibrationChange = viewModel::setCalibrationSeconds,
                    onAbout = { route = Route.About },
                    onBack = { route = Route.Home },
                )

                Route.About -> AboutScreen(
                    onBack = { route = Route.Settings },
                )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp),
        )
    }
}
