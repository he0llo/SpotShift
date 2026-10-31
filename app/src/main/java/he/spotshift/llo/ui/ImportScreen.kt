package he.spotshift.llo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import he.spotshift.llo.data.CoordConverter
import he.spotshift.llo.data.CoordSystem
import he.spotshift.llo.data.LatLng
import he.spotshift.llo.data.Spot
import he.spotshift.llo.data.coord6
import he.spotshift.llo.imports.ImportDraft
import he.spotshift.llo.imports.ImportFailure
import he.spotshift.llo.imports.ShortLinkResolver
import he.spotshift.llo.imports.SpotImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DraftRow(
    val index: Int,
    val name: String,
    val source: CoordSystem,
    val rawPoint: LatLng,
    val note: String?,
    val included: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    initialText: String,
    onBack: () -> Unit,
    onImported: (List<Spot>) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var rows by remember { mutableStateOf<List<DraftRow>>(emptyList()) }
    var failures by remember { mutableStateOf<List<ImportFailure>>(emptyList()) }
    var parsing by remember { mutableStateOf(false) }
    var parsedOnce by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    fun parse(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || parsing) return
        scope.launch {
            parsing = true
            val expanded = ShortLinkResolver.expand(trimmed)
            val report = withContext(Dispatchers.Default) { SpotImport.parse(expanded.text) }
            rows = report.drafts.mapIndexed { index, draft -> draft.toRow(index) }
            failures = report.failures + expanded.failures
            parsing = false
        }
    }

    LaunchedEffect(Unit) {
        if (!parsedOnce) {
            parsedOnce = true
            if (text.isNotBlank()) parse(text)
        }
    }

    val selectedCount = rows.count { it.included }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("导入地点") },
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
        bottomBar = {
            if (rows.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                    Button(
                        onClick = {
                            val spots = rows
                                .filter { it.included }
                                .map { row ->
                                    Spot.fromInput(
                                        name = row.name.trim().ifBlank { "导入地点" },
                                        point = row.rawPoint,
                                        system = row.source,
                                    )
                                }
                            if (spots.isNotEmpty()) onImported(spots)
                        },
                        enabled = selectedCount > 0,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .height(54.dp),
                    ) {
                        Text("导入 $selectedCount 个地点")
                    }
                }
            }
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
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "支持高德 / 百度地图的分享文本、amapuri://、baidumap:// 与网页链接。" +
                                "高德坐标（GCJ-02）和百度坐标（BD-09）会自动转换为 WGS-84。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 132.dp),
                    label = { Text("分享文本或链接") },
                    placeholder = {
                        Text("例如：我在高德地图发现了一个好地方 https://surl.amap.com/xxxx\n或 amapuri://route/plan/?dlat=39.9&dlon=116.4&dname=公司")
                    },
                    minLines = 4,
                    maxLines = 8,
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { parse(text) },
                        enabled = text.isNotBlank() && !parsing,
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(if (parsing) "识别中…" else "识别") }

                    OutlinedButton(
                        onClick = {
                            val pasted = clipboard.getText()?.text.orEmpty()
                            if (pasted.isNotBlank()) {
                                text = pasted
                                parse(pasted)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("粘贴") }

                    if (text.isNotBlank()) {
                        TextButton(onClick = {
                            text = ""
                            rows = emptyList()
                            failures = emptyList()
                        }) { Text("清空") }
                    }
                }
            }

            if (parsing) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            if (rows.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = rows.all { it.included },
                            onCheckedChange = { checked ->
                                rows = rows.map { it.copy(included = checked) }
                            },
                        )
                        Text(
                            text = "全选 · 已识别 ${rows.size} 个",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "已选 $selectedCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(rows, key = { it.index }) { row ->
                    DraftEditorCard(
                        row = row,
                        onNameChange = { newName ->
                            rows = rows.map { if (it.index == row.index) it.copy(name = newName) else it }
                        },
                        onIncludedChange = { included ->
                            rows = rows.map { if (it.index == row.index) it.copy(included = included) else it }
                        },
                        onSourceChange = { next ->
                            rows = rows.map { if (it.index == row.index) it.copy(source = next) else it }
                        },
                        onRemove = { rows = rows.filterNot { it.index == row.index } },
                    )
                }
            }

            if (failures.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "未能识别",
                        trailing = "${failures.size} 条",
                    )
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            failures.forEachIndexed { index, failure ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.15f),
                                    )
                                }
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(
                                        text = failure.raw.ifBlank { "（空内容）" },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = failure.reason,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!parsing && rows.isEmpty() && failures.isEmpty() && text.isNotBlank()) {
                item {
                    Text(
                        text = "点击「识别」查看解析结果。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun ImportDraft.toRow(index: Int): DraftRow = DraftRow(
    index = index,
    name = name,
    source = source,
    rawPoint = rawPoint,
    note = note,
)

@Composable
private fun DraftEditorCard(
    row: DraftRow,
    onNameChange: (String) -> Unit,
    onIncludedChange: (Boolean) -> Unit,
    onSourceChange: (CoordSystem) -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val wgs = CoordConverter.convert(row.rawPoint, row.source, CoordSystem.WGS84)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = row.included,
                    onCheckedChange = onIncludedChange,
                )
                OutlinedTextField(
                    value = row.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("名称") },
                    singleLine = true,
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "移除")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    AssistChip(
                        onClick = { menuOpen = true },
                        label = { Text(row.source.shortLabel) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.height(18.dp),
                            )
                        },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        CoordSystem.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.label) },
                                onClick = {
                                    menuOpen = false
                                    onSourceChange(entry)
                                },
                                trailingIcon = {
                                    if (entry == row.source) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                            )
                        }
                    }
                }
                Text(
                    text = "原始 ${row.rawPoint.format()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "保存为 WGS-84",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${wgs.lat.coord6()}, ${wgs.lng.coord6()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
            }

            row.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
