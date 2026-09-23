# Rootless limits vs rooted roadmap (NOT implemented)

Everything in izs NetTools is rootless by design. This file records, per
tool, what that costs today and what root (or a neighboring privilege like
SNMP/Shizuku) would unlock. Nothing here is implemented; items are roughly
ordered by value within each tool.

Shared root-only primitives behind most items below: raw sockets
(`AF_PACKET`/`SOCK_RAW`, i.e. `CAP_NET_RAW`), promiscuous capture, `tcpdump`
— which is not bundled with Android and would have to ship as a static
binary in `assets/` (ABI split per arch) — and packet crafting. Android
SELinux may deny even Magisk-`su` processes raw sockets, so every item needs
a per-device spike before being promised.

## Loop — L2 storm phase

1. **STP BPDU sniffing (true L2-loop proof).** With `su` + `tcpdump`,
   capture Spanning-Tree frames: `tcpdump -i wlan0 -c 200 'stp'`. A flood of
   Topology Change Notifications (TCN) or rapidly changing root-bridge IDs =
   a real switching loop, not a symptom. Sketch: `RootUtil.hasRoot()`
   (`su -c id` exit 0) gates a new hold-mode `DEEP (root)` in the Loop
   dialog; `LoopRunner` streams `su -c "tcpdump …"` and counts BPDU/sec +
   TCN flags.
2. **Interface counters, full breakdown.** The rootless half is done
   (`NetDevWatcher` via TrafficStats: RX/TX pps + asymmetry). Root adds
   counter resets and per-queue RX-error/drop detail (`ip -s link`).
3. **Neighbor-table churn.** `ip neigh show` polled like `/proc/net/arp`
   but with state (`REACHABLE/STALE/DELAY/FAILED`) — richer than ARP Flags.
   Same `ArpWatcher` polling shape, better states.
4. **Firewall counters.** `ebtables -L --Lc` / `iptables -L -v -n`; a runaway
   broadcast counter is a loop fingerprint. Rarely ships on stock ROMs —
   low priority.
5. **SNMP against the managed switch (no phone root needed!).** Query the
   switch directly: `dot1dStpTopChanges`, `ifInBroadcastPkts` deltas,
   MAC-move traps. The *operator-grade* answer for fixed LANs — but needs a
   new SNMP client dependency, community/v3 credentials in Settings →
   Servers, and the switch IP as target. Best long-term fit for
   network-admin use.
6. **Shizuku as a middle path.** ADB/shell-level without full root (one
   wireless-ADB pairing per boot). Runs shell-privileged commands and
   app-bundled binaries as shell UID — but BPDU capture stays
   device-dependent (`CAP_NET_RAW`), virtualization (AVF/pKVM) cannot touch
   the raw physical NIC at all, and VPNService capture only sees the phone's
   own L3 traffic (never STP). Verdict: spike AF_PACKET-as-shell first;
   integrate only on success, otherwise wont-do.

## Trace / Loop — L3 trace phase

Rootless today: TTL-limited `ping` per hop. Costs: slow (one process per
hop, multi-second timeout per silent hop), ICMP-only (no UDP/TCP-SYN
traceroute, so firewalled paths look identical to filtered ones), no MPLS
label visibility, no paris-style multi-path tracing.

Root unlocks: real UDP/TCP-SYN traceroute via raw sockets (faster, parallel
probes, paths through ICMP-blocking firewalls), ICMP time-exceeded parsing
with full quote inspection, and MPLS label stacks from extension headers.
`TraceRunner.probe()` stays the shared primitive; a `RootTraceRunner` would
slot beside it behind the same `[via …]` tag honesty.

## Ping

Rootless today: the system `ping` binary with safe flags. Costs: no flood
mode (`-f` needs privilege — and is a DoS primitive, so think twice before
exposing it at all), no sub-second intervals, no packet crafting.

Root unlocks: flood/interval control for link stress tests (gated behind
big warnings if ever), hardware timestamping (`-D` style output the parser
would need to learn). Low value, some abuse risk — bottom of the list.

## Ports

Rootless today: full TCP `connect()` per port. Costs: slow-ish, noisy (every
probe completes the handshake and lands in target logs), and UDP scanning is
unreliable (ICMP-unreachable feedback needs a raw listener).

Root unlocks: SYN half-open stealth scan (fast, quiet-ish), real UDP scan
with an ICMP listener, and nmap-style OS fingerprinting via crafted probes.
Same `PortChecker.parsePorts()` list format; only the engine changes, tagged
`[via su+synscan]`.

## IP Scan

Rootless today: parallel `ping -c1` sweep. Costs: a process spawn per host
(~254 for a /24), and hosts that block ICMP are invisible even when alive.

Root unlocks: ARP sweep (one packet per host, finds ICMP-blocking hosts,
an order of magnitude faster on /24). Same range/CIDR parser; engine swap
behind a root gate. High value for LAN-admin use.

## Neighbor

Rootless today: MNDP listen (UDP 5678 broadcast) — MikroTik-only, but finds
devices that don't even have an IP yet — plus mDNS/Bonjour
(`224.0.0.251:5353`: printers, casts, cameras) and SSDP/UPnP
(`239.255.255.250:1900`: TVs, routers, NAS), all plain-UDP behind a WiFi
multicast lock. That MikroTik-only boundary on the no-IP case is the
protocol's, not ours: every vendor's "hello, I'm here" speaks a different
tongue, and only MikroTik's is plain UDP a normal socket can hear.

Root unlocks: CDP (Cisco) and LLDP (everyone) are pure-L2 EtherType frames —
no IP, no UDP, only a raw socket hears them. Same for promiscuous sniffing
(see *every* MAC, including silent ones) and MAC-table reads from the AP
itself. A `NEIGHBOR (root)` mode would parse CDP/LLDP beside MNDP behind
the same `[via …]` tag honesty.

## Cert

Rootless today: direct TLS handshake — and that is already the whole job.
The known gap (STARTTLS on SMTP/IMAP/POP/FTP) is **not a root problem**: the
STARTTLS negotiation is plain app-level protocol any socket can speak. It
belongs on the rootless roadmap as pure Kotlin, not here.

## Dig / Whois / IP Info / My IP / Headers

No root needed, now or ever: DNS, RDAP/WHOIS TCP/43, HTTPS APIs, and HTTP
are all user-socket protocols. No roadmap items.

## UX rule if any of this lands

Keep the `[via …]` backend tag honest (`[via su+tcpdump]`, `[via snmp]`,
`[via su+synscan]`), auto-disable root-only modes with an explanatory note
when `su` is absent, and never silently downgrade a `DETECTED` verdict to
symptom-grade signals.
