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
| IP Scan | parallel `ping -c1` over IP, `A.B.C.X-Y`, or `/24`-`/32` (empty = own /24), UP lines + compact RTO ranges (offline lines toggleable, default off) |
| Loop | L2 storm check (gateway ping burst — count settable in Scan settings, default 10 — DUP/RTT/loss + `/proc/net/arp` flap + RX flood rate via TrafficStats, `/proc/net/dev` fallback) + L3 TTL-ping trace with confirmed-loop early-stop (empty target = auto gateway, hold for L2/L3/Both; L3 skipped when L2 already confirms a storm) |

## Known rootless limits

- Trace hops that stay silent print `*`. Consecutive duplicate IPs are
  annotated (`same as hop N, typical for anycast/MPLS`) — 8.8.8.8 does this.
  Trace is verdict-free on purpose: every loop verdict lives in Loop.
- Every Loop run ends with verdict lines in the console. L3 phase: `LOOP DETECTED`
  (red) when an IP repeats non-consecutively or an A-B-A-B cycle appears,
  `Suspected loop or filtering` (amber) when max hops is reached without
  arriving, otherwise `No loop` (green). L2 phase: `STORM DETECTED` (red) on
  2+ ping `DUP!`s, exploded gateway RTT with loss, or ARP flap combined with
  ping anomalies; `Suspected storm` (amber) on a single DUP, slow/lossy
  gateway, lone ARP flap, or incomplete ARP; otherwise `No storm` (green).
  Only the bad outcomes also pop banners above the console; clean runs stay
  text-only. A *confirmed* L3 loop stops the trace early: the same IP seen at
  3+ TTLs, an A-B cycle observed twice, or a tiny IP set dominating the run
  (a looping packet can never arrive, further probes would just repeat the
  cycle). A single repeat is suspicion only — the run continues and it
  verdicts at the end instead of cutting the trace short. Consecutive
  duplicates alone never flag (anycast), and all-silent L3 runs skip the
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
  *symptoms* honestly labeled as such.
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
- Tool scope (Local vs Global) is per-session via long-press, default Local;
  all tool cells always show 2 lines so the grid stays uniform.

## Output colors

Toggleable in General settings. Red = errors/expired/untrusted,
green = success/valid/open, blue = headers/summaries/redirects,
amber = partial loss/duplicates, dim gray = comments, closed ports,
silent hops. `Key: value` lines render the key dim and the value bright.
