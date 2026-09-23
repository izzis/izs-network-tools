package id.web.izs.nettools.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.izs.nettools.data.SettingsRepository
import id.web.izs.nettools.model.AppSettings
import id.web.izs.nettools.model.DnsPresets
import id.web.izs.nettools.model.IpInfoPresets
import id.web.izs.nettools.model.RdapPresets
import id.web.izs.nettools.model.Tool
import id.web.izs.nettools.model.WhoisPresets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Server field: editable text on the left, preset dropdown button on the right.
 *  With editable=false it becomes a pure picker (used for the app theme):
 *  an ExposedDropdownMenuBox, so taps anywhere open the menu and the text
 *  is never focusable/selectable (no copy popup). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerDropdown(
    label: String,
    value: String,
    presets: List<Pair<String, String>>,
    onChange: (String) -> Unit,
    editable: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    if (!editable) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = presets.firstOrNull { it.second == value }?.first ?: value,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.exposedDropdownSize()
            ) {
                presets.forEach { (name, url) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onChange(url)
                            expanded = false
                        }
                    )
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Typing stays local (no parent recompose per keystroke); the trimmed
        // value commits on Done, focus loss, or preset pick.
        var text by remember(value) { mutableStateOf(value) }
        fun sync() {
            val t = text.trim()
            if (t != value) onChange(t)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose $label preset")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { sync(); focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) sync() }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { (name, url) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(name)
                            Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        onChange(url)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Numeric field with deferred commit. Typing stays local (blank and partial
 *  input allowed) so single digits can be entered freely; the value is pushed
 *  to settings live when valid, and reverted when focus leaves with invalid
 *  text (tap another field/empty area) or on IME Done. Back press flushes via
 *  the live commits, so nothing half-typed is ever saved. */
@Composable
private fun NumberField(
    value: Int,
    range: IntRange,
    label: String,
    onCommit: (Int) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(value.toString()) }
    fun sync() {
        val n = text.toIntOrNull()
        if (n != null && n in range) onCommit(n) else text = value.toString()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            if (v.isEmpty() || (v.length <= 6 && v.all { it.isDigit() })) {
                text = v
                v.toIntOrNull()?.let { if (it in range) onCommit(it) }
            }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) sync() },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { sync(); focusManager.clearFocus() })
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: NetToolsViewModel, onBack: () -> Unit, onOpenColors: () -> Unit, onOpenAbout: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val repo = remember { SettingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var s by remember(state.settings) { mutableStateOf(state.settings) }
    var dirty by remember { mutableStateOf(false) }
    val tabs = listOf("Servers", "Whois", "Scan", "Tools", "General")
    val pagerState = rememberPagerState { tabs.size }

    // Auto-save (debounced): covers top-left back, system back gesture/button.
    LaunchedEffect(s) {
        if (!dirty) return@LaunchedEffect
        if (s == state.settings) {
            dirty = false
            return@LaunchedEffect
        }
        delay(600)
        repo.saveSettings(s)
        dirty = false
    }

    fun update(next: AppSettings) {
        // Same value (e.g. re-picking the active theme): ignore, otherwise
        // dirty stays true forever because LaunchedEffect(s) never restarts.
        if (next == s) return
        s = next
        dirty = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = {
                        // Flush any pending edit immediately on back.
                        scope.launch { repo.saveSettings(s) }
                        focusManager.clearFocus()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { repo.saveSettings(s) }
                        focusManager.clearFocus()
                        onOpenAbout()
                    }) {
                        Icon(Icons.Outlined.Info, contentDescription = "About")
                    }
                }
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            Text(
                if (dirty) "Saving..." else "Changes save automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = pagerState.currentPage == i,
                        onClick = {
                            focusManager.clearFocus()
                            scope.launch { pagerState.animateScrollToPage(i) }
                        },
                        text = { Text(title) }
                    )
                }
            }
            // Swipeable pages. Horizontal swipes here switch tabs; the Android
            // system back gesture (from the very screen edge) still works.
            HorizontalPager(
                state = pagerState,
                // Neighbor pages stay composed so swiping never pays
                // first-composition cost mid-gesture (4 light pages, cheap).
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f)
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                        .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (page) {
                        0 -> ServersTab(s, ::update)
                        1 -> WhoisTab(s, ::update)
                        2 -> ScanTab(s, ::update)
                        3 -> ToolsTab(s, ::update)
                        else -> GeneralTab(s, ::update, onOpenColors)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServersTab(s: AppSettings, update: (AppSettings) -> Unit) {
    val focusManager = LocalFocusManager.current
    ServerDropdown(
        label = "DNS server (Dig)",
        value = s.dnsServer,
        presets = DnsPresets.all,
        onChange = { update(s.copy(dnsServer = it)) }
    )
    ServerDropdown(
        label = "IP lookup server (IP Info)",
        value = s.ipLookupBase,
        presets = IpInfoPresets.lookup,
        onChange = { update(s.copy(ipLookupBase = it)) }
    )
    ServerDropdown(
        label = "My IP server (My IP)",
        value = s.myIpBase,
        presets = IpInfoPresets.myIp,
        onChange = { update(s.copy(myIpBase = it)) }
    )
    var tokenText by remember(s.globalpingToken) { mutableStateOf(s.globalpingToken) }
    fun syncToken() {
        val t = tokenText.trim()
        if (t != s.globalpingToken) update(s.copy(globalpingToken = t))
    }
    // IPinfo token: only relevant when one of the IP servers points at ipinfo.io.
    val showIpinfoToken = s.ipLookupBase.contains("ipinfo.io") || s.myIpBase.contains("ipinfo.io")
    var ipinfoText by remember(s.ipinfoToken) { mutableStateOf(s.ipinfoToken) }
    fun syncIpinfoToken() {
        val t = ipinfoText.trim()
        if (t != s.ipinfoToken) update(s.copy(ipinfoToken = t))
    }
    OutlinedTextField(
        value = tokenText,
        onValueChange = { tokenText = it },
        label = { Text("Globalping token (Global Ping/Trace)") },
        supportingText = { Text("Empty = anonymous (250 tests/hour)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { syncToken(); focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) syncToken() }
    )
    if (showIpinfoToken) {
        OutlinedTextField(
            value = ipinfoText,
            onValueChange = { ipinfoText = it },
            label = { Text("IPinfo token (IP Info / My IP)") },
            supportingText = { Text("Empty = anonymous (1000 lookups/day shared)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { syncIpinfoToken(); focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) syncIpinfoToken() }
        )
    }
}

@Composable
private fun WhoisTab(s: AppSettings, update: (AppSettings) -> Unit) {
    ServerDropdown(
        label = "RDAP server (modern Whois)",
        value = s.rdapBase,
        presets = RdapPresets.all,
        onChange = { update(s.copy(rdapBase = it)) }
    )
    ServerDropdown(
        label = "WHOIS server (port 43)",
        value = s.whoisServer,
        presets = WhoisPresets.all,
        onChange = { update(s.copy(whoisServer = it)) }
    )
    NumberField(
        value = s.whoisPort,
        range = 1..65535,
        label = "WHOIS port (default 43)",
        onCommit = { update(s.copy(whoisPort = it)) }
    )
}

@Composable
private fun ScanTab(s: AppSettings, update: (AppSettings) -> Unit) {
    val focusManager = LocalFocusManager.current
    val numberField = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
    val done = KeyboardActions(onDone = { focusManager.clearFocus() })

    NumberField(
        value = s.maxHops,
        range = 1..64,
        label = "Trace: max hops (1-64)",
        onCommit = { update(s.copy(maxHops = it)) }
    )
    NumberField(
        value = s.timeoutMs,
        range = 500..30000,
        label = "Network timeout ms (500-30000)",
        onCommit = { update(s.copy(timeoutMs = it)) }
    )
    NumberField(
        value = s.maxParallel,
        range = 8..256,
        label = "IP Scan: parallel probes (8-256)",
        onCommit = { update(s.copy(maxParallel = it)) }
    )
    NumberField(
        value = s.pingCount,
        range = 0..1000,
        label = "Ping count (0 = nonstop)",
        onCommit = { update(s.copy(pingCount = it)) }
    )
    NumberField(
        value = s.loopPingCount,
        range = 4..100,
        label = "Loop: gateway ping count (4-100, ~1/sec)",
        onCommit = { update(s.copy(loopPingCount = it)) }
    )
            var portsText by remember(s.portList) { mutableStateOf(s.portList) }
            fun syncPorts() {
                val t = portsText.filter { c -> c.isDigit() || c in ",; \n\t-" }
                if (t != s.portList) update(s.copy(portList = t))
            }
            OutlinedTextField(
                value = portsText,
                // Number keyboard + filter: only digits, separators and "-" get through.
                onValueChange = { v ->
                    portsText = v.filter { c -> c.isDigit() || c in ",; \n\t-" }
                },
                label = { Text("Ports: port list (e.g. 22,80,8000-8010)") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) syncPorts() },
                keyboardOptions = numberField,
                keyboardActions = KeyboardActions(onDone = { syncPorts(); focusManager.clearFocus() })
            )
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Show offline hosts in IP Scan (RTO)")
        Switch(checked = s.scanShowOffline, onCheckedChange = { update(s.copy(scanShowOffline = it)) })
    }
}

@Composable
private fun ToolsTab(s: AppSettings, update: (AppSettings) -> Unit) {
    // Display order mirrors the home grid: stored order first, then any tool
    // missing from it (forward-compatible with newly added tools).
    val ordered = remember(s.toolOrder) {
        val known = s.toolOrder.mapNotNull { Tool.of(it) }
        known + Tool.entries.filter { it !in known }
    }
    val enabledCount = ordered.count { it.name !in s.disabledTools }
    Text(
        "Pick which tools show on Home and in what order. At least one must stay on.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ordered.forEachIndexed { i, t ->
        val enabled = t.name !in s.disabledTools
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                t.title,
                modifier = Modifier.weight(1f),
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = {
                    val next = ordered.toMutableList()
                    next[i] = next[i - 1]
                    next[i - 1] = t
                    update(s.copy(toolOrder = next.map { it.name }))
                },
                enabled = i > 0
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${t.title} up")
            }
            IconButton(
                onClick = {
                    val next = ordered.toMutableList()
                    next[i] = next[i + 1]
                    next[i + 1] = t
                    update(s.copy(toolOrder = next.map { it.name }))
                },
                enabled = i < ordered.lastIndex
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${t.title} down")
            }
            Switch(
                checked = enabled,
                // Refuse to switch off the last enabled tool: an empty grid
                // would strand the Home screen with nothing to run.
                onCheckedChange = { on ->
                    if (!on && enabledCount <= 1) return@Switch
                    update(
                        s.copy(
                            disabledTools = if (on) s.disabledTools - t.name
                            else s.disabledTools + t.name
                        )
                    )
                }
            )
        }
    }
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        TextButton(
            onClick = {
                update(
                    s.copy(
                        toolOrder = Tool.entries.map { it.name },
                        disabledTools = AppSettings().disabledTools
                    )
                )
            }
        ) {
            Text("Reset tool order")
        }
    }
}

@Composable
private fun GeneralTab(s: AppSettings, update: (AppSettings) -> Unit, onOpenColors: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            ServerDropdown(
                label = "App theme",
                value = s.theme,
                presets = AppTheme.presets,
                onChange = { update(s.copy(theme = it)) },
                editable = false
            )
        }
        IconButton(onClick = onOpenColors) {
            Icon(Icons.Filled.Palette, contentDescription = "Customize colors")
        }
    }
    NumberField(
        value = s.maxRecent,
        range = 0..50,
        label = "Max recent targets (0 = off)",
        onCommit = { update(s.copy(maxRecent = it)) }
    )
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Auto-run when picking a saved target")
        Switch(checked = s.autoRunOnPick, onCheckedChange = { update(s.copy(autoRunOnPick = it)) })
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Auto-run when picking a tool")
        Switch(checked = s.autoRunOnTool, onCheckedChange = { update(s.copy(autoRunOnTool = it)) })
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Colored output")
        Switch(checked = s.coloredOutput, onCheckedChange = { update(s.copy(coloredOutput = it)) })
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Clear output on each run")
        Switch(checked = s.autoClearOutput, onCheckedChange = { update(s.copy(autoClearOutput = it)) })
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Hide recents already in Saved")
        Switch(checked = s.hideRecentDupes, onCheckedChange = { update(s.copy(hideRecentDupes = it)) })
    }
}
