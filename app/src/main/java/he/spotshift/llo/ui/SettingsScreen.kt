package he.spotshift.llo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import he.spotshift.llo.data.ThemeMode
import he.spotshift.llo.data.ThemeSettings
import kotlin.math.roundToInt

private val SeedPresets = listOf(
    0xFF2563EB, 0xFF0EA5E9, 0xFF14B8A6, 0xFF10B981, 0xFF84CC16,
    0xFFF59E0B, 0xFFEF4444, 0xFFEC4899, 0xFF8B5CF6, 0xFF64748B,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    theme: ThemeSettings,
    onThemeChange: (ThemeSettings) -> Unit,
    calibrationSeconds: Int,
    onCalibrationChange: (Int) -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit,
) {
    var showCustomColor by remember { mutableStateOf(false) }
    var sliderValue by remember(calibrationSeconds) {
        mutableFloatStateOf(calibrationSeconds.toFloat())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column {
                            Text("外观模式", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "可选择浅色、深色，或跟随系统设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = theme.mode == mode,
                                    onClick = { onThemeChange(theme.copy(mode = mode)) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size,
                                    ),
                                    label = { Text(mode.label) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("种子色", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "以此颜色生成主色、容器色与状态色",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SeedPresets.forEach { argb ->
                                SeedDot(
                                    color = Color(argb),
                                    selected = theme.seedArgb == argb,
                                    onClick = {
                                        onThemeChange(theme.copy(seedArgb = argb))
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { showCustomColor = true },
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) { Text("自定义颜色") }
                    }
                }
            }

            item {
                Text("定位", style = MaterialTheme.typography.titleSmall)
            }
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("定位校准间隔", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "每隔多久重新上报一次模拟位置；间隔越短越稳定，耗电略高",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "每 ${sliderValue.roundToInt()} 秒校准一次",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = {
                                onCalibrationChange(sliderValue.roundToInt())
                            },
                            valueRange = 1f..30f,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "1 秒",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "30 秒",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Text("关于", style = MaterialTheme.typography.titleSmall)
            }
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAbout),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpotShiftMark(size = 48.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SpotShift", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "作者 he0llo · 版本与开源信息",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showCustomColor) {
        var hex by remember { mutableStateOf("#2563EB") }
        val parsed = parseHexColor(hex)
        AlertDialog(
            onDismissRequest = { showCustomColor = false },
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            title = { Text("自定义种子色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { hex = it.trim() },
                        label = { Text("十六进制颜色") },
                        placeholder = { Text("#2563EB") },
                        singleLine = true,
                        isError = hex.isNotBlank() && parsed == null,
                    )
                    Text(
                        "支持 #RRGGBB 或 #AARRGGBB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = parsed != null,
                    onClick = {
                        parsed?.let {
                            onThemeChange(theme.copy(seedArgb = it))
                        }
                        showCustomColor = false
                    },
                ) { Text("应用") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomColor = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SeedDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (color.luminance() > 0.55f) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun parseHexColor(input: String): Int? {
    val cleaned = input.trim().removePrefix("#")
    if (cleaned.length != 6 && cleaned.length != 8) return null
    val value = cleaned.toLongOrNull(16) ?: return null
    return if (cleaned.length == 6) {
        (0xFF000000L or value).toInt()
    } else {
        value.toInt()
    }
}
