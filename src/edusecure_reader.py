from __future__ import annotations

import os
import sys
import time
from datetime import date, timedelta
from pathlib import Path
from typing import Any, Dict, List

from .utils import clean_text


def scan_recent_messages(max_scan_days: int = 30) -> List[Dict[str, Any]]:
    helper_dir = Path("_edusecure").resolve()
    if not helper_dir.exists():
        raise RuntimeError("_edusecure helper checkout is missing")

    sys.path.insert(0, str(helper_dir))
    import sync as legacy  # type: ignore

    username = os.environ.get("EDUSECURE_USERNAME", "")
    password = os.environ.get("EDUSECURE_PASSWORD", "")
    if not username or not password:
        raise RuntimeError(
            "Missing EDUSECURE_USERNAME/EDUSECURE_PASSWORD GitHub secrets"
        )

    legacy.EDUSECURE_USERNAME = username
    legacy.EDUSECURE_PASSWORD = password

    cutoff = date.today() - timedelta(days=max_scan_days)
    found: List[Dict[str, Any]] = []
    processed: set[str] = set()
    bottom_hits = 0
    old_hits = 0

    driver = legacy.make_driver()
    try:
        driver.get(legacy.START_URL)

        if not legacy.auto_login_edusecure(driver):
            legacy.save_debug_screenshot(
                driver,
                "debug_classping_login.png",
            )
            raise RuntimeError("EduSecure login failed")

        driver.get(legacy.START_URL)
        legacy.wait_ready(driver)
        time.sleep(0.8)

        while len(found) < 150:
            visible = legacy.find_visible_dashboard_messages_v29(
                driver,
                processed,
            )

            if not visible:
                result = legacy.dashboard_scroll_v24(driver)
                bottom_hits = (
                    bottom_hits + 1
                    if result.get("atBottom")
                    else 0
                )
                if bottom_hits >= 4:
                    break
                continue

            item = visible[0]
            text = clean_text(item.get("text"))
            fingerprint = (
                item.get("fp")
                or legacy.fingerprint(text)
            )

            if fingerprint:
                processed.add(fingerprint)

            if not text:
                continue

            posted_date = legacy.extract_message_date(text)

            if posted_date and posted_date < cutoff:
                old_hits += 1
                if old_hits >= 8:
                    break
                continue

            old_hits = 0
            found.append(
                {
                    "text": text,
                    "posted_date": posted_date,
                }
            )

        return found

    finally:
        try:
            driver.quit()
        except Exception:
            pass
