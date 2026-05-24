"""
Parse data/cib_wg117_all.html (single page with pageSize=100) and emit
data/cib_wg117_articles.json — a list of {serno, title, url} for every
article on the CIB "最新犯罪手法宣導" board.
"""

from __future__ import annotations

import json
import re
from html import unescape
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "data" / "cib_wg117_all.html"
OUT = ROOT / "data" / "cib_wg117_articles.json"

# Match the anchor's href + title= attribute in either order. Title is
# the most reliable source (clean, no HTML noise, present on every
# article anchor on the list page).
ANCHOR_HREF_FIRST = re.compile(
    r'<a[^>]*href="[^"]*view\?module=wg117&amp;id=1893&amp;serno=([0-9a-f-]{36})"[^>]*title="([^"]+)"',
    re.IGNORECASE,
)
ANCHOR_TITLE_FIRST = re.compile(
    r'<a[^>]*title="([^"]+)"[^>]*href="[^"]*view\?module=wg117&amp;id=1893&amp;serno=([0-9a-f-]{36})"',
    re.IGNORECASE,
)


def main() -> int:
    html = SRC.read_text(encoding="utf-8")
    seen: dict[str, str] = {}
    for m in ANCHOR_HREF_FIRST.finditer(html):
        serno, title = m.group(1), unescape(m.group(2)).strip()
        if title and serno not in seen:
            seen[serno] = title
    for m in ANCHOR_TITLE_FIRST.finditer(html):
        title, serno = unescape(m.group(1)).strip(), m.group(2)
        if title and serno not in seen:
            seen[serno] = title

    articles = [
        {
            "serno": serno,
            "title": title,
            "url": f"https://www.cib.npa.gov.tw/ch/app/data/view?module=wg117&id=1893&serno={serno}",
        }
        for serno, title in seen.items()
    ]
    OUT.write_bytes(
        json.dumps(articles, ensure_ascii=False, indent=2).encode("utf-8")
    )
    print(f"[parse_wg117] articles={len(articles)} -> {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
