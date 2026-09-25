# Architecture

Single-module app (`:app`). Kotlin + Compose Material3, unidirectional data
flow: UI state lives in `NetToolsViewModel` as a `StateFlow<HomeUiState>`,
screens collect it with `collectAsStateWithLifecycle()`.

```
app/src/main/java/id/web/izs/nettools/
  MainActivity.kt        entry point, theme, double-back-to-exit
  model/Models.kt        SavedHost, AppSettings, Tool enum, presets
  data/SettingsRepository.kt   DataStore: settings, saved hosts, recents
  core/                  one runner per tool, each returns Flow<String>
    TargetParser.kt      "https://h:8080/p" -> host + port
    ExecUtil.kt          binary detection (/system/bin/ping, traceroute)
    PingRunner.kt        system ping binary, streamed via callbackFlow
    DnsRunner.kt         dnsjava lookup + system fallback
    TraceRunner.kt       pure traceroute: TTL-ping probes, destination-IP stop, dupe notes
    LoopDetector.kt      pure L3 routing-loop analysis (used by LoopRunner)
    StormDetector.kt     pure L2 storm analysis over gateway ping + ARP samples
    ArpWatcher.kt        /proc/net/arp parsing + MAC-flap detection
    GatewayResolver.kt   default gateway via /proc/net/route, .1-guess fallback
    LoopRunner.kt        Loop tool: L2 storm phase + L3 trace phase (early-stop), L2/L3/Both mode
    WhoisRdapClient.kt   RDAP-first, WHOIS/43 fallback + referrals
    IpInfoClient.kt      generic JSON IP-info client (geo + IP-only)
    PortChecker.kt       parallel TCP connect, list/range parser
    CertChecker.kt       TLS handshake, chain capture, trust check
    HttpHeadersFetcher.kt  GET + redirect chain + headers
    IpScan.kt            parallel ping scanner (IP / range / CIDR / auto /24)
    OuiDb.kt             OUI vendor lookup for the 3-row display (assets/oui.txt)
  ui/
    NetToolsViewModel.kt tool dispatch, progress, save/recents
    HomeScreen.kt        target bar, picker, 2-row selector, terminal
    SettingsScreen.kt    tabbed settings (Servers/Whois/Scan/Tools/General)
    ManageHostsScreen.kt saved-host CRUD + search
    ColorsScreen.kt      per-section custom colors (swatches + hex)
    Theme.kt             AMOLED / Dark / Sand / Light gray + overrides
```

## Runner pattern

Every tool is `fun …(…): Flow<String>` emitting one output line at a time on
`Dispatchers.IO`. The ViewModel collects into `state.lines`; cancelling the
collector `Job` (Stop button) tears the work down — processes are destroyed
via `awaitClose`, sockets via `use {}`.

Rules learned the hard way:

- Never `emit()` from inside `withContext()` — it violates Flow exception
  transparency. Do blocking work directly in the `flow {}` body (already on
  IO through `flowOn`), or collect values first and emit afterwards.
- `callbackFlow` + `trySend` for process output and parallel fan-out
  (ping streaming, IP scan discovery).
- Failures are never silent: a missing binary or ignored TTL flag produces
  an honest `ERROR`/note line instead of fake `*` rows.

## Persistence

`SettingsRepository` over Preferences DataStore (`izs_nettools`):

- `AppSettings` (servers, hops, timeout, port list, parallel probes,
  auto-run switches, font size, max recent, theme, custom colors, schemes,
  tool order + disabled tools)
- `saved_hosts` (JSON list of `SavedHost`), `recent_hosts` (capped by `max_recent`, default 5)
- Target bar slots: `last_target` (host tools — shared, written on run and on
  clear-to-empty) and `wifi_filter` (WiFi Analyzer SSID/MAC only). Switching
  between WiFi and any host tool swaps the bar contents; each clear overwrites
  only its own key so reopen never resurrects the other tool's text.
- `last_tool` restores the selected tool with the matching slot above (select
  only, never auto-run).
- `global_prefs` (JSON `GlobalPrefs`): Global vs Local engine per tool
  (Ping/Trace/Ports) + Globalping probes/country — saved on every toggle,
  restored on launch. Fresh install = Local / 10 probes / worldwide.
- WiFi Analyzer filters: `wifi_bands` (band multi-select JSON, default
  2.4+5+6) and `wifi_rows` (AP row count 2 | 3 for the List display,
  default 2). Channel / security / display / sort stay session-only.
- Settings auto-save with 600 ms debounce; flushed on back navigation.
- Tool grid order/visibility (`tool_order`, `disabled_tools`) follows the
  `Tool` enum order by default with Headers + Cert off: top row Ping–My IP,
  bottom row Trace, Ports, Loop, Neighbor, IP Scan (5+5). The split is dynamic
  (`half = (n+1)/2`) — no hardcoded row size, no placeholder cells.
  `tool_grid_rows` (default 2) picks the classic split or a compact 1-row
  layout — fixed pages of 5 tools in a snapping `HorizontalPager`.
  `hide_tool_grid` persists the top-bar Hide collapse across launches; while
  collapsed, long-pressing the shown tool name opens a quick switcher
  (enabled tools only, same order/auto-run semantics as a grid tap).
  Loop mode (L2/L3/Both) is per-session state, not persisted (unlike the
  Ping/Trace/Ports scopes, which now live in `global_prefs`).
