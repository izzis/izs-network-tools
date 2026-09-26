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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.izs.nettools.R
import id.web.izs.nettools.model.UiLayout

private fun sectionTitleRes(id: String): Int? = when (id) {
    "topbar" -> R.string.editui_section_topbar
    "target" -> R.string.editui_section_target
    "tools" -> R.string.editui_section_tools
    "terminal" -> R.string.editui_section_terminal
    else -> null
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
                title = { Text(stringResource(R.string.editui_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                stringResource(R.string.editui_order_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            order.forEach { id ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val titleRes = sectionTitleRes(id)
                        val sectionLabel = if (titleRes != null) stringResource(titleRes) else id
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    sectionLabel,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (id == "topbar") {
                                    Text(
                                        if (s.topBarBottom) stringResource(R.string.editui_edge_bottom)
                                        else stringResource(R.string.editui_edge_top),
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
                                        stringResource(R.string.editui_move_to_top, sectionLabel)
                                    else stringResource(R.string.editui_move_up, sectionLabel)
                                )
                            }
                            IconButton(
                                onClick = { jump(id, up = false) },
                                enabled = canJump(id, up = false)
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (id == "topbar")
                                        stringResource(R.string.editui_move_to_bottom, sectionLabel)
                                    else stringResource(R.string.editui_move_down, sectionLabel)
                                )
                            }
                        }

                        if (id == "tools") {
                            settingRow(R.string.editui_tool_desc) {
                                listOf(
                                    "top" to R.string.top,
                                    "bottom" to R.string.bottom,
                                    "hide" to R.string.hide
                                ).forEach { (value, labelRes) ->
                                    FilterChip(
                                        selected = s.toolDescPos == value,
                                        onClick = { vm.setToolDescPos(value) },
                                        label = { Text(stringResource(labelRes)) }
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.editui_tool_desc_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            settingRow(R.string.editui_tool_extra) {
                                listOf(
                                    "top" to R.string.top,
                                    "bottom" to R.string.bottom
                                ).forEach { (value, labelRes) ->
                                    FilterChip(
                                        selected = s.toolExtraPos == value,
                                        onClick = { vm.setToolExtraPos(value) },
                                        label = { Text(stringResource(labelRes)) }
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.editui_tool_extra_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            settingRow(R.string.editui_tool_extra_header) {
                                listOf(
                                    "top" to R.string.top,
                                    "bottom" to R.string.bottom
                                ).forEach { (value, labelRes) ->
                                    FilterChip(
                                        selected = s.toolExtraHeader == value,
                                        onClick = { vm.setToolExtraHeader(value) },
                                        label = { Text(stringResource(labelRes)) }
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.editui_tool_extra_header_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            settingRow(R.string.editui_grid_rows) {
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
                                stringResource(R.string.editui_grid_rows_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (id == "terminal") {
                            settingRow(R.string.editui_toolbar_label) {
                                FilterChip(
                                    selected = s.runRowTop,
                                    onClick = { vm.setRunRowTop(true) },
                                    label = { Text(stringResource(R.string.top)) }
                                )
                                FilterChip(
                                    selected = !s.runRowTop,
                                    onClick = { vm.setRunRowTop(false) },
                                    label = { Text(stringResource(R.string.bottom)) }
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
                    Text(stringResource(R.string.editui_default_down), maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        vm.setEditUiFab(true)
                        onView()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.view))
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
                    Text(stringResource(R.string.defaults))
                }
            }
        }
    }
}

@Composable
private fun settingRow(labelRes: Int, chips: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        chips()
    }
}
