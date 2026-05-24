"""
165 反詐騙官網 scraper (Taiwan NPA anti-fraud bureau) + 政府開放資料平台
警政署 165 詐騙網站 CSV 下載。

Three signal sources:

  1. 165 SPA 內部 API (`/api/lineid/querydata`, `/api/article/top/{n}`)
     — bulk LINE ID list + article text regex harvest. SPA returns
     JSON directly; HTML scraping returns an empty shell.

  2. data.gov.tw 160055 — 165 假投資(博弈)網站 CSV
     民眾通報的投資詐騙網站名單。欄位 WEBURL 為 host (e.g. www.x.com)。

  3. data.gov.tw 176455 — 165 遭停止解析涉詐網站 CSV
     政府已封鎖的詐騙網域，含「網站性質」分類（金融保險／電子商務等），
     涵蓋假網拍／假投資／釣魚等多種類型。欄位「網域」為 bare domain。

所有來源都套 SITES_LOOKBACK_DAYS 過濾以控制 catalog 大小。

Output: data/scraped_165.json — findings.lineIds / phones / accounts /
urls / fraudSitesInvestment / fraudSitesDnsBlocked + sourceMeta block.
"""

from __future__ import annotations

import csv
import io
import json
import re
import sys
from datetime import datetime, timezone, timedelta
from html import unescape
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parent.parent
OUT_PATH = ROOT / "data" / "scraped_165.json"
USER_AGENT = "Mozilla/5.0 (compatible; anticyscam-catalog/0.4; +https://github.com/Flashsator/AntiCyScam)"
TIMEOUT_SEC = 120

API_BASE = "https://165.npa.gov.tw"
LINEID_URL = f"{API_BASE}/api/lineid/querydata"
LINEID_MAXDATE_URL = f"{API_BASE}/api/lineid/maxdate"
ARTICLE_TOP_TEMPLATE = f"{API_BASE}/api/article/top/{{n}}"
ARTICLE_TOP_CATEGORIES = (1, 5, 6)

# data.gov.tw direct CSV download endpoints (警政署 / 內政部開放資料)
FAKE_INVESTMENT_CSV_URL = (
    "https://opdadm.moi.gov.tw/api/v1/no-auth/resource/api/dataset/"
    "033197D4-70F4-45EB-9FB8-6D83532B999A/resource/"
    "FEAA1683-4483-4FDC-B861-BC530789E2AB/download"
)
DNS_BLOCKED_CSV_URL = (
    "https://opdadm.moi.gov.tw/api/v1/no-auth/resource/api/dataset/"
    "29E8E643-88ED-4952-B21E-BD42A3B7108C/resource/"
    "D6EE0E44-CDFC-4A39-BA56-E02BCB0A238E/download"
)

LINEID_LOOKBACK_DAYS = 365
SITES_LOOKBACK_DAYS = 365

# Domain sanity: must contain a dot, only safe chars, total >= 4 chars,
# and TLD-ish suffix at least 2 chars. Filters out CSV header noise and
# malformed entries like "網址" or single-token strings.
DOMAIN_VALID_RE = re.compile(r"^[a-z0-9][a-z0-9.\-]*\.[a-z]{2,}$")

PHONE_RE = re.compile(r"\b0\d{1,2}[-\s]?\d{6,8}\b|\b09\d{2}[-\s]?\d{3}[-\s]?\d{3}\b")
ACCOUNT_RE = re.compile(r"\b\d{8,16}\b")
DOMAIN_RE = re.compile(r"https?://[A-Za-z0-9.\-]+(?:/[A-Za-z0-9._\-/?=&%~+]*)?", re.IGNORECASE)
TAG_RE = re.compile(r"<[^>]+>")
WS_RE = re.compile(r"\s+")


def _get_json(url: str):
    resp = requests.get(
        url,
        headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
        timeout=TIMEOUT_SEC,
    )
    resp.raise_for_status()
    return resp.json()


def _get_csv_text(url: str) -> str:
    """Fetch a CSV resource and decode with BOM-stripping."""
    resp = requests.get(
        url,
        headers={"User-Agent": USER_AGENT, "Accept": "text/csv"},
        timeout=TIMEOUT_SEC,
    )
    resp.raise_for_status()
    return resp.content.decode("utf-8-sig")


def _normalize_domain(raw: str) -> str | None:
    """Strip scheme / www. / path, lowercase, validate.

    Accepts both bare domains (`bbhhshf.cc`) and URLs (`https://x.com/y`).
    Returns None when the input doesn't look like a real domain so CSV
    header rows ("網址"/"網域") and junk don't pollute the catalog.
    """
    if not raw:
        return None
    s = raw.strip().lower()
    if "://" in s:
        s = s.split("://", 1)[1]
    if "/" in s:
        s = s.split("/", 1)[0]
    if ":" in s:
        s = s.split(":", 1)[0]
    if s.startswith("www."):
        s = s[4:]
    if len(s) < 4 or "." not in s:
        return None
    if not DOMAIN_VALID_RE.match(s):
        return None
    return s


def _strip_html(html: str) -> str:
    if not html:
        return ""
    return WS_RE.sub(" ", unescape(TAG_RE.sub(" ", html))).strip()


def _parse_alert_time(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(value)
    except ValueError:
        try:
            return datetime.strptime(value[:10], "%Y-%m-%d")
        except ValueError:
            return None


def fetch_lineids() -> tuple[list[str], dict]:
    raw = _get_json(LINEID_URL)
    if not isinstance(raw, list):
        return [], {"raw_count": 0, "kept_count": 0, "cutoff": None}

    # Anchor the cutoff to the dataset's own most-recent alertTime rather than
    # `today` so we tolerate upstream stagnation. 165 publishes infrequently;
    # using wall-clock time would silently drop the whole list once it ages out.
    parsed: list[tuple[datetime | None, str]] = []
    for item in raw:
        line_id = (item.get("lineId") or "").strip()
        if not line_id:
            continue
        alert_dt = _parse_alert_time(item.get("alertTime"))
        parsed.append((alert_dt, line_id))

    valid_dates = [d for d, _ in parsed if d is not None]
    if valid_dates:
        anchor = max(d.replace(tzinfo=None) if d.tzinfo else d for d in valid_dates)
    else:
        anchor = datetime.utcnow()
    cutoff = anchor - timedelta(days=LINEID_LOOKBACK_DAYS)

    kept: set[str] = set()
    for alert_dt, line_id in parsed:
        if alert_dt is not None:
            alert_naive = alert_dt.replace(tzinfo=None) if alert_dt.tzinfo else alert_dt
            if alert_naive < cutoff:
                continue
        kept.add(f"@{line_id}")

    meta = {
        "raw_count": len(raw),
        "kept_count": len(kept),
        "anchor": anchor.date().isoformat(),
        "cutoff": cutoff.date().isoformat(),
    }
    return sorted(kept), meta


def fetch_lineid_maxdate() -> str | None:
    try:
        val = _get_json(LINEID_MAXDATE_URL)
        return val if isinstance(val, str) else None
    except requests.RequestException:
        return None


def fetch_fake_investment_sites() -> tuple[list[str], dict]:
    """data.gov.tw 160055 — 165 假投資(博弈)詐騙網站 CSV.

    Header (line 1): WEBSITE_NM,WEBURL,CNT,STA_SDATE,STA_EDATE
    Line 2 is Chinese duplicate header — domain validator rejects it.

    Keep only entries whose STA_EDATE is within SITES_LOOKBACK_DAYS so
    the catalog stays bounded as the upstream list keeps growing.
    """
    text = _get_csv_text(FAKE_INVESTMENT_CSV_URL)
    reader = csv.DictReader(io.StringIO(text))
    cutoff = (datetime.utcnow() - timedelta(days=SITES_LOOKBACK_DAYS)).date()
    kept: set[str] = set()
    raw_count = 0
    for row in reader:
        raw_count += 1
        end_str = (row.get("STA_EDATE") or "").strip()
        if end_str:
            try:
                end_date = datetime.strptime(end_str, "%Y/%m/%d").date()
                if end_date < cutoff:
                    continue
            except ValueError:
                pass
        domain = _normalize_domain(row.get("WEBURL") or "")
        if domain:
            kept.add(domain)
    return sorted(kept), {
        "raw_count": raw_count,
        "kept_count": len(kept),
        "cutoff": cutoff.isoformat(),
        "source": FAKE_INVESTMENT_CSV_URL,
    }


def fetch_dns_blocked_sites() -> tuple[list[str], dict]:
    """data.gov.tw 176455 — 165 遭停止解析涉詐網站 CSV.

    Header: 民國年月,網域,網站性質,法律依據,聲請單位
    民國年月 format: ROC year + month, e.g. "11412" = 2025-12.

    Keep only entries within SITES_LOOKBACK_DAYS (in ROC YYYYMM form).
    """
    text = _get_csv_text(DNS_BLOCKED_CSV_URL)
    reader = csv.DictReader(io.StringIO(text))
    cutoff_dt = datetime.utcnow() - timedelta(days=SITES_LOOKBACK_DAYS)
    cutoff_roc = (cutoff_dt.year - 1911) * 100 + cutoff_dt.month
    kept: set[str] = set()
    category_counts: dict[str, int] = {}
    raw_count = 0
    for row in reader:
        raw_count += 1
        roc_str = (row.get("民國年月") or "").strip()
        if roc_str:
            try:
                if int(roc_str) < cutoff_roc:
                    continue
            except ValueError:
                pass
        domain = _normalize_domain(row.get("網域") or "")
        if not domain:
            continue
        kept.add(domain)
        cat = (row.get("網站性質") or "").strip() or "未分類"
        category_counts[cat] = category_counts.get(cat, 0) + 1
    return sorted(kept), {
        "raw_count": raw_count,
        "kept_count": len(kept),
        "cutoff_roc": cutoff_roc,
        "categories": category_counts,
        "source": DNS_BLOCKED_CSV_URL,
    }


def fetch_article_text() -> tuple[str, list[str]]:
    chunks: list[str] = []
    sources: list[str] = []
    for n in ARTICLE_TOP_CATEGORIES:
        url = ARTICLE_TOP_TEMPLATE.format(n=n)
        try:
            articles = _get_json(url)
        except requests.RequestException as exc:
            print(f"[scrape_165] {url} -> {exc}", file=sys.stderr)
            continue
        if not isinstance(articles, list):
            continue
        sources.append(url)
        for article in articles:
            title = (article.get("title") or "").strip()
            content_html = article.get("content") or ""
            chunks.append(title)
            chunks.append(_strip_html(content_html))
    return " ".join(c for c in chunks if c), sources


def main() -> int:
    findings = {
        "lineIds": [],
        "phones": [],
        "accounts": [],
        "urls": [],
        "fraudSitesInvestment": [],
        "fraudSitesDnsBlocked": [],
    }
    fetched_urls: list[str] = []
    source_meta: dict = {}

    try:
        line_ids, lineid_meta = fetch_lineids()
        findings["lineIds"] = line_ids
        fetched_urls.append(LINEID_URL)
        source_meta["lineid"] = lineid_meta
    except requests.RequestException as exc:
        print(f"[scrape_165] lineid bulk failed: {exc}", file=sys.stderr)

    maxdate = fetch_lineid_maxdate()
    if maxdate:
        source_meta["lineidMaxDate"] = maxdate

    article_text, article_sources = fetch_article_text()
    fetched_urls.extend(article_sources)
    if article_text:
        findings["phones"] = sorted(set(PHONE_RE.findall(article_text)))
        findings["accounts"] = sorted(set(ACCOUNT_RE.findall(article_text)))
        findings["urls"] = sorted(set(DOMAIN_RE.findall(article_text)))
        source_meta["articleTextLen"] = len(article_text)

    try:
        sites, inv_meta = fetch_fake_investment_sites()
        findings["fraudSitesInvestment"] = sites
        fetched_urls.append(FAKE_INVESTMENT_CSV_URL)
        source_meta["fraudSitesInvestment"] = inv_meta
    except requests.RequestException as exc:
        print(f"[scrape_165] fake investment CSV failed: {exc}", file=sys.stderr)

    try:
        sites, dns_meta = fetch_dns_blocked_sites()
        findings["fraudSitesDnsBlocked"] = sites
        fetched_urls.append(DNS_BLOCKED_CSV_URL)
        source_meta["fraudSitesDnsBlocked"] = dns_meta
    except requests.RequestException as exc:
        print(f"[scrape_165] dns-blocked CSV failed: {exc}", file=sys.stderr)

    payload = {
        "scrapedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "fetchedUrls": fetched_urls,
        "findings": findings,
        "sourceMeta": source_meta,
    }

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(
        f"[scrape_165] wrote {OUT_PATH} "
        f"lineIds={len(findings['lineIds'])} "
        f"phones={len(findings['phones'])} "
        f"accounts={len(findings['accounts'])} "
        f"urls={len(findings['urls'])} "
        f"fakeInv={len(findings['fraudSitesInvestment'])} "
        f"dnsBlocked={len(findings['fraudSitesDnsBlocked'])}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
