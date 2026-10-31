package he.spotshift.llo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import he.spotshift.llo.R
import he.spotshift.llo.data.CoordConverter
import he.spotshift.llo.data.CoordSystem
import he.spotshift.llo.data.LatLng
import he.spotshift.llo.data.Spot
import he.spotshift.llo.data.coord6
import he.spotshift.llo.map.AmapMap
import java.util.UUID

private val EditorFallbackCenter = LatLng(39.9042, 116.4074)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    spot: Spot?,
    startWithMap: Boolean = false,
    onBack: () -> Unit,
    onSave: (Spot) -> Unit,
) {
    val initialSystem = spot?.source ?: CoordSystem.WGS84
    val initialPoint = spot?.positionIn(initialSystem) ?: EditorFallbackCenter

    var name by rememberSaveable(spot?.id) { mutableStateOf(spot?.name.orEmpty()) }
    var systemName by rememberSaveable(spot?.id) { mutableStateOf(initialSystem.name) }
    var latText by rememberSaveable(spot?.id) { mutableStateOf(initialPoint.lat.coord6()) }
    var lngText by rememberSaveable(spot?.id) { mutableStateOf(initialPoint.lng.coord6()) }
    var showSearch by remember { mutableStateOf(false) }
    var showFullscreen by rememberSaveable { mutableStateOf(startWithMap) }
    var mapReset by remember { mutableIntStateOf(0) }

    val system = CoordSystem.fromName(systemName)
    val point: LatLng? = latText.toDoubleOrNull()?.let { lat ->
        lngText.toDoubleOrNull()?.let { lng -> LatLng(lat, lng) }
    }?.takeIf { it.isValid }
    val wgsPoint = point?.let { CoordConverter.convert(it, system, CoordSystem.WGS84) }
    val canSave = point != null && name.isNotBlank()

    fun switchSystem(next: CoordSystem) {
        if (next == system) return
        point?.let {
            val converted = CoordConverter.convert(it, system, next)
            latText = converted.lat.coord6()
            lngText = converted.lng.coord6()
        }
        systemName = next.name
    }

    fun applyPicked(wgs: LatLng, suggestedName: String? = null) {
        val converted = CoordConverter.convert(wgs, CoordSystem.WGS84, system)
        latText = converted.lat.coord6()
        lngText = converted.lng.coord6()
        if (name.isBlank() && !suggestedName.isNullOrBlank()) name = suggestedName
        mapReset++
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (spot == null) "新增传送点" else "编辑传送点") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            val target = point ?: return@TextButton
                            onSave(
                                Spot.fromInput(
                                    name = name.trim(),
                                    point = target,
                                    system = system,
                                    id = spot?.id ?: UUID.randomUUID().toString(),
                                )
                            )
                        },
                    ) { Text("保存") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                placeholder = { Text("例如：公司、家、常去的咖啡店") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it.trim() },
                    modifier = Modifier.weight(1f),
                    label = { Text("纬度") },
                    singleLine = true,
                    isError = latText.isNotBlank() && latText.toDoubleOrNull() == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it.trim() },
                    modifier = Modifier.weight(1f),
                    label = { Text("经度") },
                    singleLine = true,
                    isError = lngText.isNotBlank() && lngText.toDoubleOrNull() == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("坐标系", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CoordSystem.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = system == entry,
                            onClick = { switchSystem(entry) },
                            shape = SegmentedButtonDefaults.itemShape(index, CoordSystem.entries.size),
                            label = { Text(entry.shortLabel) },
                        )
                    }
                }
                Text(
                    text = "按所选坐标系解释填写的数值，保存时统一换算为 WGS-84。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CoordUsage(CoordSystem.WGS84, "GPS 设备、国际地图通用坐标", system)
                CoordUsage(CoordSystem.GCJ02, "高德、腾讯等国内地图", system)
                CoordUsage(CoordSystem.BD09, "百度地图专用坐标", system)
            }

            if (point == null) {
                Text(
                    text = "纬度需在 -90..90，经度需在 -180..180，且不能为空。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("地图选点", style = MaterialTheme.typography.labelLarge)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    ) {
                        AmapMap(
                            modifier = Modifier.fillMaxSize(),
                            center = wgsPoint ?: EditorFallbackCenter,
                            zoom = 15f,
                            marker = wgsPoint,
                            resetKey = "${spot?.id ?: "new"}-$mapReset",
                            onTap = { tapped ->
                                val converted = CoordConverter.convert(
                                    tapped,
                                    CoordSystem.WGS84,
                                    system,
                                )
                                latText = converted.lat.coord6()
                                lngText = converted.lng.coord6()
                            },
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalIconButton(
                                onClick = { showSearch = true },
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(50),
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = "搜索地点")
                            }
                            FilledTonalIconButton(
                                onClick = { showFullscreen = true },
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(50),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_fullscreen),
                                    contentDescription = "全屏选点",
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp),
                        ) {
                            Text(
                                text = "点按地图可直接取点",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            wgsPoint?.let {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "保存为 WGS-84",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = it.format(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSearch) {
        MapSearchDialog(
            initialQuery = name,
            onDismiss = { showSearch = false },
            onPick = { result ->
                applyPicked(result.point, result.name)
                showSearch = false
            },
        )
    }

    if (showFullscreen) {
        FullscreenMapDialog(
            center = wgsPoint ?: EditorFallbackCenter,
            marker = wgsPoint,
            onDismiss = { showFullscreen = false },
            onConfirm = { picked, pickedName ->
                applyPicked(picked, pickedName)
                showFullscreen = false
            },
        )
    }
}

@Composable
private fun CoordUsage(system: CoordSystem, usage: String, selected: CoordSystem) {
    val isSelected = system == selected
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = system.shortLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(60.dp),
        )
        Text(
            text = usage,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
