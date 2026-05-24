"""
Merges scraped findings into the seed catalog, producing:
  - scam_catalog.json  (the catalog the app downloads)
  - version.json       (metadata: version + sha256 + updatedAt)

De-dup is conservative: we only add an entry if it does NOT already
appear in the seed. We never overwrite or remove seed entries.

If `data/scraped_165.json` is missing or contains no findings, we still
republish the seed unchanged (with a bumped version + fresh updatedAt)
so the file age stays current. The app will only prompt when the
`version` integer increases — see catalog version logic.

Two version fields, mirroring Android versionCode↔versionName:
  - `version`       : machine integer, monotonically +1, drives update detection.
  - `displayVersion`: human-facing semver-style label shown in 防詐專區.
      * GH 資訊更新 (scraped lists grew) → patch +1 automatically.
      * CIB 資訊更新 / 大改版 → pass `--display-version vMAJOR.MINOR.PATCH`
        explicitly; the build adopts it verbatim and also bumps `version`.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from datetime import date, datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED_PATH = ROOT / "seed" / "scam_catalog.json"
SCRAPED_PATH = ROOT / "data" / "scraped_165.json"
CATALOG_OUT = ROOT / "scam_catalog.json"
VERSION_OUT = ROOT / "version.json"

# Bare-domain pattern e.g. "0857.games" / "bbhhshf.cc" — must contain a
# dot and an alphabetic TLD ≥ 2 chars. The scrape side normalizes to
# this form already; this is the catalog-side classifier safety net.
BARE_DOMAIN_RE = re.compile(r"^[a-z0-9][a-z0-9.\-]*\.[a-z]{2,}$")

# Human-facing display version: optional leading "v" + MAJOR.MINOR.PATCH.
DISPLAY_VERSION_RE = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)$")
DEFAULT_DISPLAY_VERSION = "v1.0.0"


def _normalize_display_version(value: str) -> str:
    """Validate a human-supplied display version, returning canonical 'vX.Y.Z'."""
    m = DISPLAY_VERSION_RE.match(value.strip())
    if not m:
        raise SystemExit(
            f"[build_catalog] invalid --display-version: {value!r} "
            f"(expected vMAJOR.MINOR.PATCH, e.g. v1.1.0)"
        )
    major, minor, patch = (int(x) for x in m.groups())
    return f"v{major}.{minor}.{patch}"


def _bump_patch(value: str) -> str:
    """Increment the PATCH segment — used when the GH-scraped lists grew."""
    m = DISPLAY_VERSION_RE.match(value.strip())
    if not m:
        return DEFAULT_DISPLAY_VERSION
    major, minor, patch = (int(x) for x in m.groups())
    return f"v{major}.{minor}.{patch + 1}"

# (finding key in scraped JSON, source label, note)
URL_SOURCES = (
    (
        "urls",
        "165 官網（自動抓取）",
        "由 GH Actions cron 從 165.npa.gov.tw 文章內文擷取，未經人工複核",
    ),
    (
        "fraudSitesInvestment",
        "165 假投資網站名單（data.gov.tw 警政署）",
        "由 GH Actions cron 從 data.gov.tw dataset 160055 自動下載，未經人工複核",
    ),
    (
        "fraudSitesDnsBlocked",
        "遭停止解析涉詐網站（data.gov.tw 警政署）",
        "由 GH Actions cron 從 data.gov.tw dataset 176455 自動下載，未經人工複核",
    ),
)


def _sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def _load_existing_catalog() -> dict | None:
    if not CATALOG_OUT.exists():
        return None
    try:
        return json.loads(CATALOG_OUT.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return None


def _classify_alias(value: str) -> str:
    v = value.strip()
    if v.startswith("@"):
        return "LINE"
    if "://" in v:
        return "URL"
    if BARE_DOMAIN_RE.match(v.lower()):
        return "URL"
    return "OTHER"


def _merge_suspicious_names(seed_list: list[dict], scraped: dict) -> list[dict]:
    existing_names = {entry["name"].strip() for entry in seed_list}
    result = list(seed_list)
    today = date.today().isoformat()

    # LINE IDs keep the original cron source label.
    for value in scraped.get("lineIds", []):
        v = value.strip()
        if not v or v in existing_names:
            continue
        result.append({
            "name": v,
            "aliasType": _classify_alias(v),
            "source": "165 官網（自動抓取）",
            "reportedDate": today,
            "note": "由 GH Actions cron 從 165.npa.gov.tw 自動擷取，未經人工複核",
        })
        existing_names.add(v)

    # URL sources: per-source labels so the App's detail view can tell
    # the user where the alert came from.
    for key, source_label, note in URL_SOURCES:
        for value in scraped.get(key, []):
            v = value.strip()
            if not v or v in existing_names:
                continue
            result.append({
                "name": v,
                "aliasType": _classify_alias(v),
                "source": source_label,
                "reportedDate": today,
                "note": note,
            })
            existing_names.add(v)

    return result


def _merge_warned_accounts(seed_list: list[dict], scraped: dict) -> list[dict]:
    existing = {entry["account"].strip() for entry in seed_list}
    result = list(seed_list)
    today = date.today().isoformat()
    for digits in scraped.get("accounts", []):
        d = digits.strip()
        if not d or d in existing:
            continue
        if not (8 <= len(d) <= 16):
            continue
        result.append({
            "account": d,
            "bank": "",
            "source": "165 官網（自動抓取）",
            "reportedDate": today,
            "note": "由 GH Actions cron 從 165.npa.gov.tw 自動擷取，未經人工複核",
        })
        existing.add(d)
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--force-bump",
        action="store_true",
        help="Force version bump even when scraped lists did not grow "
             "(use after hand-editing seed tactics/categories).",
    )
    parser.add_argument(
        "--display-version",
        metavar="vMAJOR.MINOR.PATCH",
        help="Set the human-facing displayVersion explicitly (use for CIB "
             "資訊更新 → minor, or 大改版 → major). Implies a machine version "
             "bump so the app detects the update. When omitted, displayVersion "
             "auto-bumps its PATCH whenever the GH-scraped lists grow.",
    )
    args = parser.parse_args()

    if not SEED_PATH.exists():
        print(f"[build_catalog] missing seed: {SEED_PATH}", file=sys.stderr)
        return 1
    seed = json.loads(SEED_PATH.read_text(encoding="utf-8"))

    if SCRAPED_PATH.exists():
        scraped_findings = json.loads(SCRAPED_PATH.read_text(encoding="utf-8")).get("findings", {})
    else:
        scraped_findings = {}

    merged = dict(seed)
    merged["suspiciousNames"] = _merge_suspicious_names(seed.get("suspiciousNames", []), scraped_findings)
    merged["warnedAccounts"] = _merge_warned_accounts(seed.get("warnedAccounts", []), scraped_findings)
    merged["lastUpdated"] = date.today().isoformat()

    existing = _load_existing_catalog()
    seed_susp = len(seed.get("suspiciousNames", []))
    seed_warn = len(seed.get("warnedAccounts", []))
    grew = (len(merged["suspiciousNames"]) > seed_susp) or (len(merged["warnedAccounts"]) > seed_warn)

    # An explicit --display-version means a human is publishing a CIB / 大改版
    # update; the machine `version` must advance too or the app won't detect it.
    want_bump = grew or args.force_bump or bool(args.display_version)
    if existing is None:
        # First build: adopt the seed version as-is, no bump.
        next_version = seed.get("version", 1)
        version_bumped = False
    elif want_bump:
        next_version = existing.get("version", 1) + 1
        version_bumped = True
    else:
        next_version = existing.get("version", 1)
        version_bumped = False
    merged["version"] = next_version

    # displayVersion: explicit override wins; otherwise auto-bump PATCH only
    # when the machine version actually advanced (i.e. GH-scraped lists grew).
    base_display = (
        (existing or {}).get("displayVersion")
        or seed.get("displayVersion")
        or DEFAULT_DISPLAY_VERSION
    )
    if args.display_version:
        next_display = _normalize_display_version(args.display_version)
    elif version_bumped:
        next_display = _bump_patch(base_display)
    else:
        next_display = base_display
    merged["displayVersion"] = next_display

    # write_bytes (not write_text) so Windows doesn't translate "\n" to "\r\n".
    # The sha256 we publish in version.json must match what raw.githubusercontent
    # serves, and git stores LF in the repo regardless of autocrlf.
    CATALOG_OUT.write_bytes(
        json.dumps(merged, ensure_ascii=False, indent=2).encode("utf-8")
    )

    version_payload = {
        "version": next_version,
        "displayVersion": next_display,
        "sha256": _sha256(CATALOG_OUT),
        "updatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "suspiciousNamesCount": len(merged["suspiciousNames"]),
        "warnedAccountsCount": len(merged["warnedAccounts"]),
    }
    VERSION_OUT.write_bytes(
        json.dumps(version_payload, ensure_ascii=False, indent=2).encode("utf-8")
    )
    print(f"[build_catalog] version={next_version} displayVersion={next_display} "
          f"susp={version_payload['suspiciousNamesCount']} "
          f"warn={version_payload['warnedAccountsCount']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
