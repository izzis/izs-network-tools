# Tools

Every run prints `[via …]` in its header so you always know which engine ran.
Non-rooted Android cannot open raw sockets, so anything ICMP-flavored goes
through the privileged system `ping` binary.

| Tool | Backend |
|------|---------|
| Ping | `/system/bin/ping` (`-c`, `-W`), streamed line by line — or Global (Globalping probes worldwide) |
| Dig | dnsjava against your server; A/AAAA fall back to system resolver |
| Trace | real `traceroute` binary if present, else TTL-limited ping per hop — pure traceroute, no verdict (loop analysis lives in Loop) — or Global (Globalping probes worldwide) |
| Whois | RDAP via IANA bootstrap (`data.iana.org/rdap/dns.json`, `rdap.org`), fallback WHOIS TCP/43 with one referral hop |
| IP Info | HTTPS JSON APIs (see SERVERS.md), generic key/value rendering |
| My IP | same client, self-lookup endpoint |
| Ports | parallel TCP `connect()`; `host:port` forces single-port mode — or Global (Shodan InternetDB passive lookup) |
| Cert | direct `SSLSocket` handshake with SNI; chain captured even if untrusted, trust re-checked against the system store |
| Headers | OkHttp GET with redirect chain, status, timing, all headers |
| IP Scan | parallel `ping -c1` over IP, `A.B.C.X-Y`, or `/24`-`/32` (empty = own /24), UP lines with IP + hostname (system reverse DNS, then explicit PTR against the active network's own DNS servers, then NetBIOS, then a targeted mDNS query straight at the host itself); neighbor MAC + `[gw]` flag toggleable in Scan settings (default off) + compact RTO ranges (offline lines toggleable, default off) |
| Loop | L2 storm check (gateway ping burst — count settable in Scan settings, default 10 — DUP/RTT/loss + `/proc/net/arp` flap + RX flood rate via TrafficStats, `/proc/net/dev` fallback) + L3 TTL-ping trace with confirmed-loop early-stop (empty target = auto gateway, hold for L2/L3/Both; L3 skipped when L2 already confirms a storm) |
| Neighbor | three listen phases sharing the Scan timeout: MNDP refresh to `255.255.255.255:5678/udp` (MAC + identity + version + board + IP, `(no IP)` when the device has none) + mDNS service enumeration on `224.0.0.251:5353` + SSDP `M-SEARCH` on `239.255.255.250:1900`; needs no target and no local IP |
| WiFi Analyzer | `WifiManager.getScanResults()` re-read every 30 s (active `startScan()` throttled to the same cadence); filter tabs **Band → Channel → Security → Display** (rightmost); Band + Security are multi-select chips (default all on, no All button — items OR within a group, groups AND; Band choice **persists across app launches**, with a **Select All** action pinned at the far right of the Band row to restore the default); Channel + Display are single-select; Display = **List/Channel** · **Rows: 2/3** on the left · **Sort:** + **RSSI / SSID / Ch** pills pinned right (the value row scrolls horizontally when the screen is narrow; List only, Channel always sorts by channel no; connected AP pinned on top; list re-sorts on every refresh and when the sort chip changes, scroll stays on the focused row) = **List** (one live row per BSSID — SSID, signal bar, dBm, free-space distance estimate `~X.Xm` next to dBm, channel, bandwidth MHz, band, security, 802.11 standard when known from `ScanResult.wifiStandard` API 30+ e.g. `802.11ax`; **Rows: 2** (default) keeps the 2-line layout, **Rows: 3** moves the standard to a third line `vendor · 802.11ac (WiFi 5) · (gone)/(filter)` — vendor is an OUI lookup from the bundled `assets/oui.txt` (blank when unknown), the standard gains its Wi-Fi generation (n→4, ac→5, ax→6/6E on 6 GHz, be→7), and the line is omitted when vendor+standard+markers are all empty; `Rows` is the one Display setting persisted across launches; associated AP's row green; rows update in place; a vanished AP shows `(gone)` for one refresh cycle, then its row is auto-removed — if it reappears first, the row just updates in place; an AP still on air but knocked out by the active filters shows `(filter)` until it matches again — both markers ride the line-1/line-3 marker slot and dim the whole block) or **Channel** (overlap AP count per channel — an AP counts toward every channel whose center falls inside its occupied bandwidth, e.g. ch1 + ch3 APs both hit ch2; rows sorted by channel number; Band selection drops other bands, Channel chip keeps only the focus row — empty channels of each selected band listed (country-legal only, country from network SIM/ISO → device locale → ID fallback, never GPS: region tables ID / US / EU / JP / WORLD — e.g. ID: 2.4 ch 1–13, 5 GHz 36–64 + 149–165 (no DFS), 6 GHz 1–93 step-4; US adds DFS 100–144 + ch1–11; JP adds ch14), `█` bar = count); target bar = free-text SSID **or** MAC substring filter (optional); header shows `next Ns` countdown — **tap it to refresh now** (wakes the cycle early, no Stop/Run); switching Display while running re-runs immediately; no target needed |

## Known rootless limits

- Trace hops that stay silent print `*`. Consecutive duplicate IPs are
  annotated (`same as hop N, typical for anycast/MPLS`) — 8.8.8.8 does this.
  Trace is verdict-free on purpose: every loop verdict lives in Loop.
- Every Loop run ends with verdict lines in the console. L3 phase: `LOOP DETECTED`
  (red) when an IP repeats non-consecutively (this includes A-B-A-B cycles)
  or a tiny set of ≤3 unique IPs dominates a ≥6-hop answered run (contiguous
  repeats like X,X,Y,Y,Z,Z included),
  `Suspected loop or filtering` (amber) when max hops is reached without
  arriving, otherwise `No loop` (green). L2 phase: `STORM DETECTED` (red) on
  2+ ping `DUP!`s, or any two independent heavy signals among: ≥20% loss with
  ≥2 packets lost, p95 RTT > 500 ms, a one-way RX flood, an ARP flap, or any
  DUP reply; `Suspected storm` (amber) on a single signal — one DUP,
  slow/lossy gateway, lone ARP flap, a one-way flood with replies still
  arriving, or incomplete ARP; otherwise `No storm` (green).
  Only the bad outcomes also pop banners above the console; clean runs stay
  text-only. A *confirmed* L3 loop stops the trace early: the same IP seen at
  3+ TTLs or a tiny IP set dominating the run (a looping packet can never
  arrive, further probes would just repeat the
  cycle). A single repeat is suspicion only — the run continues and it
  verdicts at the end instead of cutting the trace short. A lone consecutive
  duplicate never flags (anycast) — only a tiny dominating set does — and
  all-silent L3 runs skip the
  verdict instead of crying wolf. A silent gateway is judged by the wire,
  not the silence: the RX/TX rate comes from the TrafficStats API
  (`/proc/net/dev` fallback) because `/proc/net` paths are unreadable to apps
  on many modern ROMs — same reason ARP may report unreadable and the verdict
  says ping-only. Silent under a one-way packet flood (high RX, quiet TX)
  = storm (a real storm drowns the very replies being measured, and the L3
  trace is then skipped as pointless); heavy two-way traffic instead reads
  as downloads, not a flood. Silent with ICMP
  "unreachable" errors and a quiet interface = cable down, not a storm;
  silent with plain timeouts = suspected (honestly ambiguous). True L2
  detection (STP/BPDU sniffing, switch MAC tables)
  needs raw sockets and is impossible without root — Loop reports storm
  *symptoms* honestly labeled as such. What root (or SNMP/Shizuku) would
  unlock is tracked in [ROOT-ROADMAP.md](ROOT-ROADMAP.md).
- Probing stops at the first answer from the resolved destination IP.
- If no hop answers, the output says why (ICMP blocked or TTL ignored).
- Cert on non-TLS ports (SMTP/IMAP) fails honestly: they need STARTTLS.
- IP-only providers (ipify, icanhazip, amazon) report just your own IP.
- Global Ping/Trace need no key (250 tests/hour, max 50 probes anonymous);
  optional token in Servers raises the limit. Needs internet, obviously.
- Global Ports = Shodan InternetDB: one passive GET, free non-commercial use,
  no key. Data is their weekly scan (can be stale; unscanned IPs return
  "no data"), includes reported vulns/hostnames. Local scan stays the
  real-time source of truth.
- Tool scope (Local vs Global) is picked via long-press and remembered across
  launches, default Local; all tool cells always show 2 lines so the grid stays
  uniform.
- Neighbor only hears what announces itself: MikroTik via MNDP, anything
  via mDNS/SSDP. Silent hosts and other vendors' APs stay invisible without
  root (their CDP/LLDP are pure-L2 frames). Binding UDP :5678/:5353 fails
  honestly if another listener holds the port; mDNS/SSDP need the WiFi
  multicast lock (auto-held during the run).
- WiFi Analyzer needs a runtime grant (Nearby devices on Android 13+,
  Location before — one prompt on first Run) and Location (GPS) switched
  on system-wide, or the OS returns zero scan results; without root it
  only sees what the phone's radio hears (no wired hosts, no hidden SSIDs
  that never beacon). Android throttles active scans to ~4 per 2 minutes —
  the 30 s cycle sits at that limit, so rows between kicks read the OS
  cache (the system also rescans on its own while WiFi is up).

## Output colors

Toggleable in General settings. Red = errors/expired/untrusted,
green = success/valid/open, blue = headers/summaries/redirects,
amber = partial loss/duplicates, dim gray = comments, closed ports,
silent hops. `Key: value` lines render the key dim and the value bright.

## Data sources

WiFi Analyzer vendor names ship in `assets/oui.txt` (58,882 MAC-prefix →
vendor entries; 6/7/9 hex-char prefixes cover MA-L, MA-M/IA-B and MA-S
blocks). The file is generated by `scripts/gen_oui.py` from the maclookup.app
JSON database (<https://maclookup.app/downloads>), whose data derives from
the IEEE public MAC address registries; redistributed with attribution per
maclookup.app terms. Lookup is fully offline — no vendor API calls at
runtime.
