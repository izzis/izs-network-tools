package id.web.izs.nettools.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.web.izs.nettools.core.CertChecker
import id.web.izs.nettools.core.DnsRunner
import id.web.izs.nettools.core.GlobalpingRunner
import id.web.izs.nettools.core.HttpHeadersFetcher
import id.web.izs.nettools.core.IpScan
import id.web.izs.nettools.core.InternetDbClient
import id.web.izs.nettools.core.IpInfoClient
import id.web.izs.nettools.core.PingRunner
import id.web.izs.nettools.core.PortChecker
import id.web.izs.nettools.core.TargetParser
import id.web.izs.nettools.core.TraceRunner
import id.web.izs.nettools.core.WhoisRdapClient
import id.web.izs.nettools.data.SettingsRepository
import id.web.izs.nettools.model.AppSettings
import id.web.izs.nettools.model.SavedHost
import id.web.izs.nettools.model.SavedSort
import id.web.izs.nettools.model.Tool
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeUiState(
    val target: String = "",
    val tool: Tool = Tool.PING,
    val digType: String = "A",
    val pingGlobal: Boolean = false,
    val traceGlobal: Boolean = false,
    val portsGlobal: Boolean = false,
    val globalProbes: Int = 10,
    val globalCountry: String = "",
    val lines: List<String> = emptyList(),
    val running: Boolean = false,
    val progress: String? = null,
    val startedAt: Long = 0L,
    val message: String? = null,
    val settings: AppSettings = AppSettings(),
    val settingsLoaded: Boolean = false,
    val saved: List<SavedHost> = emptyList(),
    val recent: List<String> = emptyList(),
    val dropExpanded: Boolean = false,
    val isTargetSaved: Boolean = false
)

class NetToolsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch { repo.settings.collect { s ->
            _state.update { it.copy(settings = s, settingsLoaded = true) }
            // Shrink a legacy long recent list to the current limit.
            repo.trimRecent(s.maxRecent)
        } }
        viewModelScope.launch { repo.savedHosts.collect { l -> _state.update { it.copy(saved = l) } } }
        viewModelScope.launch { repo.recentHosts.collect { l -> _state.update { it.copy(recent = l) } } }
        // Restore the last ran target once at startup. Fresh install has none,
        // so the bar stays empty; never overwrite text the user already typed.
        viewModelScope.launch {
            val last = repo.lastTarget.first()
            if (last.isNotEmpty() && _state.value.target.isEmpty()) setTarget(last)
        }
    }

    fun setTarget(v: String) {
        _state.update { it.copy(target = v, message = null) }
        viewModelScope.launch {
            _state.update { it.copy(isTargetSaved = repo.isSaved(v)) }
        }
    }

    fun setTool(t: Tool) {
        _state.update { it.copy(tool = t) }
        // IP Scan: autofill the target bar with your own /24 network.
        if (t == Tool.SWEEP && _state.value.target.isBlank()) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                IpScan.ownNetwork()?.let { own ->
                    _state.update { s -> if (s.target.isBlank()) s.copy(target = "${own.base24}.0/24") else s }
                }
            }
        }
    }

    /** Tap a tool = run it immediately (except IP Scan: too heavy to trigger
     *  by accident, it needs an explicit Run press). Tapping again re-runs. */
    fun selectAndRun(t: Tool) {
        if (_state.value.running) return
        setTool(t)
        if (t == Tool.SWEEP) return
        run()
    }
    fun setDigType(t: String) {
        _state.update { it.copy(digType = t) }
        // Switching record type re-runs Dig immediately, no need to tap again.
        if (_state.value.tool == Tool.DIG) {
            stop()
            run()
        }
    }
    /** Global scope is per-session (like Dig's record type), not saved. */
    fun setPingGlobal(g: Boolean) = _state.update { it.copy(pingGlobal = g) }
    fun setTraceGlobal(g: Boolean) = _state.update { it.copy(traceGlobal = g) }
    /** Global Ports (Shodan InternetDB) is per-session, default local. */
    fun setPortsGlobal(g: Boolean) = _state.update { it.copy(portsGlobal = g) }
    fun setGlobalProbes(n: Int) = _state.update { it.copy(globalProbes = n) }
    fun setGlobalCountry(c: String) = _state.update { it.copy(globalCountry = c.trim().uppercase().take(2)) }
    fun setDrop(e: Boolean) = _state.update { it.copy(dropExpanded = e) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun pickTarget(host: String) {
        setTarget(host)
        setDrop(false)
        viewModelScope.launch {
            repo.touchSaved(host)
            if (_state.value.settings.autoRunOnPick) run()
        }
    }

    fun toggleSave(label: String = "") {
        val host = _state.value.target.trim()
        if (host.isEmpty()) {
            _state.update { it.copy(message = "Enter a target first before saving") }
            return
        }
        viewModelScope.launch {
            if (repo.isSaved(host)) {
                _state.value.saved.firstOrNull { it.host.equals(host, ignoreCase = true) }?.let {
                    repo.deleteSaved(it.id)
                }
                _state.update { it.copy(isTargetSaved = false, message = "Removed from saved") }
            } else {
                val ok = repo.addSaved(label, host)
                _state.update {
                    it.copy(
                        isTargetSaved = ok,
                        message = if (ok) "Saved — just pick it next time" else "Already in saved"
                    )
                }
            }
        }
    }

    fun clearOutput() = _state.update { it.copy(lines = emptyList()) }

    fun bumpFont(deltaSp: Float) {
        val next = (_state.value.settings.outputFontSp + deltaSp).coerceIn(9f, 22f)
        viewModelScope.launch { repo.saveSettings(_state.value.settings.copy(outputFontSp = next)) }
    }

    fun deleteSaved(id: Long) {
        viewModelScope.launch { repo.deleteSaved(id) }
    }

    fun setSavedSort(mode: SavedSort) {
        val cur = _state.value.settings
        if (cur.savedSort == mode.name) return
        viewModelScope.launch { repo.saveSettings(cur.copy(savedSort = mode.name)) }
    }

    /** Long-press on a tool cell: swap that tool's server/provider. */
    fun setToolServer(tool: Tool, value: String) {
        val v = value.trim()
        if (v.isEmpty()) return
        val cur = _state.value.settings
        val next = when (tool) {
            Tool.DIG -> cur.copy(dnsServer = v)
            Tool.WHOIS -> cur.copy(whoisServer = v)
            Tool.IPINFO -> cur.copy(ipLookupBase = v)
            Tool.MYIP -> cur.copy(myIpBase = v)
            else -> return
        }
        viewModelScope.launch { repo.saveSettings(next) }
    }

    fun setCustomColor(role: String, hex: String) {
        viewModelScope.launch {
            val updated = _state.value.settings.customColors + (role to hex)
            repo.saveCustomColors(updated)
        }
    }

    fun clearCustomColor(role: String) {
        viewModelScope.launch {
            repo.saveCustomColors(_state.value.settings.customColors - role)
        }
    }

    fun resetCustomColors() {
        viewModelScope.launch {
            repo.saveCustomColors(emptyMap())
            repo.saveSchemeName("")
        }
    }

    /** Save the current working colors under [name]. Same name = overwrite, new name = new scheme. */
    fun saveScheme(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            val s = _state.value.settings
            repo.saveColorSchemes(s.colorSchemes + (n to s.customColors))
            repo.saveSchemeName(n)
        }
    }

    fun applyScheme(name: String) {
        val colors = _state.value.settings.colorSchemes[name] ?: return
        viewModelScope.launch {
            repo.saveCustomColors(colors)
            repo.saveSchemeName(name)
        }
    }

    fun deleteScheme(name: String) {
        viewModelScope.launch {
            repo.saveColorSchemes(_state.value.settings.colorSchemes - name)
            if (_state.value.settings.schemeName == name) repo.saveSchemeName("")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false, progress = null) }
    }

    fun run() {
        val st = _state.value
        val rawTarget = st.target.trim()
        // My IP and LAN sweep work without a target.
        if (st.tool != Tool.MYIP && st.tool != Tool.SWEEP && rawTarget.isEmpty()) {
            _state.update { it.copy(message = "Enter a target first (IP / host)") }
            return
        }
        if (st.running) return
        val parsed = TargetParser.parse(rawTarget)
        if (st.tool != Tool.MYIP && st.tool != Tool.SWEEP && parsed.host.isEmpty()) {
            _state.update { it.copy(message = "Invalid target") }
            return
        }
        job?.cancel()
        val s = st.settings
        // Port list parsed once: the count goes into the header, the list into the run.
        val portList = PortChecker.parsePorts(s.portList)
        val backend = when (st.tool) {
            Tool.PING -> if (st.pingGlobal) "globalping x${st.globalProbes}" + globalWhere(st)
                else "system ping " + (if (s.pingCount > 0) "x${s.pingCount}" else "nonstop")
            Tool.DIG -> "dnsjava ${st.digType} via ${s.dnsServer}"
            Tool.TRACE -> if (st.traceGlobal) "globalping trace x${st.globalProbes}" + globalWhere(st)
                else "system traceroute if present, else TTL-ping"
            Tool.WHOIS -> "RDAP + WHOIS port 43"
            Tool.IPINFO -> s.ipLookupBase
            Tool.MYIP -> s.myIpBase
            Tool.PORTS -> if (st.portsGlobal) "internetdb"
                else if (parsed.port != null) "TCP connect" else "TCP connect ${portList.size} ports"
            Tool.CERT -> "TLS handshake"
            Tool.HEADERS -> "HTTP GET"
            Tool.SWEEP -> "ping sweep"
        }
        // The blue "== ... ==" line is the single intro: it already carries tool,
        // target, backend and time, so per-runner echo lines are dropped in collect().
        // Heads-up extras that don't fit elsewhere are merged here: Dig record type
        // (backend), port count (backend), Cert/Ports :port (target), full URL (Headers).
        val portSuffix =
            if ((st.tool == Tool.CERT || st.tool == Tool.PORTS) && parsed.port != null) ":${parsed.port}" else ""
        val headerTarget = when (st.tool) {
            Tool.SWEEP -> rawTarget.ifEmpty { "auto /24" }
            Tool.HEADERS -> rawTarget.ifEmpty { "this device" }
            Tool.MYIP -> "this device"
            else -> parsed.host.ifEmpty { "this device" } + portSuffix
        }
        val header = "== ${st.tool.title} $headerTarget [via $backend] " +
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) + " =="
        _state.update { it.copy(lines = ((if (s.autoClearOutput) emptyList() else it.lines) + header).takeLast(2000), running = true, progress = "Starting...", startedAt = System.currentTimeMillis()) }
        if (st.tool != Tool.SWEEP && parsed.host.isNotEmpty()) viewModelScope.launch { repo.pushRecent(parsed.host, _state.value.settings.maxRecent) }
        // Remember the used target for the next startup. My IP ignores the
        // target bar, so it never overwrites; empty sweep keeps the old one.
        if (st.tool != Tool.MYIP && rawTarget.isNotEmpty()) viewModelScope.launch { repo.saveLastTarget(rawTarget) }

        val onProgress: (String) -> Unit = { msg -> _state.update { it.copy(progress = msg) } }
        val flow = when (st.tool) {
            Tool.PING -> if (st.pingGlobal) GlobalpingRunner.ping(parsed.host, st.globalProbes, st.globalCountry, s.globalpingToken, onProgress)
                else PingRunner.ping(parsed.host, s.pingCount)
            Tool.DIG -> DnsRunner.lookup(parsed.host, st.digType, s.dnsServer, s.timeoutMs)
            Tool.TRACE -> if (st.traceGlobal) GlobalpingRunner.trace(parsed.host, st.globalProbes, st.globalCountry, s.globalpingToken, onProgress)
                else TraceRunner.traceroute(parsed.host, s.maxHops, onProgress)
            Tool.WHOIS -> WhoisRdapClient.lookup(parsed.host, s.rdapBase, s.whoisServer, s.whoisPort, s.timeoutMs)
            Tool.IPINFO -> IpInfoClient.lookup(parsed.host, s.ipLookupBase, s.ipinfoToken)
            Tool.MYIP -> IpInfoClient.lookup("", s.myIpBase, s.ipinfoToken)
            Tool.PORTS -> if (st.portsGlobal) InternetDbClient.lookup(parsed.host)
                else PortChecker.check(parsed.host, parsed.port, s.timeoutMs, portList)
            Tool.CERT -> CertChecker.fetch(parsed.host, parsed.port ?: 443, s.timeoutMs)
            Tool.HEADERS -> HttpHeadersFetcher.fetch(rawTarget, s.timeoutMs)
            Tool.SWEEP -> IpScan.sweep(rawTarget, s.timeoutMs, onProgress, s.maxParallel, s.scanShowOffline)
        }
        job = viewModelScope.launch {
            flow.catch { e -> _state.update { it.copy(lines = it.lines + "ERROR: ${e.message}") } }
                .collect { line ->
                    if (isEchoLine(st.tool, st, line)) return@collect
                    _state.update { cur ->
                        // Live-update lines ("key\ntext") replace the earlier
                        // line with the same key in place; plain lines append.
                        val key = line.substringBefore('\n')
                        if (line.startsWith(GlobalpingRunner.LIVE)) {
                            val idx = cur.lines.indexOfLast { l -> l.startsWith("$key\n") }
                            val next = if (idx >= 0) {
                                cur.lines.toMutableList().also { it[idx] = line }
                            } else {
                                cur.lines + line
                            }
                            cur.copy(lines = next.takeLast(2000))
                        } else {
                            cur.copy(lines = (cur.lines + line).takeLast(2000))
                        }
                    }
                }
            _state.update { it.copy(running = false, progress = null) }
        }
    }

    fun outputText(): String = _state.value.lines.joinToString("\n") { GlobalpingRunner.displayOf(it) }

    /** Runner intro lines that only repeat what the blue "== ... ==" header
     *  already says (tool + target + backend). Dropped so each run opens with
     *  a single header line. Lines carrying unique info (resolved IPs, counts,
     *  warnings, errors) never match and are kept. */
    private fun isEchoLine(tool: Tool, st: HomeUiState, line: String): Boolean {
        if (line.startsWith(GlobalpingRunner.LIVE)) return false
        if ((tool == Tool.PING && st.pingGlobal) || (tool == Tool.TRACE && st.traceGlobal))
            return line.startsWith(";; global ")
        return when (tool) {
            Tool.PING -> line.startsWith("PING ")
            Tool.DIG -> line.startsWith("; DiG via ")
            Tool.TRACE -> line.startsWith("traceroute to ")
            Tool.WHOIS -> line.startsWith(";; whois/rdap for ")
            Tool.IPINFO, Tool.MYIP -> line.startsWith(";; GET ") ||
                line.startsWith(";; public IP of this device") ||
                line.startsWith(";; note: this provider only")
            Tool.HEADERS -> line.startsWith(";; HEADERS ")
            Tool.PORTS -> !st.portsGlobal && line.startsWith(";; checking ")
            Tool.CERT -> line.startsWith(";; TLS certificate for ")
            Tool.SWEEP -> line.startsWith(";; IP scan on ")
        }
    }

    private fun globalWhere(st: HomeUiState): String =
        if (st.globalCountry.isEmpty()) " worldwide" else " ${st.globalCountry}"

}
