# Servers — all open, keyless, free

Every network endpoint the app talks to is configurable in Settings.
Presets are dropdowns; any field also accepts a custom URL (typed left,
preset picked right). No API keys, no accounts, no paid tiers anywhere.

## DNS (Dig) — `Servers` tab

Cloudflare `1.1.1.1`, Google `8.8.8.8`, Quad9 `9.9.9.9`,
Cloudflare alt `1.0.0.1`, or custom IP.

## IP lookup (IP Info) — geo-capable only

`ipwho.is`, `ip-api.com/json` (HTTP, non-commercial),
`ipaddress.to/api/lookup` (geo+ASN), `ipinfo.io/json`, or custom.
These can resolve any IP/domain, so IP Info stays flexible.

## My IP server — any provider

All four above plus IP-only ones: `api.ipify.org?format=json`,
`icanhazip.com`, `checkip.amazonaws.com`. IP-only providers report just
your own IP; if you run IP Info against one, the output says the target
was ignored. Plain-text (non-JSON) answers are shown raw.

## RDAP + WHOIS — `Whois` tab

- RDAP base, default `https://rdap.org` (IANA bootstrap picks the
  authoritative registry per TLD automatically).
- WHOIS fallback server, default `whois.iana.org`, presets for all five
  RIRs (RIPE, ARIN, APNIC, LACNIC, AFRINIC), plus configurable port
  (default 43, in the `Whois` tab).

## Other knobs — `Scan` tab

Max hops (1–64), timeout ms (500–30000), WHOIS port, parallel probes
for IP Scan (8–256, default 32), show-offline-hosts toggle for IP Scan
(RTO ranges, default off), show-MAC toggle for IP Scan UP lines
(default off — off means IP + hostname only), and the port list
(`22,80,8000-8010`: commas/spaces/new lines separate, `-` is an
inclusive range, capped at 1000 with an honest note).
