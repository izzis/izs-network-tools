#!/usr/bin/env python3
"""Generate app/src/main/assets/oui.txt from the maclookup.app JSON export.

Source : https://maclookup.app/downloads (JSON database, free for app use;
         attribution recommended when redistributing - see docs/TOOLS.md)
Usage  : python3 scripts/gen_oui.py /path/to/mac-vendors-export.json

Output : one "<hexprefix>\\t<vendor>" line per entry, sorted.
         Prefix length = registry block: 6 hex = MA-L/CID (24-bit),
         7 hex = MA-M/IAB (28-bit), 9 hex = MA-S (36-bit).
         Vendor names are cleaned (text before first comma, trailing
         corporate suffix dropped) and capped at 18 chars so the
         WiFi Analyzer line-3 vendor column stays aligned.
"""

import json
import re
import sys
from pathlib import Path

MAX_VENDOR = 18
# Trailing words dropped when they are corporate suffixes only:
# "ZTE corporation" -> "ZTE", "Nokia Shanghai Bell Co." -> "Nokia Shanghai Bell".
SUFFIXES = {
    "inc", "incorporated", "corp", "corporation", "co", "company",
    "ltd", "limited", "llc", "llp", "gmbh", "plc", "pte", "bv", "nv",
}
VALID_PREFIX_LENS = (6, 7, 9)


def clean_vendor(raw: str) -> str:
    if raw.islower():
        raw = raw.upper()  # IEEE lists some orgs lowercase: "zte corporation" -> ZTE
    name = re.sub(r"\s+", " ", raw.split(",")[0]).strip()
    while True:
        parts = name.split()
        if len(parts) <= 1:
            break
        if parts[-1].rstrip(".").lower() in SUFFIXES:
            name = " ".join(parts[:-1])
        else:
            break
    if not name:
        name = re.sub(r"\s+", " ", raw).strip()
    return name[:MAX_VENDOR]


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    src = Path(sys.argv[1])
    entries = json.loads(src.read_text(encoding="utf-8"))

    table: dict[str, str] = {}
    skipped = 0
    for e in entries:
        prefix = e.get("macPrefix", "").replace(":", "").lower()
        vendor = clean_vendor(e.get("vendorName", ""))
        if len(prefix) not in VALID_PREFIX_LENS or not vendor:
            skipped += 1
            continue
        table[prefix] = vendor

    out = Path(__file__).resolve().parents[1] / "app/src/main/assets/oui.txt"
    out.parent.mkdir(parents=True, exist_ok=True)
    lines = sorted(table.items(), key=lambda kv: (len(kv[0]), kv[0]))
    out.write_text("".join(f"{k}\t{v}\n" for k, v in lines), encoding="utf-8")

    print(f"{len(entries)} in -> {len(lines)} out ({skipped} skipped), "
          f"{out.stat().st_size} bytes -> {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
