# Tools

Every run prints `[via …]` in its header so you always know which engine ran.
Non-rooted Android cannot open raw sockets, so anything ICMP-flavored goes
through the privileged system `ping` binary.

| Tool | Backend |
|------|---------|
| Ping | `/system/bin/ping` (`-c`, `-W`), streamed line by line — or Global (Globalping probes worldwide) |
| Dig | dnsjava against your server; A/AAAA fall back to system resolver |
| Trace | real `traceroute` binary if present, else TTL-limited ping per hop — or Global (Globalping probes worldwide) |
| Whois | RDAP via IANA bootstrap (`data.iana.org/rdap/dns.json`, `rdap.org`), fallback WHOIS TCP/43 with one referral hop |
| IP Info | HTTPS JSON APIs (see SERVERS.md), generic key/value rendering |
| My IP | same client, self-lookup endpoint |
| Ports | parallel TCP `connect()`; `host:port` forces single-port mode — or Global (Shodan InternetDB passive lookup) |
| Cert | direct `SSLSocket` handshake with SNI; chain captured even if untrusted, trust re-checked against the system store |
| Headers | OkHttp GET with redirect chain, status, timing, all headers |
| IP Scan | parallel `ping -c1` over IP, `A.B.C.X-Y`, or `/24`-`/32` (empty = own /24), UP lines + compact RTO ranges (offline lines toggleable, default off) |

## Known rootless limits

- Trace hops that stay silent print `*`. Consecutive duplicate IPs are
  annotated (`same as hop N, typical for anycast/MPLS`) — 8.8.8.8 does this.
- Every trace ends with a loop verdict line in the console: `LOOP DETECTED`
  (red) when an IP repeats non-consecutively or an A-B-A-B cycle appears,
  `Suspected loop or filtering` (amber) when max hops is reached without
  arriving, otherwise `No loop` (green). Only the two bad outcomes also
  pop a banner above the console; clean traces stay text-only.
  A *confirmed* loop stops the trace early: the same IP seen at 3+ TTLs,
  an A-B cycle observed twice, or a tiny IP set dominating the run (a
  looping packet can never arrive, further probes would just repeat the
  cycle). A single repeat is suspicion only — the run continues and it
  verdicts at the end instead of cutting the trace short. Clean traces run
  on to the destination as before. Consecutive duplicates alone never flag
  (anycast), and all-silent runs skip the verdict instead of crying wolf.
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
