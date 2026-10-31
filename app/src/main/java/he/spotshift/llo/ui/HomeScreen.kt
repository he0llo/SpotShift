package he.spotshift.llo.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import he.spotshift.llo.data.LatLng
import he.spotshift.llo.data.Spot
import he.spotshift.llo.data.coord6
import he.spotshift.llo.map.AmapMap
import he.spotshift.llo.mock.MockLocationPermission
import he.spotshift.llo.mock.MockSession

private val FallbackCenter = LatLng(39.9042, 116.4074)

@Composable
fun HomeScreen(
    spots: List<Spot>,
    selectedId: String?,
    session: MockSession?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onTeleport: (Spot) -> Unit,
    onStop: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val selected = spots.firstOrNull { it.id == selectedId }
    var mockGranted by remember { mutableStateOf(MockLocationPermission.isGranted(context)) }
    var showGuide by remember { mutableStateOf(false) }
    var pendingSpot by remember { mutableStateOf<Spot?>(null) }
    var wifiPromptSpot by remember { mutableStateOf<Spot?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        mockGranted = MockLocationPermission.isGranted(context)
    }

    val startMock: (Spot) -> Unit = { spot ->
        if (MockLocationPermission.isGranted(context)) {
            onTeleport(spot)
        } else {
            showGuide = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val spot = pendingSpot
        pendingSpot = null
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        when {
            spot == null -> Unit
            locationGranted -> {
                mockGranted = true
                startMock(spot)
            }
            else -> onMessage("未授予定位权限，无法启动模拟定位")
        }
    }

    val startWithPermissions: (Spot) -> Unit = { spot ->
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            startMock(spot)
        } else {
            pendingSpot = spot
            permissionLauncher.launch(
                buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray()
            )
        }
    }

    val teleport: (Spot) -> Unit = { spot ->
        if (wifiConnected(context)) {
            wifiPromptSpot = spot
        } else {
            startWithPermissions(spot)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            TeleportBar(
                selected = selected,
                session = session,
                mockGranted = mockGranted,
                onStart = { selected?.let(teleport) },
                onStop = onStop,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HomeHeader(
                onSettings = onSettings,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MapPreviewCard(selected = selected, session = session)
                }

                item {
                    SectionTitle(
                        title = "快捷传送点",
                        trailing = if (spots.isEmpty()) null else "${spots.size} 个 · 管理",
                        onTrailingClick = if (spots.isEmpty()) null else onManage,
                    )
                }

                if (spots.isEmpty()) {
                    item {
                        EmptySpots(onAdd = onAdd, onImport = onImport)
                    }
                } else {
                    items(spots, key = { it.id }) { spot ->
                        SpotRow(
                            spot = spot,
                            selected = spot.id == selectedId,
                            active = spot.id == session?.spotId,
                            onClick = { onSelect(spot.id) },
                        )
                    }
                }

                item {
                    Text(
                        text = "模拟位置会持续生效，直到手动停止。请仅用于开发与测试。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }
    }

    if (showGuide) {
        MockPermissionDialog(
            onDismiss = { showGuide = false },
            onOpenDeveloperOptions = {
                showGuide = false
                MockLocationPermission.openDeveloperOptions(context)
            },
        )
    }

    if (wifiPromptSpot != null) {
        AlertDialog(
            onDismissRequest = { wifiPromptSpot = null },
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            title = { Text("当前已连接 Wi-Fi") },
            text = {
                Text("系统网络定位可能覆盖模拟位置，建议先关闭 Wi-Fi 再开启模拟。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val spot = wifiPromptSpot
                        wifiPromptSpot = null
                        spot?.let(startWithPermissions)
                    },
                ) { Text("强制开启模拟") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { wifiPromptSpot = null }) { Text("取消") }
                    TextButton(
                        onClick = {
                            wifiPromptSpot = null
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_WIFI_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure { onMessage("无法打开 Wi-Fi 设置") }
                        },
                    ) { Text("去关闭 Wi-Fi") }
                }
            },
        )
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SpotShift",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "选一个地点，一键传送",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "设置")
        }
    }
}

@Composable
private fun MapPreviewCard(selected: Spot?, session: MockSession?) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(208.dp),
            ) {
                AmapMap(
                    modifier = Modifier.fillMaxSize(),
                    center = selected?.position ?: FallbackCenter,
                    zoom = if (selected == null) 11f else 15f,
                    marker = selected?.position,
                    resetKey = selected?.id,
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (session != null) {
                        MapBadge(
                            text = "正在模拟",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            dot = true,
                        )
                    } else {
                        MapBadge(
                            text = if (selected == null) "预览" else "当前选择",
                            container = MaterialTheme.colorScheme.surface,
                            content = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = selected?.name?.take(1) ?: "地",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected?.name ?: "还没有选中的位置",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = selected?.let {
                            "${it.positionIn(it.source).format()} · ${it.source.shortLabel}"
                        } ?: "添加传送点后，这里会显示当前位置",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (selected == null) null else FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (session != null) {
                    ActiveDot()
                }
            }
        }
    }
}

@Composable
private fun MapBadge(
    text: String,
    container: Color,
    content: Color,
    dot: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = container.copy(alpha = 0.94f),
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(content, CircleShape),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TeleportBar(
    selected: Spot?,
    session: MockSession?,
    mockGranted: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            when {
                session != null -> {
                    Button(
                        onClick = onStop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("停止模拟")
                    }
                }

                selected != null -> {
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("传送到 ${selected.name}", maxLines = 1)
                    }
                }

                else -> {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text("先添加一个传送点")
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    session != null -> "正在模拟「${session.name}」· ${session.lat.coord6()}, ${session.lng.coord6()}"
                    !mockGranted -> "需要先在开发者选项中把 SpotShift 设为模拟位置应用"
                    selected != null -> "开始后会持续模拟，直到手动停止"
                    else -> "点击「管理」添加或导入位置"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (!mockGranted && session == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun wifiConnected(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    return manager.allNetworks.any { network ->
        manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}

@Composable
private fun MockPermissionDialog(
    onDismiss: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
        title = { Text("先选择模拟位置应用") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Android 要求先在“开发者选项”中指定一个模拟位置信息应用，SpotShift 才能改写系统定位。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        "设置 → 开发者选项 → 选择模拟位置信息应用 → SpotShift",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Text(
                    "如果找不到开发者选项，请先在“关于手机”中连续点击版本号 7 次。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenDeveloperOptions) { Text("打开开发者选项") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        },
    )
}
