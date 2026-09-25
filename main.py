from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Dict, Iterable, List

from src.ai_parser import analyze_messages
from src.edusecure_reader import scan_recent_messages
from src.firebase_store import (
    existing_source_state,
    get_firestore,
    mark_source,
    store_event,
    write_log,
)
from src.utils import (
    clean_text,
    likely_academic_event,
    normalize_event,
    source_id,
)

REPORT_FILE = Path("classping_report.json")
MAX_SCAN_DAYS = int(os.environ.get("CLASSPING_SCAN_DAYS", "30"))
MAX_AI_BATCH = int(os.environ.get("CLASSPING_AI_BATCH", "8"))


def chunked(
    values: List[Dict[str, Any]],
    size: int,
) -> Iterable[List[Dict[str, Any]]]:
    size = max(1, size)
    for i in range(0, len(values), size):
        yield values[i:i + size]


def main() -> int:
    report: Dict[str, Any] = {
        "messagesScanned": 0,
        "alreadyProcessed": 0,
        "localFilterIgnored": 0,
        "aiCandidates": 0,
        "aiBatches": 0,
        "eventsCreated": 0,
        "needsReview": 0,
        "errors": 0,
    }

    try:
        db = get_firestore()
        messages = scan_recent_messages(MAX_SCAN_DAYS)
        report["messagesScanned"] = len(messages)

        candidates: List[Dict[str, Any]] = []

        for item in messages:
            text = item["text"]
            posted = item.get("posted_date")
            sid = source_id(text, posted)
            state = existing_source_state(db, sid)

            if state in {"done", "ignored"}:
                report["alreadyProcessed"] += 1
                continue

            if not likely_academic_event(text):
                mark_source(
                    db,
                    sid,
                    text=text,
                    posted_date=posted,
                    state="ignored",
                    relevant=False,
                    ai_processed=False,
                    note="local pre-filter: no academic event signal",
                )
                report["localFilterIgnored"] += 1
                continue

            mark_source(
                db,
                sid,
                text=text,
                posted_date=posted,
                state="pending_ai",
                relevant=True,
                ai_processed=False,
            )

            candidates.append(
                {
                    "id": sid,
                    "text": text,
                    "postedDate": (
                        posted.isoformat()
                        if posted
                        else None
                    ),
                    "posted_date_obj": posted,
                }
            )

        report["aiCandidates"] = len(candidates)

        for batch in chunked(candidates, MAX_AI_BATCH):
            report["aiBatches"] += 1

            results = (
                analyze_messages(batch).get("results")
                or []
            )
            by_id = {
                clean_text(item.get("id")): item
                for item in results
                if isinstance(item, dict)
            }

            for candidate in batch:
                sid = candidate["id"]
                result = by_id.get(sid)

                if not result:
                    mark_source(
                        db,
                        sid,
                        text=candidate["text"],
                        posted_date=candidate["posted_date_obj"],
                        state="pending_ai",
                        relevant=True,
                        ai_processed=False,
                        note=(
                            "AI response missing this message; "
                            "retry next run"
                        ),
                    )
                    continue

                events_raw = result.get("events") or []
                relevant = (
                    bool(result.get("relevant"))
                    and bool(events_raw)
                )

                if not relevant:
                    mark_source(
                        db,
                        sid,
                        text=candidate["text"],
                        posted_date=candidate["posted_date_obj"],
                        state="ignored",
                        relevant=False,
                        ai_processed=True,
                        note="AI classified as non-actionable",
                    )
                    continue

                created = 0

                for index, raw_event in enumerate(
                    events_raw[:8]
                ):
                    if not isinstance(raw_event, dict):
                        continue

                    event = normalize_event(raw_event)

                    store_event(
                        db,
                        sid,
                        index,
                        event,
                        candidate["posted_date_obj"],
                    )

                    created += 1
                    report["eventsCreated"] += 1

                    if event["needsReview"]:
                        report["needsReview"] += 1

                mark_source(
                    db,
                    sid,
                    text=candidate["text"],
                    posted_date=candidate["posted_date_obj"],
                    state=(
                        "done"
                        if created
                        else "ignored"
                    ),
                    relevant=created > 0,
                    ai_processed=True,
                    note=f"{created} event(s) stored",
                )

        write_log(db, report)

        REPORT_FILE.write_text(
            json.dumps(report, indent=2),
            encoding="utf-8",
        )

        print(json.dumps(report, indent=2))
        return 0

    except Exception as exc:
        report["errors"] += 1
        report["lastError"] = (
            f"{type(exc).__name__}: {exc}"[:500]
        )

        REPORT_FILE.write_text(
            json.dumps(report, indent=2),
            encoding="utf-8",
        )

        print(
            "ClassPing sync failed: "
            f"{type(exc).__name__}: {exc}"
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
