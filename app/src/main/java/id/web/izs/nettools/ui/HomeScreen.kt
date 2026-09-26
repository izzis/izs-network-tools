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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import id.web.izs.nettools.R
import id.web.izs.nettools.core.DnsRunner
import id.web.izs.nettools.core.GlobalpingRunner
import id.web.izs.nettools.core.LoopResult
import id.web.izs.nettools.core.LoopRunner
import id.web.izs.nettools.core.PortChecker
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

/** Dig answer row from DnsRunner.answerRow (`name 300 IN A 1.2.3.4`). Matched on a
 *  copy whose NBSP padding became plain spaces — same length, so group ranges still
 *  index into the original line. */
private val dnsAnswer = Regex("^(\\S+)\\s+(\\d+|-)\\s+IN\\s+([A-Z]+)\\s(.+)$")

/** MAC line of a WiFi Analyzer AP block (`aa:bb:cc:dd:ee:ff  ch…`). */
private val wifiMacLine = Regex("^(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b.*")

/** Raw server text (whois, RDAP, DNS) can carry tabs. Compose has no tab stops:
 *  '\t' measures zero-width AND is a break point, so the row renders merged
 *  ("300INA103.26.10.4") and wraps before the value. Expand each tab to spaces
 *  on 8-cell stops — TermMono is 1 cell per char. */
private fun String.expandTabs(): String {
    if ('\t' !in this) return this
    return lineSequence().joinToString("\n") { row ->
        buildString {
            for (ch in row) {
                // NBSP: padding must not become a break point, or a wrapped row
                // starts line 2 with leftover spaces.
                if (ch == '\t') repeat(8 - (length % 8)) { append('\u00A0') }
                else append(ch)
            }
        }
    }
}

/**
 * One output line: semantic color + dim key / bright value for "Key: value" lines.
 * WiFi AP blocks (SSID\\nMAC…) get a bright title row and a same-color detail row so
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
    val raw = GlobalpingRunner.displayOf(line)
    val line = raw.expandTabs()
    val body: @Composable () -> Unit = {
        when {
            !colored -> Text(
                line,
                color = p.text,
                fontFamily = TermMono,
                fontSize = fontSize
            )
            // WiFi AP block: line 1 = SSID/signal, line 2 = MAC/ch/width/band/sec,
            // optional line 3 (3-row mode) = vendor · 802.11 (WiFi N) · markers.
            // Connected AP: line 1 is green instead of the usual semantic color.
            // (gone)/(filter) rows dim ALL lines so inactive APs recede.
            line.split('\n').let { ls ->
                ls.size >= 2 && wifiMacLine.matches(ls[1].trim())
            } -> {
                val lines = line.split('\n')
                val head = lines[0]
                val inactive = line.contains("(gone)") || line.contains("(filter)")
                val mac = lines[1].trim().substringBefore(' ')
                val titleColor = when {
                    inactive -> p.dim
                    wifiConnBssid.isNotEmpty() && mac.equals(wifiConnBssid, true) -> p.green
                    else -> terminalLineColor(head, p)
                }
                val detailColor = if (inactive) p.dim else p.text
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = titleColor)) {
                            append(head)
                        }
                        lines.drop(1).forEach { l ->
                            append('\n')
                            withStyle(SpanStyle(color = detailColor)) {
                                append(l)
                            }
                        }
                    },
                    fontFamily = TermMono,
                    fontSize = fontSize
                )
            }
            else -> {
                val kv = if (!raw.contains('\t')) kvPattern.find(line) else null
                // Loop verdict lines keep their full semantic color (whole line green
                // or red) instead of a dimmed "Key:" prefix — the verdict must pop.
                val t = line.trimStart()
                val isVerdict = t.startsWith("LOOP DETECTED") || t.startsWith("Suspected loop") ||
                    t.startsWith("No loop:") || t.startsWith("STORM DETECTED") ||
                    t.startsWith("Suspected storm") || t.startsWith("No storm:")
                val dns = if (!isVerdict) dnsAnswer.find(line.replace('\u00A0', ' ')) else null
                when {
                    // Dig answer row: the domain and the value stay on p.text, only
                    // the ttl/class gutter dims (a dim domain read as another `;;`
                    // comment) and the record type carries the family colour — all
                    // from the palette, so light themes render dark-on-light.
                    dns != null -> {
                        val name = dns.groups[1]!!
                        val type = dns.groups[3]!!
                        val typeColor = when (type.value) {
                            "A", "AAAA" -> p.green
                            "TXT" -> p.amber
                            else -> p.blue
                        }
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = p.text)) {
                                    append(line.substring(0, name.range.last + 1))
                                }
                                withStyle(SpanStyle(color = p.dim)) {
                                    append(line.substring(name.range.last + 1, type.range.first))
                                }
                                withStyle(SpanStyle(color = typeColor)) { append(type.value) }
                                withStyle(SpanStyle(color = p.text)) {
                                    append(line.substring(type.range.last + 1))
                                }
                            },
                            fontFamily = TermMono,
                            fontSize = fontSize
                        )
                    }
                    !isVerdict && kv != null && kv.groupValues[2].isNotEmpty() &&
                        !t.startsWith(";;") && !t.startsWith("==") ->
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = p.dim)) { append(kv.groupValues[1] + ":") }
                                append(" ")
                                withStyle(SpanStyle(color = terminalLineColor(line, p))) {
                                    append(kv.groupValues[2])
                                }
                            },
                            fontFamily = TermMono,
                            fontSize = fontSize
                        )
                    else -> Text(
                        line,
                        color = terminalLineColor(line, p),
                        fontFamily = TermMono,
                        fontSize = fontSize
                    )
                }
            }
        }
    }
    // Row gap: a wrapped row would otherwise run straight into the next output
    // line — the slightly wider gap is what tells two output lines apart (same
    // idea as the WiFi AP block gap, which is bigger to group SSID + MAC + detail).
    Column(Modifier.padding(bottom = if (bottomSpacer) 12.dp else 6.dp)) { body() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    vm: NetToolsViewModel,
    onOpenSettings: () -> Unit,
    onOpenHosts: () -> Unit,
    onOpenEditUi: () -> Unit
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
    // Saved capped short in the dropdown (full list lives in Manage Hosts);
    // Recent shows everything storage kept — that IS the maxRecent setting,
    // so the number in Settings always matches what appears here.
    // Typing in the target bar widens Saved so a filter can still reach the rest.
    val savedShown = (if (dropQuery.isEmpty()) savedBase
        else savedBase.filter { it.label.contains(dropQuery, true) || it.host.contains(dropQuery, true) })
        .take(if (dropQuery.isEmpty()) 10 else 50)
    val recentBase = if (state.settings.hideRecentDupes)
        state.recent.filter { r -> state.saved.none { it.host.equals(r, ignoreCase = true) } }
    else state.recent
    val recentShown = if (dropQuery.isEmpty()) recentBase
        else recentBase.filter { it.contains(dropQuery, true) }

    // Snackbar text: a raw String (localized at the call site) or a resource
    // id from the ViewModel — resolved here, outside the suspend effect.
    val snackMsg = state.message ?: state.messageRes?.let { stringResource(it) }
    LaunchedEffect(snackMsg) {
        snackMsg?.let { snack.showSnackbar(it); vm.clearMessage() }
    }
    // WiFi List display: re-sort LIVE AP blocks for the active sort chip
    // (connected first, then RSSI/SSID/channel). Plain lines and Channel rows
    // keep their slots. Keys survive content updates so scroll can re-anchor.
    val displayLines = remember(
        state.lines, state.wifiSort, state.wifiCycles, state.wifiDisplay,
        state.tool, state.wifiConnBssid
    ) {
        if (state.tool == Tool.WIFIANALYZER && state.wifiDisplay == WifiAnalyzerRunner.DISPLAY_LIST) {
            WifiAnalyzerRunner.reorderApLines(state.lines, state.wifiSort, state.wifiConnBssid)
        } else state.lines
    }
    val displayKeyed = remember(displayLines) {
        val counts = mutableMapOf<String, Int>()
        displayLines.map { line ->
            val base = if (line.startsWith(GlobalpingRunner.LIVE)) line.substringBefore('\n')
            else "p:$line"
            val n = (counts[base] ?: 0) + 1
            counts[base] = n
            (if (n == 1) base else "$base#$n") to line
        }
    }
    // First-visible key from the previous layout pass (still valid when this
    // composition introduces a new displayKeyed). Re-sort keeps the viewport
    // on that AP instead of jumping to the new index-0 row.
    // derivedStateOf: layoutInfo flips on every layout pass — reading it
    // straight in composition would recompose the whole screen each frame.
    val preAnchorKey by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key }
    }
    LaunchedEffect(displayKeyed) {
        if (state.tool != Tool.WIFIANALYZER || state.wifiCycles == 0) return@LaunchedEffect
        val key = preAnchorKey ?: return@LaunchedEffect
        val idx = displayKeyed.indexOfFirst { it.first == key }
        if (idx >= 0 && idx != listState.firstVisibleItemIndex) {
            listState.scrollToItem(idx)
        }
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
    // The launcher callback is not composable: resolve the message here.
    val permDeniedMsg = stringResource(R.string.home_perm_denied)
    val wifiPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // getScanResults() needs a location permission on every API (OEMs
        // ignore neverForLocation + NEARBY alone); NEARBY is still required on
        // 33+. Android 12+ lets the user answer with approximate-only, so
        // COARSE counts as granted too — scans never need street precision.
        val loc = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val nearby = Build.VERSION.SDK_INT < 33 ||
            grants[Manifest.permission.NEARBY_WIFI_DEVICES] == true
        if (loc && nearby) {
            if (!isLocationEnabled(context)) {
                promptLocationSettings(context, vm, R.string.home_location_off)
            } else {
                vm.run()
            }
        } else {
            vm.setMessage(permDeniedMsg)
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
                    promptLocationSettings(context, vm, R.string.home_location_off)
                else -> vm.run()
            }
        } else {
            vm.run()
        }
    }
    // Countdown to the next WiFi scan cycle (ticks locally; lastRefreshAt
    // resets it after every completed cycle).
    var wifiCountdown by remember { mutableIntStateOf(0) }
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

    // Top bar docks at the top edge (default) or the bottom edge (Edit UI).
    // Insets follow the dock: top = status bar (default), bottom = nav bar +
    // keyboard, so the bottom dock never reserves status-bar height or sits
    // under the gesture pill (Scaffold leaves insets to the bar itself).
    val topBar: @Composable () -> Unit = {
            TopAppBar(
                // Like empty space elsewhere, the bar's empty area dismisses
                // the keyboard / target focus and closes the saved list —
                // taps on the buttons are consumed by them first (matters when
                // the bar docks at the bottom, under the focused target box).
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures {
                        focusManager.clearFocus()
                        if (state.dropExpanded) vm.setDrop(false)
                    }
                },
                title = { Text("izs NetTools") },
                windowInsets = if (state.settings.topBarBottom) {
                    WindowInsets.navigationBars.union(WindowInsets.ime)
                } else {
                    TopAppBarDefaults.windowInsets
                },
                actions = {
                    // Hide collapses the tool grid (taller terminal). When
                    // collapsed, this button shows the active tool and re-opens
                    // the grid on tap. Long press = quick tool switcher while
                    // collapsed (with the grid visible it IS the picker, and
                    // switching is blocked while running — same as the grid).
                    // One combinedClickable node (same pattern as the grid
                    // cells): tap and long-press never race a second detector.
                    var toolMenu by remember { mutableStateOf(false) }
                    Box {
                        Text(
                            text = if (state.hideToolGrid) state.tool.title
                                else stringResource(R.string.hide),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (state.hideToolGrid) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .combinedClickable(
                                    onClick = { vm.toggleToolGrid() },
                                    onLongClick = {
                                        if (state.hideToolGrid && !state.running) toolMenu = true
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                        DropdownMenu(
                            expanded = toolMenu,
                            onDismissRequest = { toolMenu = false }
                        ) {
                            // Enabled tools only, in the user's grid order —
                            // disabled-in-Settings tools stay unreachable here
                            // too. Selection mirrors a grid tap.
                            state.settings.orderedEnabledTools().forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.title) },
                                    trailingIcon = {
                                        if (t == state.tool) {
                                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.home_current))
                                        }
                                    },
                                    onClick = {
                                        toolMenu = false
                                        if (state.settings.autoRunOnTool) vm.selectAndRun(t)
                                        else vm.setTool(t)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenHosts) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.home_manage_saved))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_settings))
                    }
                }
            )
    }
    // Keyboard open: lift the content above the IME so the target box never
    // hides under it, wherever that box sits in the order — unless it is the
    // first section (top of the screen, out of the keyboard reach anyway, so
    // lifting would only waste terminal space). Also skipped when the bar is
    // docked at the bottom: its own windowInsets already grow the bar by the
    // keyboard, and padding here too would lift twice and hide the box.
    val targetTop = state.settings.uiSections.firstOrNull() == "target"
    Scaffold(
        topBar = { if (!state.settings.topBarBottom) topBar() },
        bottomBar = { if (state.settings.topBarBottom) topBar() },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            // Edit UI's View button arms this: tap to jump straight back to
            // the editor, then it clears until View is pressed again.
            if (state.editUiFab) {
                FloatingActionButton(onClick = {
                    vm.setEditUiFab(false)
                    onOpenEditUi()
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_ui))
                }
            }
        }
    ) { pad ->
        // Box hosts the saved-list overlay so it floats over the tool grid
        // instead of pushing it down (layout stays put on open/close).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                // Tighter on top only: less air between TopAppBar and the
                // target field; sides/bottom keep 8.dp with the column gap.
                .padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 8.dp)
                .then(if (!targetTop && !state.settings.topBarBottom) Modifier.imePadding() else Modifier)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        // Tap on empty space (console, hint, spacers) dismisses
                        // the saved list. Clickable children consume their own
                        // taps, so setTool/run/stop close it on their side.
                        if (state.dropExpanded) vm.setDrop(false)
                    })
                },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Sections (Edit UI order) --------------------------------
            // Each lambda holds the exact pre-refactor block; only the
            // sequence below follows the user's order (Settings > Edit UI).
            // Recent/saved picker side (Edit UI): "top" flows the card above
            // the target box, "bottom" (default) keeps it below.
            val savedListTop = state.settings.savedListPos == "top"
            // Picker body shared by both positions: Recent first, Saved below.
            val savedListBody: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Each list is one block so Edit UI can swap their
                        // order (recent first by default).
                        val recentBlock: @Composable () -> Unit = {
                        if (state.settings.maxRecent > 0) {
                            Text(
                                stringResource(R.string.home_recent),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            if (recentShown.isEmpty()) {
                                Text(
                                    if (dropQuery.isEmpty()) stringResource(R.string.home_no_history)
                                    else stringResource(R.string.home_no_recent_match, dropQuery),
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
                        val savedBlock: @Composable () -> Unit = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.home_saved),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            if (state.target.isNotBlank()) {
                                TextButton(
                                    onClick = { vm.toggleSave() },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        if (state.isTargetSaved) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (state.isTargetSaved) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (state.isTargetSaved) stringResource(R.string.home_remove)
                                        else stringResource(R.string.save),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        if (savedShown.isEmpty()) {
                            Text(
                                if (dropQuery.isEmpty()) stringResource(R.string.home_saved_empty)
                                else stringResource(R.string.home_no_saved_match, dropQuery),
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
                        }
                        if (state.settings.savedListOrder == "saved") {
                            savedBlock()
                            recentBlock()
                        } else {
                            recentBlock()
                            savedBlock()
                        }
                    }
            }
            val targetSection: @Composable ColumnScope.() -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // The picker overflows this block; zIndex lifts the whole
                    // section over whatever follows it in the order.
                    .zIndex(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            if (savedListTop && state.dropExpanded) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    savedListBody()
                }
            }
            Box(modifier = Modifier.fillMaxWidth()) {
            // --- Target bar: single unified search bar ---
            // My IP / Neighbor ignore the target: show the label and hide
            // any value left over from another tool (kept in state, restored
            // when switching back to a tool that needs it).
            // label (not placeholder): floats onto the border when focused or
            // filled, same as Settings fields; height pinned so the min-height
            // never grows past the compact look.
            val targetHidden = state.tool == Tool.MYIP || state.tool == Tool.NEIGHBOR
            OutlinedTextField(
                value = if (targetHidden) "" else state.target,
                onValueChange = { if (!targetHidden) vm.setTarget(it) },
                readOnly = targetHidden,
                label = {
                    Text(
                        stringResource(
                            when (state.tool) {
                                Tool.PING -> R.string.target_hint_ip_host
                                Tool.DIG -> R.string.target_hint_domain_name
                                Tool.TRACE -> R.string.target_hint_ip_host
                                Tool.WHOIS -> R.string.target_hint_domain_ip
                                Tool.IPINFO -> R.string.target_hint_ip_host
                                Tool.MYIP -> R.string.target_hint_no_target
                                Tool.HEADERS -> R.string.target_hint_url_host
                                Tool.PORTS -> R.string.target_hint_ip_host_port
                                Tool.CERT -> R.string.target_hint_host_port
                                Tool.LOOP -> R.string.target_hint_ip_host_gw
                                Tool.NEIGHBOR -> R.string.target_hint_no_target
                                Tool.SWEEP -> R.string.target_hint_ip_range
                                Tool.WIFIANALYZER -> R.string.target_hint_ssid_mac
                            }
                        )
                    )
                },
                singleLine = true,
                leadingIcon = {
                    if (state.target.isEmpty() || targetHidden) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    } else {
                        // Clear doubles as the leading icon: no extra trailing
                        // button; focus stays so the keyboard remains open.
                        IconButton(onClick = { vm.setTarget("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.home_clear_target))
                        }
                    }
                },
                trailingIcon = {
                    IconButton(onClick = { vm.setDrop(!state.dropExpanded) }, enabled = !targetHidden) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.home_saved_list))
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
                // Pin the height: M3's default min-height for a label field is
                // taller than this bar used to be — don't let it creep back.
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )

            // --- Saved + recent picker: side picked in Edit UI ---
            // Bottom (default): anchored under the field (+ 8.dp column gap),
            // the wrapper Box's zIndex keeps it above the sections below.
            if (!savedListTop && state.dropExpanded) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(top = 64.dp)
                        .zIndex(1f)
                ) {
                    savedListBody()
                }
            }
            }
            }
            }
            val toolsSection: @Composable ColumnScope.() -> Unit = {
            // --- Tool selector: exactly 2 rows, divider-separated, no boxes.
            // Tap = select (+ auto-run when enabled, except IP Scan and Loop).
            // Long-press a server tool = change its server; hold Loop = pick mode.
            // Hidden via top-bar Hide → more terminal height; button shows tool name.
            var serverTool by remember { mutableStateOf<Tool?>(null) }
            var scopeTool by remember { mutableStateOf<Tool?>(null) }
            var loopModeTool by remember { mutableStateOf<Tool?>(null) }
            // --- Tool descriptions (hint texts): Edit UI picks top/bottom/hide.
            // Each hint names the tool's job first, then the usage shortcut.
            val toolHints: @Composable ColumnScope.() -> Unit = {
            if (state.tool == Tool.PING) {
                Text(
                    stringResource(R.string.hint_ping),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.DIG) {
                Text(
                    stringResource(R.string.hint_dig),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.WHOIS) {
                Text(
                    stringResource(R.string.hint_whois),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.TRACE) {
                Text(
                    (if (state.traceGlobal)
                        stringResource(R.string.hint_trace_global, state.globalProbes)
                    else pluralStringResource(
                        R.plurals.hint_trace_local, state.settings.maxHops,
                        state.settings.maxHops
                    )),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.LOOP) {
                Text(
                    stringResource(R.string.hint_loop),
                    style = MaterialTheme.typography.bodySmall
                )
                // Verdict banners: pop in when the Loop run finishes (or stops
                // early on a proven L3 loop). Clean runs stay console-only.
                state.stormVerdict?.let { StormVerdictBanner(it) }
                state.loopVerdict?.let { LoopVerdictBanner(it) }
            }
            if (state.tool == Tool.CERT) {
                Text(
                    stringResource(R.string.hint_cert),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.MYIP) {
                Text(
                    stringResource(R.string.hint_myip),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.NEIGHBOR) {
                Text(
                    stringResource(R.string.hint_neighbor),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.IPINFO) {
                Text(
                    stringResource(R.string.hint_ipinfo),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.PORTS) {
                Text(
                    stringResource(R.string.hint_ports),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.HEADERS) {
                Text(
                    stringResource(R.string.hint_headers),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.SWEEP) {
                Text(
                    stringResource(R.string.hint_sweep),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.tool == Tool.WIFIANALYZER) {
                Text(
                    stringResource(
                        R.string.hint_wifi,
                        WifiAnalyzerRunner.REFRESH_MS / 1000
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            }
            val toolExtra: @Composable ColumnScope.() -> Unit = {
            if (state.tool == Tool.DIG) {
                val digHeader: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.home_record_type),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            stringResource(R.string.home_dns_server, state.settings.dnsServer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                val digBody: @Composable () -> Unit = {
                    OptionRow(
                        options = DnsRunner.types.map { it to it },
                        selected = state.digType,
                        onSelect = { vm.setDigType(it) }
                    )
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.settings.toolExtraHeader == "top") {
                            digHeader()
                            digBody()
                        } else {
                            digBody()
                            digHeader()
                        }
                    }
                }
            }
            if (state.tool == Tool.WIFIANALYZER) {
                // Filters as a Settings-style 2-row block: dimension tabs on
                // top, values for the active dimension below — fixed height
                // no matter how many filter kinds exist. Display (rightmost)
                // = List/Channel + "Sort:" + RSSI/SSID/Ch + Rows: 2/3 in one
                // horizontally scrollable row — Rows sits at the far right, so
                // on narrow screens it stays out of view until scrolled
                // (rarely changed); Channel always sorts by channel no.
                // SSIDs stay free text in the target bar (names are too
                // random to enumerate).
                var wifiFilterDim by remember { mutableIntStateOf(0) }
                val wifiHeader: @Composable () -> Unit = {
                PrimaryTabRow(
                    selectedTabIndex = wifiFilterDim,
                    containerColor = CardDefaults.cardColors().containerColor
                ) {
                    listOf(
                        R.string.wifi_tab_band, R.string.wifi_tab_channel,
                        R.string.wifi_tab_security, R.string.wifi_tab_display
                    ).forEachIndexed { i, nameRes ->
                        Tab(
                            selected = wifiFilterDim == i,
                            onClick = { wifiFilterDim = i },
                            modifier = Modifier.height(36.dp),
                            text = {
                                Text(
                                    stringResource(nameRes),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
                }
                val wifiBody: @Composable () -> Unit = {
                when (wifiFilterDim) {
                    0 -> WifiMultiOptionRow(
                        options = listOf("2.4" to "2.4 GHz", "5" to "5 GHz", "6" to "6 GHz"),
                        selected = state.wifiBand,
                        onToggle = vm::toggleWifiBand,
                        onSelectAll = vm::selectAllWifiBands
                    )
                    1 -> {
                        val chans = listOf(-1) + state.wifiChannels
                        OptionRow(
                            options = chans.map { c ->
                                c to (if (c == -1) stringResource(R.string.wifi_all) else "$c")
                            },
                            selected = state.wifiChannel,
                            onSelect = vm::setWifiChannel
                        )
                    }
                    2 -> WifiMultiOptionRow(
                        options = listOf(
                            "WPA3" to "WPA3", "WPA2" to "WPA2",
                            "WPA" to "WPA", "WEP" to "WEP", "open" to "open"
                        ),
                        selected = state.wifiSecurity,
                        onToggle = vm::toggleWifiSecurity
                    )
                    else -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            WifiAnalyzerRunner.DISPLAY_LIST to stringResource(R.string.wifi_display_list),
                            WifiAnalyzerRunner.DISPLAY_CHANNEL to stringResource(R.string.wifi_display_channel)
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = value == state.wifiDisplay,
                                onClick = { vm.setWifiDisplay(value) },
                                label = {
                                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.wifi_sort),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf(
                            WifiAnalyzerRunner.SORT_RSSI to "RSSI",
                            WifiAnalyzerRunner.SORT_SSID to "SSID",
                            WifiAnalyzerRunner.SORT_CHANNEL to "Ch"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = value == state.wifiSort,
                                onClick = { vm.setWifiSort(value) },
                                label = {
                                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                        // Rows last = far right of the scroll content: rarely
                        // changed, so it stays off-screen on narrow displays.
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.wifi_rows),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf(
                            WifiAnalyzerRunner.ROWS_2 to "2",
                            WifiAnalyzerRunner.ROWS_3 to "3"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = value == state.wifiRows,
                                onClick = { vm.setWifiRows(value) },
                                label = {
                                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (state.settings.toolExtraHeader == "top") {
                            wifiHeader()
                            wifiBody()
                        } else {
                            wifiBody()
                            wifiHeader()
                        }
                    }
                }
            }
            }
            if (state.settings.toolExtraPos == "top") toolExtra()
            if (state.settings.toolDescPos == "top") toolHints()
            if (!state.hideToolGrid) {
                ToolSelector(
                    selected = state.tool,
                    enabled = !state.running,
                    settings = state.settings,
                    rows = state.settings.toolGridRows,
                    extraSub = { t ->
                        when (t) {
                            Tool.PING -> if (state.pingGlobal)
                                globalSub(state.globalProbes, state.globalCountry)
                            else stringResource(R.string.home_local)
                            Tool.TRACE -> if (state.traceGlobal)
                                globalSub(state.globalProbes, state.globalCountry)
                            else stringResource(R.string.home_local)
                            Tool.PORTS -> if (state.portsGlobal)
                                stringResource(R.string.home_global)
                            else stringResource(R.string.home_local)
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
                    portList = if (t == Tool.PORTS) state.settings.portList else null,
                    onSave = { g, n, c, ports ->
                        when (t) {
                            Tool.PING -> vm.setPingGlobal(g)
                            Tool.TRACE -> vm.setTraceGlobal(g)
                            else -> vm.setPortsGlobal(g)
                        }
                        vm.setGlobalProbes(n)
                        vm.setGlobalCountry(c)
                        if (ports != null && ports != state.settings.portList) vm.setPortList(ports)
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
            if (state.settings.toolExtraPos == "bottom") toolExtra()
            if (state.settings.toolDescPos == "bottom") toolHints()
            }
            val terminalSection: @Composable ColumnScope.() -> Unit = {
            // --- Output console: dark terminal panel ---
            Card(
                colors = CardDefaults.cardColors(containerColor = term.bg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    // Terminal toolbar: Run/Stop · progress · font · Clear.
                    // Edit UI picks above or below the output — same row.
                    val termToolbar: @Composable () -> Unit = {
                        // Run and Clear sit at the two ends — Edit UI picks the side.
                        val runRight = state.settings.runPos == "right"
                        val runBtn: @Composable () -> Unit = {
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
                                    if (state.running) stringResource(R.string.home_stop)
                                    else stringResource(R.string.home_run),
                                    color = if (state.running) term.green else term.text
                                )
                            }
                        }
                        val clearBtn: @Composable () -> Unit = {
                            TextButton(onClick = { vm.clearOutput() }) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = term.text
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.home_clear), color = term.text)
                            }
                        }
                        // Groups so Run can sit on either end: status hugs Run,
                        // Clear/font/sort hug the other end (Edit UI picks the side).
                        val statusGroup: @Composable RowScope.() -> Unit = {
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
                            // WiFi Analyzer: spinner while a scan + cache-grace
                            // window is open (first cycle or manual "next" tap),
                            // countdown to the next cycle once rows land.
                            // Intrinsic width only — a weight slot here clips
                            // "next 30s" down to "next 9s"-length space.
                            // Tap countdown = refresh now (wakes the cycle early).
                            if (state.tool == Tool.WIFIANALYZER && state.running) {
                                Spacer(Modifier.width(8.dp))
                                if (state.wifiScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = term.green
                                    )
                                } else if (wifiCountdown > 0) {
                                    Text(
                                        stringResource(R.string.home_next_s, wifiCountdown),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = term.green,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .clickable { vm.refreshWifiNow() }
                                            .padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                        val sortBtn: @Composable RowScope.() -> Unit = {
                            // Ports only, only after a finished scan: manual
                            // re-sort with open lines above closed ones.
                            if (state.tool == Tool.PORTS && !state.running &&
                                state.lines.any { l -> l.startsWith("OPEN ") || l.startsWith("closed ") }
                            ) {
                                TextButton(onClick = { vm.sortOutputOpenFirst() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = term.text
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.home_sort), color = term.text)
                                }
                            }
                        }
                        val fontBtns: @Composable RowScope.() -> Unit = {
                            IconButton(onClick = { vm.bumpFont(-1f) }) {
                                Icon(Icons.Filled.TextDecrease, contentDescription = stringResource(R.string.home_smaller_text), tint = term.text)
                            }
                            IconButton(onClick = { vm.bumpFont(1f) }) {
                                Icon(Icons.Filled.TextIncrease, contentDescription = stringResource(R.string.home_bigger_text), tint = term.text)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!runRight) {
                                runBtn()
                                statusGroup()
                                Spacer(Modifier.weight(1f))
                                sortBtn()
                                fontBtns()
                                clearBtn()
                            } else {
                                clearBtn()
                                fontBtns()
                                sortBtn()
                                Spacer(Modifier.weight(1f))
                                statusGroup()
                                runBtn()
                            }
                        }
                        }
                        if (state.settings.runRowTop) {
                            termToolbar()
                            Spacer(Modifier.height(4.dp))
                        }
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(displayKeyed, key = { it.first }) { (_, raw) ->
                                    // WiFi AP blocks get bottom margin so each
                                    // SSID+MAC pair is a distinct visual group.
                                    val shown = GlobalpingRunner.displayOf(raw)
                                    val isApBlock = shown.split('\n').let { ls ->
                                        ls.size >= 2 && wifiMacLine.matches(ls[1].trim())
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
                        if (!state.settings.runRowTop) {
                            Spacer(Modifier.height(4.dp))
                            termToolbar()
                        }
                    }
            }
            }
            state.settings.uiSections.forEach { id ->
                when (id) {
                    "target" -> targetSection()
                    "tools" -> toolsSection()
                    "terminal" -> terminalSection()
                    else -> {}
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
                    fontFamily = TermMono,
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
                    contentDescription = stringResource(R.string.home_remove),
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
            title = stringResource(R.string.dlg_loop_detected)
            detail = verdict.message
                .removePrefix("LOOP DETECTED: ")
                .removeSuffix(" (routing loop suspected)")
                .removeSuffix(" (routing loop confirmed)")
        }
        is LoopResult.Suspected -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer
            icon = Icons.Filled.Warning
            title = stringResource(R.string.dlg_loop_possible)
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
                    fontFamily = TermMono,
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
            title = stringResource(R.string.dlg_storm_detected)
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
            title = stringResource(R.string.dlg_storm_possible)
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
                    fontFamily = TermMono,
                    color = onContainer
                )
            }
        }
    }
}

/**
 * Tool selector: items separated by thin divider lines — no boxes, no chips.
 * Tap = run immediately.
 * Long-press a server-backed tool (Dig/Whois/IP Info/My IP) = change server.
 * [rows] 2 (default) = the classic two-row split; 1 = one visible row in
 * swipeable pages of 5 (Settings → Tools → "Home grid rows").
 */
@Composable
private fun ToolSelector(
    selected: Tool,
    enabled: Boolean,
    settings: AppSettings,
    rows: Int,
    extraSub: @Composable (Tool) -> String?,
    onSelect: (Tool) -> Unit,
    onLongPress: (Tool) -> Unit
) {
    val tools = settings.orderedEnabledTools()
    if (tools.isEmpty()) return
    if (rows == 1) {
        // Fixed pages of 5, snap per swipe (no free scrolling). 5 or fewer
        // tools = a single static row, no pager needed.
        val pageSize = 5
        val pages = (tools.size + pageSize - 1) / pageSize
        if (pages <= 1) {
            ToolSelectorRow(tools, selected, enabled, settings, extraSub, onSelect, onLongPress)
        } else {
            val pagerState = rememberPagerState { pages }
            HorizontalPager(state = pagerState) { page ->
                ToolSelectorRow(
                    tools.drop(page * pageSize).take(pageSize),
                    selected, enabled, settings, extraSub, onSelect, onLongPress
                )
            }
        }
        return
    }
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

/** Server backing for the tools that have one; null = long-press does nothing.
 *  [labelRes] is a resource id (non-composable, resolved at the dialog title). */
private data class ServerSlot(
    val labelRes: Int,
    val current: String,
    val presets: List<Pair<String, String>>
)

private fun toolServerSlot(tool: Tool, s: AppSettings): ServerSlot? = when (tool) {
    Tool.DIG -> ServerSlot(R.string.dlg_slot_dns, s.dnsServer, DnsPresets.all)
    Tool.WHOIS -> ServerSlot(R.string.dlg_slot_whois, s.whoisServer, WhoisPresets.all)
    Tool.IPINFO -> ServerSlot(R.string.dlg_slot_ip_lookup, s.ipLookupBase, IpInfoPresets.lookup)
    Tool.MYIP -> ServerSlot(R.string.dlg_slot_myip, s.myIpBase, IpInfoPresets.myIp)
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
    extraSub: @Composable (Tool) -> String?,
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
                    // the subtitle. The wrapped title halves its line height so
                    // the cell measures exactly like every title+sub cell
                    // (2 × 18sp = 20sp + 16sp) — every row and pager page keeps
                    // the same height, matching the shorter all-title row.
                    val twoLineTitle = t.title.length > 11
                    Text(
                        t.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = if (twoLineTitle) 2 else 1,
                        lineHeight = if (twoLineTitle) 18.sp else TextUnit.Unspecified
                    )
                    if (!twoLineTitle) {
                        // Short titles keep the subtitle so every cell stays
                        // uniformly 2 lines tall (nbsp placeholder when none).
                        Text(
                            server ?: " ",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            lineHeight = 16.sp,
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
@Composable
private fun globalSub(probes: Int, country: String): String =
    if (country.isEmpty()) stringResource(R.string.home_global_x, probes)
    else stringResource(R.string.home_global_x_country, probes, country)

/**
 * Runtime grant check for the WiFi Analyzer scan. A location permission is
 * required on every API (several OEMs still reject getScanResults with NEARBY
 * alone) — FINE or COARSE, since Android 12 lets the user grant approximate
 * only; NEARBY is also required on 33+ per the platform contract.
 */
private fun hasWifiScanPermission(context: Context): Boolean {
    val loc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!loc) return false
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

/** Location is off: explain + jump to the system toggle (it cannot be flipped in-app).
 *  [messageRes] is a resource id — this runs outside any composable. */
private fun promptLocationSettings(context: Context, vm: NetToolsViewModel, messageRes: Int) {
    vm.setMessage(context.getString(messageRes))
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
private fun <T> OptionRow(
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

/** Multi-select chip strip (Band / Security): each item toggles on/off.
 *  [onSelectAll] (Band only) pins a "Select All" action at the far right —
 *  chips stay scrollable on the left. */
@Composable
private fun WifiMultiOptionRow(
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = true)
                .horizontalScroll(rememberScrollState())
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = value in selected,
                    onClick = { onToggle(value) },
                    label = {
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    },
                    modifier = Modifier.height(28.dp)
                )
            }
        }
        if (onSelectAll != null) {
            TextButton(
                onClick = onSelectAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    stringResource(R.string.wifi_select_all),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
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
    portList: String? = null,
    onSave: (Boolean, Int, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var global by remember { mutableStateOf(isGlobal) }
    var n by remember { mutableIntStateOf(probes) }
    var c by remember { mutableStateOf(country) }
    var custom by remember { mutableStateOf(country) }
    var draftList by remember(portList) { mutableStateOf(portList) }
    val localLabel = when (tool) {
        Tool.PING -> stringResource(R.string.dlg_local_device)
        Tool.TRACE -> stringResource(R.string.dlg_local_system)
        else -> stringResource(R.string.dlg_local_tcp)
    }
    val globalLabel = if (simple) stringResource(R.string.dlg_global_shodan)
    else stringResource(R.string.dlg_global_probes)
    // Same localized text the "Custom" chip shows — compared against it below,
    // never against a hardcoded English literal.
    val customLabel = stringResource(R.string.dlg_custom)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dlg_tool_source, tool.title)) },
        text = {
            // Fixed header (source, probes, custom field); only the
            // country preset list below scrolls.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(false to localLabel, true to globalLabel).forEachIndexed { idx, (g, name) ->
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
                    // Right under Local: how much to scan. Presets fill the
                    // draft; Custom restores the original list (shown selected
                    // whenever the list matches no preset, e.g. preset + a few
                    // manual ports added in Settings). Save writes the draft
                    // back to the Settings port list.
                    if (idx == 0 && portList != null && !global) {
                        Text(
                            stringResource(R.string.dlg_port_list),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        val items = PortChecker.scanPresets.map { it.key to it.value } +
                            (customLabel to portList)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items.chunked(2).forEach { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    row.forEach { (label, value) ->
                                        val isCustom = label == customLabel
                                        FilterChip(
                                            selected = if (isCustom) {
                                                draftList !in PortChecker.scanPresets.values
                                            } else draftList == value,
                                            onClick = { draftList = value },
                                            label = {
                                                Text(
                                                    label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (global && !simple) {
                    Text(
                        stringResource(R.string.dlg_probes),
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
                        stringResource(R.string.dlg_probe_location),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.trim().uppercase().take(2); c = custom },
                        label = { Text(stringResource(R.string.dlg_country_code)) },
                        placeholder = { Text(stringResource(R.string.dlg_country_example)) },
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
            TextButton(onClick = { onSave(global, n, c, draftList); onDismiss() }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
        title = { Text(stringResource(R.string.dlg_loop_mode)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LoopRunner.LoopMode.entries.forEach { m ->
                    val hint = when (m) {
                        LoopRunner.LoopMode.BOTH -> stringResource(R.string.dlg_loop_both)
                        LoopRunner.LoopMode.L2_ONLY -> stringResource(R.string.dlg_loop_l2)
                        LoopRunner.LoopMode.L3_ONLY -> stringResource(R.string.dlg_loop_l3)
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
            TextButton(onClick = { onSave(picked); onDismiss() }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
        title = {
            Text(
                stringResource(R.string.dlg_tool_slot, tool.title, stringResource(slot.labelRes))
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.trim(); picked = custom },
                    label = { Text(stringResource(R.string.dlg_custom)) },
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
                                fontFamily = TermMono,
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
            TextButton(onClick = { onSave(picked.trim()); onDismiss() }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
