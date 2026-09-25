from __future__ import annotations

import json
import os
from datetime import date, datetime, timezone
from typing import Any, Dict, Optional

from .utils import clean_text


def get_firestore():
    import firebase_admin
    from firebase_admin import credentials, firestore

    raw = os.environ.get("FIREBASE_SERVICE_ACCOUNT", "").strip()
    if not raw:
        raise RuntimeError("Missing FIREBASE_SERVICE_ACCOUNT GitHub secret")

    try:
        service_account = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise RuntimeError(
            "FIREBASE_SERVICE_ACCOUNT must contain the full Firebase JSON key"
        ) from exc

    if not firebase_admin._apps:
        firebase_admin.initialize_app(credentials.Certificate(service_account))
    return firestore.client()


def existing_source_state(db, sid: str) -> Optional[str]:
    snap = db.collection("source_messages").document(sid).get()
    if not snap.exists:
        return None
    return str((snap.to_dict() or {}).get("state") or "")


def mark_source(
    db,
    sid: str,
    *,
    text: str,
    posted_date: Optional[date],
    state: str,
    relevant: bool,
    ai_processed: bool,
    note: str = "",
) -> None:
    from firebase_admin import firestore

    db.collection("source_messages").document(sid).set(
        {
            "source": "edusecure",
            "sourceMessageId": sid,
            "contentHash": sid,
            "rawText": clean_text(text),
            "postedDate": posted_date.isoformat() if posted_date else None,
            "state": state,
            "relevant": relevant,
            "aiProcessed": ai_processed,
            "note": clean_text(note)[:240],
            "updatedAt": firestore.SERVER_TIMESTAMP,
            "createdAt": firestore.SERVER_TIMESTAMP,
        },
        merge=True,
    )


def store_event(
    db,
    sid: str,
    index: int,
    event: Dict[str, Any],
    message_posted: Optional[date],
) -> None:
    from firebase_admin import firestore

    event_id = f"{sid}-{index + 1}"
    data: Dict[str, Any] = {
        **event,
        "source": "edusecure",
        "sourceMessageId": sid,
        "published": True,
        "status": "active",
        "className": "8",
        "section": "A",
        "messagePostedDate": (
            message_posted.isoformat() if message_posted else None
        ),
        "updatedAt": firestore.SERVER_TIMESTAMP,
        "createdAt": firestore.SERVER_TIMESTAMP,
    }

    if event.get("eventDate"):
        data["eventDate"] = datetime.strptime(
            event["eventDate"], "%Y-%m-%d"
        ).replace(hour=12, tzinfo=timezone.utc)
    else:
        data["eventDate"] = None

    db.collection("events").document(event_id).set(data, merge=True)


def write_log(db, report: Dict[str, Any]) -> None:
    from firebase_admin import firestore

    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    db.collection("automation_logs").document(run_id).set(
        {**report, "runAt": firestore.SERVER_TIMESTAMP}
    )
