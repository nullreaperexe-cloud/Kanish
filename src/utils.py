from __future__ import annotations

import hashlib
import json
import re
from datetime import date, datetime
from typing import Any, Dict, Optional

EVENT_TYPES = {
    "test", "notebook_submission", "homework", "assignment", "project",
    "exam", "practical", "important", "other",
}

ACADEMIC_PREFILTER = re.compile(
    r"\b("
    r"revision\s*test|class\s*test|unit\s*test|weekly\s*test|test|quiz|assessment|"
    r"exam(?:ination)?|oral|viva|practical|"
    r"notebook|note\s*book|submission|submit|checking|"
    r"assignment|project|home\s*work|homework|worksheet|learn|revise|prepare|"
    r"bring\s+(?:your\s+)?(?:book|notebook|file)|"
    r"syllabus|chapter|exercise|question\s*answers?|q\s*&\s*a"
    r")\b",
    re.I,
)

NOISE_PREFIXES = re.compile(
    r"^\s*(?:dear\s+(?:students?|parents?|children)|respected\s+parents?|hello\s+students?)"
    r"\s*[,!:\-]*\s*",
    re.I,
)
NOISE_SUFFIXES = re.compile(
    r"\s*(?:regards|thanks(?:\s*&\s*regards)?|thank\s+you)[\s\S]{0,80}$",
    re.I,
)


def clean_text(value: Any) -> str:
    text = str(value or "").replace("\u00a0", " ")
    return re.sub(r"\s+", " ", text).strip()


def clean_student_text(value: Any) -> str:
    text = NOISE_PREFIXES.sub("", clean_text(value))
    text = NOISE_SUFFIXES.sub("", text)
    return text.strip(" -:|,;")


def source_id(message_text: str, posted_date: Optional[date]) -> str:
    normalized = clean_text(message_text).lower()
    day = posted_date.isoformat() if posted_date else "undated"
    return hashlib.sha256(f"{day}|{normalized}".encode()).hexdigest()[:40]


def likely_academic_event(text: str) -> bool:
    return bool(ACADEMIC_PREFILTER.search(clean_text(text)))


def extract_json_object(text: str) -> Dict[str, Any]:
    raw = clean_text(text)
    fence = chr(96) * 3
    if raw.startswith(fence + "json"):
        raw = raw[len(fence + "json"):].strip()
    elif raw.startswith(fence):
        raw = raw[len(fence):].strip()
    if raw.endswith(fence):
        raw = raw[:-len(fence)].strip()

    try:
        value = json.loads(raw)
        if isinstance(value, dict):
            return value
    except json.JSONDecodeError:
        pass

    start, end = raw.find("{"), raw.rfind("}")
    if start >= 0 and end > start:
        value = json.loads(raw[start:end + 1])
        if isinstance(value, dict):
            return value
    raise ValueError("OpenRouter did not return a JSON object")


def normalize_event(raw: Dict[str, Any]) -> Dict[str, Any]:
    event_type = clean_text(raw.get("type")).lower()
    if event_type not in EVENT_TYPES:
        event_type = "other"

    event_date = clean_text(raw.get("eventDate"))
    if event_date:
        try:
            datetime.strptime(event_date, "%Y-%m-%d")
        except ValueError:
            event_date = ""

    return {
        "type": event_type,
        "subject": clean_text(raw.get("subject")) or "General",
        "title": clean_student_text(raw.get("title"))[:90] or "School update",
        "topic": clean_student_text(raw.get("topic"))[:220],
        "eventDate": event_date or None,
        "dateLabel": clean_student_text(raw.get("dateLabel"))[:80],
        "priority": "high" if clean_text(raw.get("priority")).lower() == "high" else "normal",
        "needsReview": bool(raw.get("needsReview")) or not bool(event_date),
    }
