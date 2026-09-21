package id.web.izs.nettools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.izs.nettools.core.DnsRunner
import id.web.izs.nettools.model.AppSettings
import id.web.izs.nettools.model.DnsPresets
import id.web.izs.nettools.model.IpInfoPresets
import id.web.izs.nettools.model.SavedSort
import id.web.izs.nettools.model.Tool
import id.web.izs.nettools.model.WhoisPresets
import id.web.izs.nettools.model.sortedFor

/** Console palette. Dark themes keep the classic dark terminal; light themes
 *  follow the theme surfaces with darker semantic colors for contrast. */
private data class TerminalPalette(
    val bg: Color,
    val text: Color,
    val dim: Color,
    val green: Color,
    val blue: Color,
    val red: Color,
    val amber: Color
)

private val DarkTerminal = TerminalPalette(
    bg = Color(0xFF0D1117),
    text = Color(0xFFC9D1D9),
    dim = Color(0xFF6E7681),
    green = Color(0xFF3FB950),
    blue = Color(0xFF79C0FF),
    red = Color(0xFFF85149),
    amber = Color(0xFFD29922)
)

private fun lightTerminal(surface: Color, onSurface: Color, dim: Color) = TerminalPalette(
    bg = surface,
    text = onSurface,
    dim = dim,
    green = Color(0xFF1A7F37),
    blue = Color(0xFF0A58CA),
    red = Color(0xFFCF222E),
    amber = Color(0xFF9A6700)
)

/** Semantic color for one output line. */
private fun terminalLineColor(line: String, p: TerminalPalette): Color {
    val t = line.trimStart()
    // Errors and failures always win.
    if (t.startsWith("ERROR") || t.contains("failed", ignoreCase = true)) return p.red
    // Headers / summaries / comments.
    if (t.startsWith("==") || t.startsWith("---") || t.startsWith("Final URL") ||
        t.startsWith("Done") || t.startsWith("rtt ")
    ) return p.blue
    if (t.startsWith(";;")) return p.dim
    // Success markers.
    if (t.startsWith("OPEN") || t.startsWith("UP  ") ||
        t.startsWith("Trusted: yes") || t.contains("expires in", ignoreCase = true) ||
        t.contains("Destination reached")
    ) return p.green
    // Warnings and bad states.
    if (t.startsWith("Trusted: NO") || t.contains("EXPIRED") ||
        t.contains("NOT YET VALID") || t.contains("NXDOMAIN", ignoreCase = true) ||
        t.contains("unknown host", ignoreCase = true) ||
        t.contains("Request timeout", ignoreCase = true)
    ) return p.red
    if (t.contains("packet loss")) {
        return when {
            t.contains("0% packet loss") -> p.green
            t.contains("100% packet loss") -> p.red
            else -> p.amber
        }
    }
    if (t.contains("bytes from")) return p.green
    if (t.contains("[same as hop")) return p.amber
    if (t.startsWith("Status:", ignoreCase = true) &&
        (t.contains("Hold", ignoreCase = true) ||
            t.contains("Delete", ignoreCase = true) ||
            t.contains("redemption", ignoreCase = true))
    ) return p.red
    if (t.startsWith("closed")) return p.dim
    // Silent trace hops recede.
    if (t.matches(Regex("[0-9]+\\s+\\*.*")) && !t.contains("ms")) return p.dim
    // HTTP status lines.
    val code = Regex("^([0-9]{3})\\s").find(t)?.groupValues?.get(1)
    if (code != null) {
        return when (code[0]) {
            '2' -> p.green
            '3' -> p.blue
            else -> p.red
        }
    }
    return p.text
}

private val kvPattern = Regex("^([A-Za-z][A-Za-z0-9 _.\\-/]{0,40}): (.*)$")

/** One output line: semantic color + dim key / bright value for "Key: value" lines. */
@Composable
private fun OutputLine(line: String, colored: Boolean, p: TerminalPalette, fontSize: TextUnit) {
    if (!colored) {
        Text(line, color = p.text, fontFamily = FontFamily.Monospace, fontSize = fontSize)
        return
    }
    val kv = if (!line.contains('\t')) kvPattern.find(line) else null
    if (kv != null && kv.groupValues[2].isNotEmpty() &&
        !line.trimStart().startsWith(";;") && !line.trimStart().startsWith("==")
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = p.dim)) { append(kv.groupValues[1] + ":") }
                append(" ")
                withStyle(SpanStyle(color = terminalLineColor(line, p))) { append(kv.groupValues[2]) }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize
        )
    } else {
        Text(
            line,
            color = terminalLineColor(line, p),
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    vm: NetToolsViewModel,
    onOpenSettings: () -> Unit,
    onOpenHosts: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    // Console follows the app theme: dark terminal on dark themes,
    // theme surfaces on light themes.
    val scheme = MaterialTheme.colorScheme
    val term = remember(state.settings.theme, scheme.surfaceContainer, state.settings.customColors) {
        val base = if (AppTheme.isDark(state.settings.theme)) DarkTerminal
            else lightTerminal(scheme.surfaceContainer, scheme.onSurface, scheme.onSurfaceVariant)
        // Custom terminal background: keep it readable by switching the text set
        // automatically (dark text on light bg, classic terminal on dark bg).
        val bgOverride = state.settings.customColors["terminal"]?.let { hexToColor(it) }
            ?: return@remember base
        if (bgOverride.luminance() > 0.5f) {
            lightTerminal(bgOverride, Color(0xFF24262C), Color(0xFF5A5E66))
        } else {
            base.copy(bg = bgOverride)
        }
    }
    val snack = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    // Autofilter for the Saved/Recent dropdown: snapshot the target text at the
    // moment the dropdown opens. Everything shows first; filtering kicks in
    // only once the user types something new above it.
    var dropFilterBase by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.dropExpanded) {
        if (state.dropExpanded) dropFilterBase = state.target
    }
    val dropQuery =
        if (state.dropExpanded && dropFilterBase != null && state.target != dropFilterBase) state.target.trim() else ""
    // Same order as Manage Hosts: the persisted sort applies here too.
    val savedBase = remember(state.saved, state.settings.savedSort) {
        state.saved.sortedFor(SavedSort.of(state.settings.savedSort))
    }
    val savedShown = (if (dropQuery.isEmpty()) savedBase
        else savedBase.filter { it.label.contains(dropQuery, true) || it.host.contains(dropQuery, true) }).take(50)
    val recentBase = if (state.settings.hideRecentDupes)
        state.recent.filter { r -> state.saved.none { it.host.equals(r, ignoreCase = true) } }
    else state.recent
    val recentShown = (if (dropQuery.isEmpty()) recentBase
        else recentBase.filter { it.contains(dropQuery, true) }).take(20)

    LaunchedEffect(state.message) {
        state.message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) listState.scrollToItem(state.lines.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("izs NetTools") },
                actions = {
                    IconButton(onClick = onOpenHosts) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Manage saved")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(12.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Target bar: single unified search bar ---
            OutlinedTextField(
                value = state.target,
                onValueChange = vm::setTarget,
                placeholder = { Text("Target (IP / host)") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.toggleSave() }) {
                            Icon(
                                if (state.isTargetSaved) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "Save target",
                                tint = if (state.isTargetSaved) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { vm.setDrop(!state.dropExpanded) }) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pick saved")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = {
                    focusManager.clearFocus()
                    vm.run()
                }),
                modifier = Modifier.fillMaxWidth()
            )

            // --- Saved + recent dropdown (compact rows) ---
            if (state.dropExpanded) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Text(
                            "Saved",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        if (savedShown.isEmpty()) {
                            Text(
                                if (dropQuery.isEmpty()) "Empty. Type a target, then tap the star."
                                else "No saved match for \"$dropQuery\".",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        savedShown.forEach { h ->
                            TargetRow(
                                title = h.label,
                                subtitle = h.host,
                                onPick = { vm.pickTarget(h.host) },
                                onDelete = { vm.deleteSaved(h.id) }
                            )
                        }
                        if (state.settings.maxRecent > 0) {
                            Text(
                                "Recent",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            if (recentShown.isEmpty()) {
                                Text(
                                    if (dropQuery.isEmpty()) "No history yet."
                                    else "No recent match for \"$dropQuery\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                            recentShown.forEach { h ->
                                TargetRow(
                                    title = h,
                                    subtitle = null,
                                    onPick = { vm.pickTarget(h) },
                                    onDelete = null
                                )
                            }
                        }
                    }
                }
            }

            // --- Tool selector: exactly 2 rows, divider-separated, no boxes.
            // Tap = select (+ auto-run when enabled, except IP Scan).
            // Long-press a server tool = change its server.
            var serverTool by remember { mutableStateOf<Tool?>(null) }
            ToolSelector(
                selected = state.tool,
                enabled = !state.running,
                settings = state.settings,
                onSelect = { if (state.settings.autoRunOnTool) vm.selectAndRun(it) else vm.setTool(it) },
                onLongPress = { t ->
                    if (toolServerSlot(t, state.settings) != null) serverTool = t
                }
            )
            serverTool?.let { t ->
                toolServerSlot(t, state.settings)?.let { slot ->
                    ServerPickerDialog(
                        tool = t,
                        slot = slot,
                        onSave = { vm.setToolServer(t, it) },
                        onDismiss = { serverTool = null }
                    )
                }
            }
            // --- Contextual options ---
            if (state.tool == Tool.DIG) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Record type", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DnsRunner.types.forEach { ty ->
                                FilterChip(
                                    selected = state.digType == ty,
                                    onClick = { vm.setDigType(ty) },
                                    label = { Text(ty) }
                                )
                            }
                        }
                        Text("DNS server: ${state.settings.dnsServer} (change in Settings)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.tool == Tool.TRACE) {
                Text(
                    "Max ${state.settings.maxHops} hops - change in Settings",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.CERT) {
                Text(
                    "Port 443 by default - type host:port for custom (e.g. mail.example.com:993)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.MYIP) {
                Text(
                    "Shows this device's current public IP - target field is ignored",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.IPINFO) {
                Text(
                    "Lookup any IP or domain - server from Settings",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.PORTS) {
                Text(
                    "Scans the port list from Settings (e.g. 22,80,8000-8010) - or type host:port for a single port",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.HEADERS) {
                Text(
                    "Type a URL or host, path included (https is assumed)",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (state.tool == Tool.SWEEP) {
                Text(
                    "Ping scan - range autofills above (e.g. 192.168.1.1-50, 10.0.0.0/24)",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // --- Output console: dark terminal panel ---
            Card(
                colors = CardDefaults.cardColors(containerColor = term.bg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // No static "Output" label (obvious enough): dynamic Run/Stop instead.
                            TextButton(onClick = { if (state.running) vm.stop() else vm.run() }) {
                                Icon(
                                    if (state.running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (state.running) term.green else term.text
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (state.running) "Stop" else "Run",
                                    color = if (state.running) term.green else term.text
                                )
                            }
                            // Scan progress, numbers only (e.g. 25/254), IP Scan only.
                            if (state.tool == Tool.SWEEP) {
                                state.progress?.let {
                                    val nums = Regex("""\d+/\d+""").find(it)?.value ?: it
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        nums,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = term.green,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { vm.bumpFont(-1f) }) {
                                Icon(Icons.Filled.TextDecrease, contentDescription = "Smaller text", tint = term.text)
                            }
                            IconButton(onClick = { vm.bumpFont(1f) }) {
                                Icon(Icons.Filled.TextIncrease, contentDescription = "Bigger text", tint = term.text)
                            }
                            TextButton(onClick = { vm.clearOutput() }) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = term.text
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Clear", color = term.text)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        SelectionContainer(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(state.lines) { line ->
                                    OutputLine(
                                        line = line,
                                        colored = state.settings.coloredOutput,
                                        p = term,
                                        fontSize = state.settings.outputFontSp.sp
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}

/** Compact two-line row for the saved/recent picker. Tap = use, X = delete. */@Composable
private fun TargetRow(
    title: String,
    subtitle: String?,
    onPick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onPick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Tool selector: always exactly 2 rows, items separated by thin divider
 * lines — no boxes, no chips. Tap = run immediately.
 * Long-press a server-backed tool (Dig/Whois/IP Info/My IP) = change server.
 */
@Composable
private fun ToolSelector(
    selected: Tool,
    enabled: Boolean,
    settings: AppSettings,
    onSelect: (Tool) -> Unit,
    onLongPress: (Tool) -> Unit
) {
    val tools = Tool.entries
    val half = (tools.size + 1) / 2
    Column(modifier = Modifier.fillMaxWidth()) {
        ToolSelectorRow(tools.take(half), selected, enabled, settings, onSelect, onLongPress)
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        ToolSelectorRow(tools.drop(half), selected, enabled, settings, onSelect, onLongPress)
    }
}

/** Server backing for the tools that have one; null = long-press does nothing. */
private data class ServerSlot(
    val label: String,
    val current: String,
    val presets: List<Pair<String, String>>
)

private fun toolServerSlot(tool: Tool, s: AppSettings): ServerSlot? = when (tool) {
    Tool.DIG -> ServerSlot("DNS server", s.dnsServer, DnsPresets.all)
    Tool.WHOIS -> ServerSlot("Whois server", s.whoisServer, WhoisPresets.all)
    Tool.IPINFO -> ServerSlot("IP lookup provider", s.ipLookupBase, IpInfoPresets.lookup)
    Tool.MYIP -> ServerSlot("My IP provider", s.myIpBase, IpInfoPresets.myIp)
    else -> null
}

/** Short host part for the tiny subtitle under a tool name. */
private fun shortServer(value: String): String =
    value.substringAfter("://").substringBefore("/")

@Composable
private fun ToolSelectorRow(
    tools: List<Tool>,
    selected: Tool,
    enabled: Boolean,
    settings: AppSettings,
    onSelect: (Tool) -> Unit,
    onLongPress: (Tool) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        tools.forEachIndexed { i, t ->
            if (i > 0) {
                VerticalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
            val isSel = t == selected
            val server = toolServerSlot(t, settings)?.current?.let(::shortServer)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .combinedClickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(t) },
                        onLongClick = { onLongPress(t) }
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        t.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (server != null) {
                        Text(
                            server,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** Long-press dialog: pick a preset or type a custom server for one tool. */
@Composable
private fun ServerPickerDialog(
    tool: Tool,
    slot: ServerSlot,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var custom by remember(slot.current) { mutableStateOf(slot.current) }
    var picked by remember(slot.current) { mutableStateOf(slot.current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${tool.title}: ${slot.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.trim(); picked = custom },
                    label = { Text("Custom") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                slot.presets.forEach { (name, url) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { picked = url; custom = url }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = picked == url,
                            onClick = { picked = url; custom = url }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                url,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(picked.trim()); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
