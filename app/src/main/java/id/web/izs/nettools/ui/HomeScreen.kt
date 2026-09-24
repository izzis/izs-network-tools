package id.web.izs.nettools.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.web.izs.nettools.core.DnsRunner
import id.web.izs.nettools.core.GlobalpingRunner
import id.web.izs.nettools.core.LoopResult
import id.web.izs.nettools.core.LoopRunner
import id.web.izs.nettools.core.StormResult
import id.web.izs.nettools.core.WifiAnalyzerRunner
import id.web.izs.nettools.model.AppSettings
import id.web.izs.nettools.model.DnsPresets
import id.web.izs.nettools.model.GlobalpingCountries
import id.web.izs.nettools.model.IpInfoPresets
import id.web.izs.nettools.model.SavedSort
import id.web.izs.nettools.model.Tool
import id.web.izs.nettools.model.WhoisPresets
import id.web.izs.nettools.model.orderedEnabledTools
import id.web.izs.nettools.model.sortedFor
import kotlinx.coroutines.delay

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
        t.contains("Destination reached") || t.startsWith("No loop:") ||
        t.startsWith("No storm:")
    ) return p.green
    // Loop verdicts: detected/suspected always stand out.
    if (t.startsWith("LOOP DETECTED") || t.startsWith("Suspected loop") ||
        t.startsWith("STORM DETECTED") || t.startsWith("Suspected storm")
    ) return p.red
    // Warnings and bad states.
    if (t.startsWith("Trusted: NO") || t.contains("EXPIRED") ||
        t.contains("NOT YET VALID") || t.contains("NXDOMAIN", ignoreCase = true) ||
        t.contains("unknown host", ignoreCase = true) ||
        t.contains("unreachable", ignoreCase = true) ||
        t.contains("Request timeout", ignoreCase = true) ||
        t.contains("no answer yet", ignoreCase = true)
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

/** MAC line of a WiFi Analyzer AP block (`aa:bb:cc:dd:ee:ff  ch…`). */
private val wifiMacLine = Regex("^(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b.*")

/**
 * One output line: semantic color + dim key / bright value for "Key: value" lines.
 * WiFi AP blocks (SSID\\nMAC…) get a bright title row and a dim detail row so
 * consecutive APs read as separate groups instead of one wall of text.
 */
@Composable
private fun OutputLine(
    line: String,
    colored: Boolean,
    p: TerminalPalette,
    fontSize: TextUnit,
    bottomSpacer: Boolean = false,
    /** BSSID of the associated AP — colors that AP's SSID row green. */
    wifiConnBssid: String = ""
) {
    // Live-update bookkeeping ("key\ntext") is never shown.
    val line = GlobalpingRunner.displayOf(line)
    val body: @Composable () -> Unit = {
        when {
            !colored -> Text(
                line,
                color = p.text,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize
            )
            // WiFi AP block: line 1 = SSID/signal, line 2 = MAC/ch/band (dim).
            // Connected AP: line 1 is green instead of the usual semantic color.
            line.indexOf('\n').let { nl ->
                nl > 0 && wifiMacLine.matches(line.substring(nl + 1).trim())
            } -> {
                val nl = line.indexOf('\n')
                val mac = line.substring(nl + 1).trim().substringBefore(' ')
                val titleColor = if (
                    wifiConnBssid.isNotEmpty() && mac.equals(wifiConnBssid, true)
                ) p.green else terminalLineColor(line.substring(0, nl), p)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = titleColor)) {
                            append(line.substring(0, nl))
                        }
                        append('\n')
                        withStyle(SpanStyle(color = p.dim)) {
                            append(line.substring(nl + 1))
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize
                )
            }
            else -> {
                val kv = if (!line.contains('\t')) kvPattern.find(line) else null
                // Loop verdict lines keep their full semantic color (whole line green
                // or red) instead of a dimmed "Key:" prefix — the verdict must pop.
                val t = line.trimStart()
                val isVerdict = t.startsWith("LOOP DETECTED") || t.startsWith("Suspected loop") ||
                    t.startsWith("No loop:") || t.startsWith("STORM DETECTED") ||
                    t.startsWith("Suspected storm") || t.startsWith("No storm:")
                if (!isVerdict && kv != null && kv.groupValues[2].isNotEmpty() &&
                    !t.startsWith(";;") && !t.startsWith("==")
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = p.dim)) { append(kv.groupValues[1] + ":") }
                            append(" ")
                            withStyle(SpanStyle(color = terminalLineColor(line, p))) {
                                append(kv.groupValues[2])
                            }
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
        }
    }
    if (bottomSpacer) {
        Column(Modifier.padding(bottom = 10.dp)) { body() }
    } else {
        body()
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
    // Console follows the app theme: per-theme dark terminal on dark themes,
    // theme surfaces on light themes.
    val scheme = MaterialTheme.colorScheme
    val term = remember(state.settings.theme, scheme.surfaceContainer, state.settings.customColors) {
        val base = if (AppTheme.isDark(state.settings.theme))
            DarkTerminal.copy(bg = defaultTerminalBg(state.settings.theme, scheme))
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
        if (state.lines.isEmpty()) return@LaunchedEffect
        // WiFi Analyzer: follow the bottom only for the first fill; once a
        // full cycle has run, keep the viewport — LIVE rows replace in place
        // and a new AP / notice line must not jump the user's position.
        if (state.tool == Tool.WIFIANALYZER && state.wifiCycles > 0) return@LaunchedEffect
        listState.scrollToItem(state.lines.size - 1)
    }

    // --- WiFi scan permission (first runtime permission in the app) ---
    val context = LocalContext.current
    val wifiPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // getScanResults() needs FINE_LOCATION on every API (OEMs ignore
        // neverForLocation + NEARBY alone); NEARBY is still required on 33+.
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val nearby = Build.VERSION.SDK_INT < 33 ||
            grants[Manifest.permission.NEARBY_WIFI_DEVICES] == true
        if (fine && nearby) {
            if (!isLocationEnabled(context)) {
                promptLocationSettings(context, vm)
            } else {
                vm.run()
            }
        } else {
            vm.setMessage(
                "Allow Location (and Nearby devices on Android 13+) in the system " +
                    "dialog — or enable them for this app in Settings > Apps > Permissions"
            )
        }
    }
    val runAction: () -> Unit = {
        if (state.tool == Tool.WIFIANALYZER) {
            when {
                !hasWifiScanPermission(context) -> {
                    val perms = if (Build.VERSION.SDK_INT >= 33) {
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.NEARBY_WIFI_DEVICES
                        )
                    } else {
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    wifiPermLauncher.launch(perms)
                }
                // Location services must be on for getScanResults on all APIs
                // (permission alone still yields an empty list).
                !isLocationEnabled(context) ->
                    promptLocationSettings(context, vm)
                else -> vm.run()
            }
        } else {
            vm.run()
        }
    }
    // Countdown to the next WiFi scan cycle (ticks locally; lastRefreshAt
    // resets it after every completed cycle).
    var wifiCountdown by remember { mutableStateOf(0) }
    LaunchedEffect(state.lastRefreshAt, state.running, state.tool) {
        if (state.tool != Tool.WIFIANALYZER || !state.running || state.lastRefreshAt == 0L) {
            wifiCountdown = 0
            return@LaunchedEffect
        }
        while (state.running) {
            val left = WifiAnalyzerRunner.REFRESH_MS - (System.currentTimeMillis() - state.lastRefreshAt)
            wifiCountdown = (left / 1000).toInt().coerceIn(0, WifiAnalyzerRunner.REFRESH_MS / 1000)
            delay(500)
        }
        wifiCountdown = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("izs NetTools") },
                actions = {
                    // Hide collapses the tool grid (taller terminal). When
                    // collapsed, this button shows the active tool and re-opens
                    // the grid on tap so you can still switch tools.
                    TextButton(onClick = { vm.toggleToolGrid() }) {
                        Text(
                            if (state.settings.hideToolGrid) state.tool.title else "Hide",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state.settings.hideToolGrid) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )
                    }
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
                placeholder = {
                    Text(if (state.tool == Tool.WIFIANALYZER) "SSID or MAC filter (optional)" else "Target (IP / host)")
                },
                singleLine = true,
                leadingIcon = {
                    if (state.target.isEmpty()) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    } else {
                        // Clear doubles as the leading icon: no extra trailing
                        // button; focus stays so the keyboard remains open.
                        IconButton(onClick = { vm.setTarget("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear target")
                        }
                    }
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
                    runAction()
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
            // Tap = select (+ auto-run when enabled, except IP Scan and Loop).
            // Long-press a server tool = change its server; hold Loop = pick mode.
            // Hidden via top-bar Hide → more terminal height; button shows tool name.
            var serverTool by remember { mutableStateOf<Tool?>(null) }
            var scopeTool by remember { mutableStateOf<Tool?>(null) }
            var loopModeTool by remember { mutableStateOf<Tool?>(null) }
            if (!state.settings.hideToolGrid) {
                ToolSelector(
                    selected = state.tool,
                    enabled = !state.running,
                    settings = state.settings,
                    extraSub = { t ->
                        when (t) {
                            Tool.PING -> if (state.pingGlobal) globalSub(state.globalProbes, state.globalCountry) else "Local"
                            Tool.TRACE -> if (state.traceGlobal) globalSub(state.globalProbes, state.globalCountry) else "Local"
                            Tool.PORTS -> if (state.portsGlobal) "Global" else "Local"
                            Tool.LOOP -> state.loopMode.sub
                            // WiFi Analyzer: no gray subtitle — the filter tabs
                            // below the hint carry that state more clearly.
                            Tool.WIFIANALYZER -> null
                            else -> null
                        }
                    },
                    onSelect = { if (state.settings.autoRunOnTool) vm.selectAndRun(it) else vm.setTool(it) },
                    onLongPress = { t ->
                        when {
                            toolServerSlot(t, state.settings) != null -> serverTool = t
                            t == Tool.PING || t == Tool.TRACE || t == Tool.PORTS -> scopeTool = t
                            t == Tool.LOOP -> loopModeTool = t
                        }
                    }
                )
            }
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
            scopeTool?.let { t ->
                val isGlobal = when (t) {
                    Tool.PING -> state.pingGlobal
                    Tool.TRACE -> state.traceGlobal
                    else -> state.portsGlobal
                }
                ScopePickerDialog(
                    tool = t,
                    isGlobal = isGlobal,
                    probes = state.globalProbes,
                    country = state.globalCountry,
                    simple = t == Tool.PORTS,
                    onSave = { g, n, c ->
                        when (t) {
                            Tool.PING -> vm.setPingGlobal(g)
                            Tool.TRACE -> vm.setTraceGlobal(g)
                            else -> vm.setPortsGlobal(g)
                        }
                        vm.setGlobalProbes(n)
                        vm.setGlobalCountry(c)
                    },
                    onDismiss = { scopeTool = null }
                )
            }
            loopModeTool?.let {
                LoopModePickerDialog(
                    current = state.loopMode,
                    onSave = { vm.setLoopMode(it) },
                    onDismiss = { loopModeTool = null }
                )
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
                    (if (state.traceGlobal) "Global trace via Globalping (x${state.globalProbes} probes) - hold Trace to change"
                    else "Max ${state.settings.maxHops} hops - change in Settings (hold Trace for Global)"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.LOOP) {
                Text(
                    "Empty target = auto gateway (L2 storm + L3 trace, max ${state.settings.maxHops} hops) - or type a host/IP to trace L3 there (hold Loop for L2/L3/Both)",
                    style = MaterialTheme.typography.bodySmall
                )
                // Verdict banners: pop in when the Loop run finishes (or stops
                // early on a proven L3 loop). Clean runs stay console-only.
                state.stormVerdict?.let { StormVerdictBanner(it) }
                state.loopVerdict?.let { LoopVerdictBanner(it) }
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
            if (state.tool == Tool.NEIGHBOR) {
                Text(
                    "Hears who's on the LAN: MikroTik (MNDP, even without IP), services (mDNS), devices (SSDP) - target field is ignored",
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
            if (state.tool == Tool.WIFIANALYZER) {
                Text(
                    "Live nearby APs, re-scanned every ${WifiAnalyzerRunner.REFRESH_MS / 1000}s - " +
                        "type part of an SSID above to match it (optional)",
                    style = MaterialTheme.typography.bodySmall
                )
                // Filters as a Settings-style 2-row block: dimension tabs on
                // top, values for the active dimension below — fixed height
                // no matter how many filter kinds exist. SSIDs stay free text
                // in the target bar (names are too random to enumerate).
                var wifiFilterDim by remember { mutableStateOf(0) }
                PrimaryTabRow(selectedTabIndex = wifiFilterDim) {
                    listOf("Band", "Channel", "Security").forEachIndexed { i, name ->
                        Tab(
                            selected = wifiFilterDim == i,
                            onClick = { wifiFilterDim = i },
                            text = {
                                Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                        )
                    }
                }
                when (wifiFilterDim) {
                    0 -> WifiOptionRow(
                        options = listOf("" to "All", "2.4" to "2.4", "5" to "5", "6" to "6"),
                        selected = state.wifiBand,
                        onSelect = vm::setWifiBand
                    )
                    1 -> {
                        val chans = listOf(-1) + state.wifiChannels
                        WifiOptionRow(
                            options = chans.map { c -> c to (if (c == -1) "All" else "$c") },
                            selected = state.wifiChannel,
                            onSelect = vm::setWifiChannel
                        )
                    }
                    else -> WifiOptionRow(
                        options = listOf(
                            "" to "All", "WPA3" to "WPA3", "WPA2" to "WPA2",
                            "WPA" to "WPA", "WEP" to "WEP", "open" to "open"
                        ),
                        selected = state.wifiSecurity,
                        onSelect = vm::setWifiSecurity
                    )
                }
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
                            TextButton(onClick = { if (state.running) vm.stop() else runAction() }) {
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
                            // Scan/Loop progress, numbers only (e.g. 25/254), plus
                            // Global progress (e.g. 3/10 probes) while a global run is live.
                            val isGlobalRun = (state.tool == Tool.PING && state.pingGlobal) ||
                                (state.tool == Tool.TRACE && state.traceGlobal)
                            if (state.tool == Tool.SWEEP || state.tool == Tool.LOOP || (isGlobalRun && state.running)) {
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
                            // WiFi Analyzer: countdown to the next scan cycle.
                            // Intrinsic width only — a weight slot here clips
                            // "next 30s" down to "next 9s"-length space.
                            if (state.tool == Tool.WIFIANALYZER && state.running && wifiCountdown > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "next ${wifiCountdown}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = term.green,
                                    maxLines = 1
                                )
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
                                items(state.lines) { raw ->
                                    // WiFi AP blocks get bottom margin so each
                                    // SSID+MAC pair is a distinct visual group.
                                    val shown = GlobalpingRunner.displayOf(raw)
                                    val isApBlock = shown.indexOf('\n').let { nl ->
                                        nl > 0 && wifiMacLine.matches(shown.substring(nl + 1).trim())
                                    }
                                    OutputLine(
                                        line = raw,
                                        colored = state.settings.coloredOutput,
                                        p = term,
                                        fontSize = state.settings.outputFontSp.sp,
                                        bottomSpacer = isApBlock,
                                        wifiConnBssid = state.wifiConnBssid
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
 * Loop verdict banner for the Loop tool (L3 phase). Red error card when a routing
 * loop is proven, tertiary card when the trace ran full length without
 * arriving. Clean traces stay console-only (no banner, no clutter).
 * Hidden until the first verdict lands.
 */
@Composable
private fun LoopVerdictBanner(verdict: LoopResult) {
    // Clean trace: console text is enough, don't banner it.
    if (verdict is LoopResult.NoLoop) return
    val container: Color
    val onContainer: Color
    val icon: ImageVector
    val title: String
    val detail: String
    when (verdict) {
        is LoopResult.Loop -> {
            container = MaterialTheme.colorScheme.errorContainer
            onContainer = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Filled.Error
            title = "Routing loop detected"
            detail = verdict.message
                .removePrefix("LOOP DETECTED: ")
                .removeSuffix(" (routing loop suspected)")
                .removeSuffix(" (routing loop confirmed)")
        }
        is LoopResult.Suspected -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer
            icon = Icons.Filled.Warning
            title = "Possible loop - destination never reached"
            detail = verdict.message
        }
        is LoopResult.NoLoop -> return // Unreachable: filtered above.
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = onContainer)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = onContainer
                )
            }
        }
    }
}

/**
 * Storm verdict banner for the Loop tool (L2 phase). Red error card on a
 * detected broadcast storm, tertiary card on suspicion. Clean gateways stay
 * console-only (no banner, no clutter).
 */
@Composable
private fun StormVerdictBanner(verdict: StormResult) {
    // Clean gateway: console text is enough, don't banner it.
    if (verdict is StormResult.NoStorm) return
    val container: Color
    val onContainer: Color
    val icon: ImageVector
    val title: String
    val detail: String
    when (verdict) {
        is StormResult.Storm -> {
            container = MaterialTheme.colorScheme.errorContainer
            onContainer = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Filled.Error
            title = "Broadcast storm detected"
            detail = verdict.message
                .removePrefix("STORM DETECTED: ")
                .removeSuffix(" (frames circulating — L2 loop suspected)")
                .removeSuffix(" (broadcast storm suspected)")
                .removeSuffix(" (replies drowned by a flood — L2 loop suspected)")
                .removeSuffix(" (L2 loop suspected)")
        }
        is StormResult.Suspected -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer
            icon = Icons.Filled.Warning
            title = "Possible storm - gateway looks off"
            detail = verdict.message
        }
        is StormResult.NoStorm -> return // Unreachable: filtered above.
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = onContainer)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = onContainer
                )
            }
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
    extraSub: (Tool) -> String?,
    onSelect: (Tool) -> Unit,
    onLongPress: (Tool) -> Unit
) {
    val tools = settings.orderedEnabledTools()
    if (tools.isEmpty()) return
    val half = (tools.size + 1) / 2
    Column(modifier = Modifier.fillMaxWidth()) {
        ToolSelectorRow(tools.take(half), selected, enabled, settings, extraSub, onSelect, onLongPress)
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        ToolSelectorRow(tools.drop(half), selected, enabled, settings, extraSub, onSelect, onLongPress)
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
    extraSub: (Tool) -> String?,
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
            val server = extraSub(t)
                ?: toolServerSlot(t, settings)?.current?.let(::shortServer)
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
                    // Long names ("WiFi Analyzer") take both text lines and skip
                    // the subtitle, so the cell stays as tall as every other
                    // 2-line cell (title + gray sub) — the row never grows.
                    val twoLineTitle = t.title.length > 11
                    Text(
                        t.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = if (twoLineTitle) 2 else 1
                    )
                    if (!twoLineTitle) {
                        // Short titles keep the subtitle so every cell stays
                        // uniformly 2 lines tall (nbsp placeholder when none).
                        Text(
                            server ?: " ",
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

/** Short gray subtitle for an active global scope, e.g. "Global x10" or "Global x1 ID". */
private fun globalSub(probes: Int, country: String): String =
    if (country.isEmpty()) "Global x$probes" else "Global x$probes $country"

/**
 * Runtime grant check for the WiFi Analyzer scan. FINE_LOCATION is required
 * on every API (several OEMs still reject getScanResults with NEARBY alone);
 * NEARBY is also required on 33+ per the platform contract.
 */
private fun hasWifiScanPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!fine) return false
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
        PackageManager.PERMISSION_GRANTED
}

/** System Location toggle state — permission alone doesn't fill scan results pre-33. */
private fun isLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return true
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        lm.isLocationEnabled
    } else {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}

/** Location is off: explain + jump to the system toggle (it cannot be flipped in-app). */
private fun promptLocationSettings(context: Context, vm: NetToolsViewModel) {
    vm.setMessage(
        "Turn on Location (GPS) — Android returns empty WiFi scans without it. " +
            "Also allow Location for this app in Settings > Apps, then tap Run"
    )
    try {
        context.startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

/** One option strip for the active filter dimension: tight chips, one row, h-scroll. */
@Composable
private fun <T> WifiOptionRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = {
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                },
                modifier = Modifier.height(28.dp)
            )
        }
    }
}

/** Long-press dialog for Ping/Trace/Ports: local engine or global source.
 *  Simple variant (Ports) hides probe count + country: just Local vs Global. */
@Composable
private fun ScopePickerDialog(
    tool: Tool,
    isGlobal: Boolean,
    probes: Int,
    country: String,
    simple: Boolean = false,
    onSave: (Boolean, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var global by remember { mutableStateOf(isGlobal) }
    var n by remember { mutableStateOf(probes) }
    var c by remember { mutableStateOf(country) }
    var custom by remember { mutableStateOf(country) }
    val localLabel = when (tool) {
        Tool.PING -> "This device"
        Tool.TRACE -> "System"
        else -> "Local (live TCP connect)"
    }
    val globalLabel = if (simple) "Global (Shodan InternetDB)" else "Global (worldwide probes)"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${tool.title}: source") },
        text = {
            // Fixed header (source, probes, custom field); only the
            // country preset list below scrolls.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(false to localLabel, true to globalLabel).forEach { (g, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { global = g }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = global == g, onClick = { global = g })
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (global && !simple) {
                    Text(
                        "Probes",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 5, 10, 25, 50).forEach { count ->
                            FilterChip(
                                selected = n == count,
                                onClick = { n = count },
                                label = { Text("$count") }
                            )
                        }
                    }
                }
                // Single probe: let the user pick where it runs from.
                // Empty code = API picks randomly worldwide (the default).
                if (global && n == 1 && !simple) {
                    Text(
                        "Probe location",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.trim().uppercase().take(2); c = custom },
                        label = { Text("Country code (empty = auto)") },
                        placeholder = { Text("e.g. ID") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Only this preset list scrolls; everything above stays pinned.
                    Column(
                        modifier = Modifier
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        GlobalpingCountries.all.forEach { (name, code) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { c = code; custom = code },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = c == code,
                                    onClick = { c = code; custom = code }
                                )
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(global, n, c); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
/** Long-press dialog for Loop: which half of the loop check to run.
 *  Per-session (like the Ping/Trace/Ports scope), default L2+L3. */
@Composable
private fun LoopModePickerDialog(
    current: LoopRunner.LoopMode,
    onSave: (LoopRunner.LoopMode) -> Unit,
    onDismiss: () -> Unit
) {
    var picked by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loop: detection mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LoopRunner.LoopMode.entries.forEach { m ->
                    val hint = when (m) {
                        LoopRunner.LoopMode.BOTH -> "Gateway storm check + trace (default)"
                        LoopRunner.LoopMode.L2_ONLY -> "Gateway storm check only (fast)"
                        LoopRunner.LoopMode.L3_ONLY -> "Routing-loop trace only"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { picked = m }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = picked == m, onClick = { picked = m })
                        Column {
                            Text(m.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(picked); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

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
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
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
