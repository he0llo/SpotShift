package he.spotshift.llo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import he.spotshift.llo.data.Spot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(
    spots: List<Spot>,
    selectedId: String?,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onMapPick: () -> Unit,
    onEdit: (Spot) -> Unit,
    onDelete: (Spot) -> Unit,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Spot?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("传送点管理") },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onMapPick,
                icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                text = { Text("地图选点") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ImportEntryCard(onClick = onImport)
            }

            item {
                SectionTitle(
                    title = "全部传送点",
                    trailing = if (spots.isEmpty()) null else "${spots.size} 个",
                )
            }

            if (spots.isEmpty()) {
                item { EmptySpots(onAdd = onAdd, onImport = onImport) }
            } else {
                items(spots, key = { it.id }) { spot ->
                    ManageSpotRow(
                        spot = spot,
                        selected = spot.id == selectedId,
                        onSelect = { onSelect(spot.id) },
                        onEdit = { onEdit(spot) },
                        onDelete = { pendingDelete = spot },
                    )
                }
                item {
                    Text(
                        text = "点按「设为当前」切换传送目标；编辑时可修改坐标与坐标系。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    pendingDelete?.let { spot ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${spot.name}」？") },
            text = { Text("该传送点会从列表中移除，正在进行的模拟定位不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(spot)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}
