package id.web.izs.nettools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.izs.nettools.model.UiLayout

private fun sectionTitle(id: String): String = when (id) {
    "topbar" -> "Top bar"
    "target" -> "Target box"
    "tools" -> "Tools"
    "terminal" -> "Terminal"
    else -> id
}

/**
 * Edit UI: every home section is a card, stacked in its live home order.
 * Top bar arrows jump edge-to-edge (it only has two positions); the other
 * sections move one position at a time. Per-section settings live inside
 * the card: tool description, tool extra, grid rows, terminal toolbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditUiScreen(vm: NetToolsViewModel, onBack: () -> Unit, onView: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state.settings

    // Effective home order: top bar first or last, middle sections between.
    val order = if (s.topBarBottom) s.uiSections + "topbar"
                else listOf("topbar") + s.uiSections

    fun jump(id: String, up: Boolean) {
        if (id == "topbar") {
            vm.setTopBarBottom(!up)
            return
        }
        // Others swap with their neighbour — one step per tap.
        val list = s.uiSections.toMutableList()
        val i = list.indexOf(id)
        val t = if (up) i - 1 else i + 1
        if (i in list.indices && t in list.indices) {
            list.removeAt(i)
            list.add(t, id)
            vm.setUiSections(list)
        }
    }

    // Disabled when the move would do nothing (already at that edge).
    fun canJump(id: String, up: Boolean): Boolean = when (id) {
        "topbar" -> if (up) s.topBarBottom else !s.topBarBottom
        else -> {
            val i = s.uiSections.indexOf(id)
            if (up) i > 0 else i in 0 until s.uiSections.lastIndex
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit UI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Cards follow the home order. Top bar arrows jump to the edge; other sections move one step per tap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            order.forEach { id ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    sectionTitle(id),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (id == "topbar") {
                                    Text(
                                        if (s.topBarBottom) "Bottom edge" else "Top edge",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { jump(id, up = true) },
                                enabled = canJump(id, up = true)
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = if (id == "topbar")
                                        "Move ${sectionTitle(id)} to top"
                                    else "Move ${sectionTitle(id)} up"
                                )
                            }
                            IconButton(
                                onClick = { jump(id, up = false) },
                                enabled = canJump(id, up = false)
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (id == "topbar")
                                        "Move ${sectionTitle(id)} to bottom"
                                    else "Move ${sectionTitle(id)} down"
                                )
                            }
                        }

                        if (id == "tools") {
                            settingRow("Tool description") {
                                listOf("top" to "Top", "bottom" to "Bottom", "hide" to "Hide")
                                    .forEach { (value, label) ->
                                        FilterChip(
                                            selected = s.toolDescPos == value,
                                            onClick = { vm.setToolDescPos(value) },
                                            label = { Text(label) }
                                        )
                                    }
                            }
                            Text(
                                "Hint text, e.g. \"DNS lookup - pick record type - hold Dig to change server\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            settingRow("Tool extra") {
                                listOf("top" to "Top", "bottom" to "Bottom")
                                    .forEach { (value, label) ->
                                        FilterChip(
                                            selected = s.toolExtraPos == value,
                                            onClick = { vm.setToolExtraPos(value) },
                                            label = { Text(label) }
                                        )
                                    }
                            }
                            Text(
                                "WiFi tabs (Band/Channel/Security/Display), DIG record types - stays next to the tool row",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            settingRow("Tool extra header") {
                                listOf("top" to "Top", "bottom" to "Bottom")
                                    .forEach { (value, label) ->
                                        FilterChip(
                                            selected = s.toolExtraHeader == value,
                                            onClick = { vm.setToolExtraHeader(value) },
                                            label = { Text(label) }
                                        )
                                    }
                            }
                            Text(
                                "Inside the card: DIG \"Record type / DNS server\" row, WiFi Band/Channel tabs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            settingRow("Grid rows") {
                                FilterChip(
                                    selected = s.toolGridRows == 1,
                                    onClick = { vm.setToolGridRows(1) },
                                    label = { Text("1") }
                                )
                                FilterChip(
                                    selected = s.toolGridRows == 2,
                                    onClick = { vm.setToolGridRows(2) },
                                    label = { Text("2") }
                                )
                            }
                            Text(
                                "1 = one row, swipe for the next 5 tools · 2 = classic two-row split",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (id == "terminal") {
                            settingRow("Toolbar (Run · font · Clear)") {
                                FilterChip(
                                    selected = s.runRowTop,
                                    onClick = { vm.setRunRowTop(true) },
                                    label = { Text("Top") }
                                )
                                FilterChip(
                                    selected = !s.runRowTop,
                                    onClick = { vm.setRunRowTop(false) },
                                    label = { Text("Bottom") }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        vm.setUiSections(listOf("terminal", "tools", "target"))
                        vm.setTopBarBottom(true)
                        vm.setRunRowTop(false)
                        vm.setToolGridRows(2)
                        vm.setToolDescPos("top")
                        vm.setToolExtraPos("top")
                        vm.setToolExtraHeader("bottom")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Default ↓", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        vm.setEditUiFab(true)
                        onView()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("View")
                }
                OutlinedButton(
                    onClick = {
                        vm.setUiSections(UiLayout.SECTIONS)
                        vm.setTopBarBottom(false)
                        vm.setRunRowTop(true)
                        vm.setToolGridRows(2)
                        vm.setToolDescPos("bottom")
                        vm.setToolExtraPos("bottom")
                        vm.setToolExtraHeader("top")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Default")
                }
            }
        }
    }
}

@Composable
private fun settingRow(label: String, chips: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        chips()
    }
}
