package id.web.izs.nettools.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.izs.nettools.R
import id.web.izs.nettools.data.SettingsRepository
import id.web.izs.nettools.model.AppSettings
import id.web.izs.nettools.model.DnsPresets
import id.web.izs.nettools.model.IpInfoPresets
import id.web.izs.nettools.model.RdapPresets
import id.web.izs.nettools.model.Tool
import id.web.izs.nettools.model.WhoisPresets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

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
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.choose_preset, label))
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

/**
 * Left/right edge dead zone for the tab pager: a swipe that starts inside the
 * system-gesture inset belongs to the Android back gesture, not to paging —
 * the two used to fight (sometimes the tab flipped, sometimes back fired).
 * Only horizontal drags are swallowed: taps and vertical scrolls still reach
 * the page content underneath.
 */
@Composable
private fun edgeDeadZone(): Modifier {
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val deadPx = with(density) { WindowInsets.systemGestures.getLeft(density, dir).toFloat() }
    return Modifier.pointerInput(deadPx) {
        if (deadPx <= 0f) return@pointerInput
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val w = size.width
            // Only strips at the very edges; the middle swipes normally.
            if (down.position.x > deadPx && down.position.x < w - deadPx) return@awaitEachGesture
            var armed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val ch = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (armed) {
                    down.consume()
                    event.changes.forEach { it.consume() }
                    if (!ch.pressed) return@awaitEachGesture
                    continue
                }
                if (!ch.pressed) return@awaitEachGesture
                val dx = abs(ch.position.x - down.position.x)
                val dy = abs(ch.position.y - down.position.y)
                if (dx > slop && dx > dy) {
                    armed = true
                    down.consume()
                    event.changes.forEach { it.consume() }
                } else if (dy > slop) {
                    return@awaitEachGesture
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: NetToolsViewModel, onBack: () -> Unit, onOpenColors: () -> Unit, onOpenAbout: () -> Unit, onOpenEditUi: () -> Unit, initialTab: Int = 0, onTabChange: (Int) -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val repo = remember { SettingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var s by remember(state.settings) { mutableStateOf(state.settings) }
    var dirty by remember { mutableStateOf(false) }
    val tabs = listOf(
        stringResource(R.string.tab_servers), stringResource(R.string.tab_scan),
        stringResource(R.string.tab_tools), stringResource(R.string.tab_general)
    )
    val pagerState = rememberPagerState(initialPage = initialTab) { tabs.size }
    // Report the visible tab so a sub-screen (Edit UI, Custom Colors) can bring
    // you back to where you left instead of resetting to the first tab.
    LaunchedEffect(pagerState.currentPage) { onTabChange(pagerState.currentPage) }

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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        // Flush any pending edit immediately on back.
                        scope.launch { repo.saveSettings(s) }
                        focusManager.clearFocus()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { repo.saveSettings(s) }
                        focusManager.clearFocus()
                        onOpenAbout()
                    }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.about))
                    }
                }
            )
        }
    ) { pad ->
        // Keyboard up: shrink the tab + pager area above the IME so a focused
        // form field scrolls into the visible part instead of hiding behind
        // the keyboard (edge-to-edge dispatches ime insets; nothing resizes).
        Column(modifier = Modifier.fillMaxSize().padding(pad).imePadding()) {
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
            // Swipeable pages: horizontal swipes switch tabs, except inside the
            // system-gesture strip at each edge, which is left to back-gesture.
            HorizontalPager(
                state = pagerState,
                // Neighbor pages stay composed so swiping never pays
                // first-composition cost mid-gesture (4 light pages, cheap).
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f).then(edgeDeadZone())
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
                        1 -> ScanTab(s, ::update)
                        2 -> ToolsTab(s, ::update)
                        else -> GeneralTab(s, ::update, onOpenColors, onOpenEditUi = {
                            // Flush pending edits before leaving: the debounced
                            // save dies with this composition.
                            scope.launch { repo.saveSettings(s) }
                            focusManager.clearFocus()
                            onOpenEditUi()
                        })
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
        label = stringResource(R.string.set_dns_server),
        value = s.dnsServer,
        presets = DnsPresets.all,
        onChange = { update(s.copy(dnsServer = it)) }
    )
    ServerDropdown(
        label = stringResource(R.string.set_ip_lookup),
        value = s.ipLookupBase,
        presets = IpInfoPresets.lookup,
        onChange = { update(s.copy(ipLookupBase = it)) }
    )
    ServerDropdown(
        label = stringResource(R.string.set_myip_server),
        value = s.myIpBase,
        presets = IpInfoPresets.myIp,
        onChange = { update(s.copy(myIpBase = it)) }
    )
    ServerDropdown(
        label = stringResource(R.string.set_rdap_server),
        value = s.rdapBase,
        presets = RdapPresets.all,
        onChange = { update(s.copy(rdapBase = it)) }
    )
    ServerDropdown(
        label = stringResource(R.string.set_whois_server),
        value = s.whoisServer,
        presets = WhoisPresets.all,
        onChange = { update(s.copy(whoisServer = it)) }
    )
    NumberField(
        value = s.whoisPort,
        range = 1..65535,
        label = stringResource(R.string.set_whois_port),
        onCommit = { update(s.copy(whoisPort = it)) }
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
        label = { Text(stringResource(R.string.set_gping_token)) },
        supportingText = { Text(stringResource(R.string.set_gping_token_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { syncToken(); focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) syncToken() }
    )
    if (showIpinfoToken) {
        OutlinedTextField(
            value = ipinfoText,
            onValueChange = { ipinfoText = it },
            label = { Text(stringResource(R.string.set_ipinfo_token)) },
            supportingText = { Text(stringResource(R.string.set_ipinfo_token_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { syncIpinfoToken(); focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) syncIpinfoToken() }
        )
    }
}

@Composable
private fun ScanTab(s: AppSettings, update: (AppSettings) -> Unit) {
    val focusManager = LocalFocusManager.current
    val numberField = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
    val done = KeyboardActions(onDone = { focusManager.clearFocus() })

    NumberField(
        value = s.maxHops,
        range = 1..64,
        label = stringResource(R.string.set_max_hops),
        onCommit = { update(s.copy(maxHops = it)) }
    )
    NumberField(
        value = s.timeoutMs,
        range = 500..30000,
        label = stringResource(R.string.set_timeout),
        onCommit = { update(s.copy(timeoutMs = it)) }
    )
    NumberField(
        value = s.maxParallel,
        range = 8..256,
        label = stringResource(R.string.set_parallel),
        onCommit = { update(s.copy(maxParallel = it)) }
    )
    NumberField(
        value = s.pingCount,
        range = 0..1000,
        label = stringResource(R.string.set_ping_count),
        onCommit = { update(s.copy(pingCount = it)) }
    )
    NumberField(
        value = s.loopPingCount,
        range = 4..100,
        label = stringResource(R.string.set_loop_count),
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
                label = { Text(stringResource(R.string.set_ports)) },
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
        Text(stringResource(R.string.set_scan_offline), modifier = Modifier.weight(1f))
        Switch(checked = s.scanShowOffline, onCheckedChange = { update(s.copy(scanShowOffline = it)) })
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.set_scan_mac), modifier = Modifier.weight(1f))
        Switch(checked = s.scanShowMac, onCheckedChange = { update(s.copy(scanShowMac = it)) })
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
    // Grid rows (1 vs 2) live in Edit UI — it's layout, not tool config.
    Text(
        stringResource(R.string.set_tools_help),
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
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up, t.title))
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
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down, t.title))
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
            Text(stringResource(R.string.reset_tool_order))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneralTab(s: AppSettings, update: (AppSettings) -> Unit, onOpenColors: () -> Unit, onOpenEditUi: () -> Unit) {
    @Composable
    fun section(title: String) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    section(stringResource(R.string.sec_language))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "system" to stringResource(R.string.lang_system),
            "en" to stringResource(R.string.lang_en),
            "in" to stringResource(R.string.lang_id)
        ).forEach { (code, label) ->
            FilterChip(
                selected = s.language == code,
                onClick = { update(s.copy(language = code)) },
                label = { Text(label) }
            )
        }
    }

    section(stringResource(R.string.sec_appearance))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            ServerDropdown(
                label = stringResource(R.string.set_theme),
                value = s.theme,
                presets = AppTheme.presets,
                onChange = { update(s.copy(theme = it)) },
                editable = false
            )
        }
        IconButton(onClick = onOpenColors) {
            Icon(Icons.Filled.Palette, contentDescription = stringResource(R.string.customize_colors))
        }
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.set_colored), modifier = Modifier.weight(1f))
        Switch(checked = s.coloredOutput, onCheckedChange = { update(s.copy(coloredOutput = it)) })
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.set_home_layout), modifier = Modifier.weight(1f))
        TextButton(onClick = onOpenEditUi) {
            Text(stringResource(R.string.edit_ui))
        }
    }

    section(stringResource(R.string.sec_run))
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.set_autorun_pick), modifier = Modifier.weight(1f))
        Switch(checked = s.autoRunOnPick, onCheckedChange = { update(s.copy(autoRunOnPick = it)) })
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.set_autorun_tool), modifier = Modifier.weight(1f))
        Switch(checked = s.autoRunOnTool, onCheckedChange = { update(s.copy(autoRunOnTool = it)) })
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.set_autoclear), modifier = Modifier.weight(1f))
        Switch(checked = s.autoClearOutput, onCheckedChange = { update(s.copy(autoClearOutput = it)) })
    }

    section(stringResource(R.string.sec_history))
    NumberField(
        value = s.maxRecent,
        range = 0..50,
        label = stringResource(R.string.set_max_recent),
        onCommit = { update(s.copy(maxRecent = it)) }
    )
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.set_hide_dupes), modifier = Modifier.weight(1f))
        Switch(checked = s.hideRecentDupes, onCheckedChange = { update(s.copy(hideRecentDupes = it)) })
    }
}
