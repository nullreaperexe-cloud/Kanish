from __future__ import annotations

import re
from datetime import date, datetime, timedelta
from typing import Any, Dict, Optional, Tuple

from .utils import clean_student_text, clean_text

SUBJECT_PATTERNS = [
    ("Artificial Intelligence", r"\b(?:artificial\s+intelligence|ai)\b"),
    ("Social Science", r"\b(?:social\s+science|social\s+studies|sst|history|geography|civics)\b"),
    ("Mathematics", r"\b(?:mathematics|maths|math)\b"),
    ("Science", r"\b(?:science|physics|chemistry|biology)\b"),
    ("Computer", r"\b(?:computer(?:\s+science)?|ict)\b"),
    ("English", r"\benglish\b"),
    ("Hindi", r"\bhindi\b"),
    ("Punjabi", r"\bpunjabi\b"),
    ("French", r"\bfrench\b"),
    ("GK", r"\b(?:general\s+knowledge|gk)\b"),
]

EVENT_PATTERNS = [
    (
        "notebook_submission",
        r"\b(?:notebook|note\s*book)\b[\s\S]{0,80}\b(?:submit|submission|checking|check|bring|complete|show)\b"
        r"|\b(?:submit|submission|checking|check|bring|complete|show)\b[\s\S]{0,80}\b(?:notebook|note\s*book)\b",
    ),
    (
        "test",
        r"\b(?:revision\s*test|class\s*test|unit\s*test|weekly\s*test|test|quiz|assessment)\b",
    ),
    ("exam", r"\b(?:exam|examination)\b"),
    ("practical", r"\b(?:practical|viva|oral\s+test)\b"),
    ("assignment", r"\bassignment\b"),
    ("project", r"\bproject\b"),
    ("homework", r"\b(?:home\s*work|homework)\b"),
]

MONTHS = {
    "jan": 1, "january": 1,
    "feb": 2, "february": 2,
    "mar": 3, "march": 3,
    "apr": 4, "april": 4,
    "may": 5,
    "jun": 6, "june": 6,
    "jul": 7, "july": 7,
    "aug": 8, "august": 8,
    "sep": 9, "sept": 9, "september": 9,
    "oct": 10, "october": 10,
    "nov": 11, "november": 11,
    "dec": 12, "december": 12,
}

WEEKDAYS = {
    "monday": 0,
    "tuesday": 1,
    "wednesday": 2,
    "thursday": 3,
    "friday": 4,
    "saturday": 5,
    "sunday": 6,
}


def detect_event_type(text: str) -> Optional[str]:
    raw = clean_text(text)
    for event_type, pattern in EVENT_PATTERNS:
        if re.search(pattern, raw, flags=re.I):
            return event_type
    return None


def detect_subject(text: str) -> str:
    raw = clean_text(text)
    for subject, pattern in SUBJECT_PATTERNS:
        if re.search(pattern, raw, flags=re.I):
            return subject

    if re.search(r"[\u0A00-\u0A7F]", raw):
        return "Punjabi"
    if re.search(r"[\u0900-\u097F]", raw):
        return "Hindi"
    return "General"


def _safe_date(year: int, month: int, day: int) -> Optional[date]:
    try:
        return date(year, month, day)
    except ValueError:
        return None


def _choose_year(month: int, day: int, posted_date: Optional[date]) -> int:
    base = posted_date or date.today()
    candidate = _safe_date(base.year, month, day)
    if candidate and candidate < base - timedelta(days=120):
        return base.year + 1
    return base.year


def extract_event_date(
    text: str,
    posted_date: Optional[date],
) -> Tuple[Optional[date], str]:
    raw = clean_text(text)

    m = re.search(r"\b(20\d{2})[-/](\d{1,2})[-/](\d{1,2})\b", raw)
    if m:
        parsed = _safe_date(int(m.group(1)), int(m.group(2)), int(m.group(3)))
        if parsed:
            return parsed, m.group(0)

    m = re.search(r"\b(\d{1,2})[/-](\d{1,2})[/-](20\d{2})\b", raw)
    if m:
        parsed = _safe_date(int(m.group(3)), int(m.group(2)), int(m.group(1)))
        if parsed:
            return parsed, m.group(0)

    m = re.search(
        r"\b(\d{1,2})(?:st|nd|rd|th)?\s+"
        r"(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|"
        r"Jul(?:y)?|Aug(?:ust)?|Sep(?:t|tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"
        r"(?:\s*,?\s*(20\d{2}))?\b",
        raw,
        flags=re.I,
    )
    if m:
        month = MONTHS[m.group(2).lower()]
        year = int(m.group(3)) if m.group(3) else _choose_year(month, int(m.group(1)), posted_date)
        parsed = _safe_date(year, month, int(m.group(1)))
        if parsed:
            return parsed, m.group(0)

    m = re.search(
        r"\b(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|"
        r"Jul(?:y)?|Aug(?:ust)?|Sep(?:t|tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"
        r"\s+(\d{1,2})(?:st|nd|rd|th)?(?:\s*,?\s*(20\d{2}))?\b",
        raw,
        flags=re.I,
    )
    if m:
        month = MONTHS[m.group(1).lower()]
        year = int(m.group(3)) if m.group(3) else _choose_year(month, int(m.group(2)), posted_date)
        parsed = _safe_date(year, month, int(m.group(2)))
        if parsed:
            return parsed, m.group(0)

    if posted_date:
        if re.search(r"\bday\s+after\s+tomorrow\b", raw, flags=re.I):
            return posted_date + timedelta(days=2), "day after tomorrow"
        if re.search(r"\btomorrow\b", raw, flags=re.I):
            return posted_date + timedelta(days=1), "tomorrow"
        if re.search(r"\btoday\b", raw, flags=re.I):
            return posted_date, "today"

        weekday_match = re.search(
            r"\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b",
            raw,
            flags=re.I,
        )
        if weekday_match:
            target = WEEKDAYS[weekday_match.group(1).lower()]
            delta = (target - posted_date.weekday()) % 7
            if delta == 0:
                delta = 7
            return posted_date + timedelta(days=delta), weekday_match.group(0)

    return None, ""


def extract_topic(text: str, event_type: str) -> str:
    raw = clean_student_text(text)

    patterns = [
        r"\b(?:chapter|ch\.?)\s*[-:]?\s*\d+[A-Za-z]?(?:\s*(?:to|-|–|&)\s*\d+[A-Za-z]?)?(?:\s*(?:q\s*&\s*a|question\s*answers?))?",
        r"\bexercise\s*[-:]?\s*[\d.]+(?:\s*(?:to|-|–|&)\s*[\d.]+)?",
        r"\b(?:question\s*answers?|q\s*&\s*a)\b",
    ]
    for pattern in patterns:
        m = re.search(pattern, raw, flags=re.I)
        if m:
            return clean_text(m.group(0))

    if event_type == "notebook_submission":
        subject = detect_subject(raw)
        return f"{subject} Notebook" if subject != "General" else "Notebook"

    return ""


def make_title(event_type: str, subject: str, topic: str) -> str:
    short_subject = {
        "Social Science": "SST",
        "Mathematics": "Maths",
        "Artificial Intelligence": "AI",
    }.get(subject, subject)

    labels = {
        "test": "Test",
        "notebook_submission": "Notebook Submission",
        "homework": "Homework",
        "assignment": "Assignment",
        "project": "Project",
        "exam": "Exam",
        "practical": "Practical",
    }

    pieces = []
    if short_subject and short_subject != "General":
        pieces.append(short_subject)
    if topic:
        pieces.append(topic)
    pieces.append(labels.get(event_type, "School Update"))
    return clean_text(" ".join(pieces))[:90]


def try_local_parse(
    text: str,
    posted_date: Optional[date],
) -> Dict[str, Any]:
    event_type = detect_event_type(text)
    if not event_type:
        return {
            "decision": "not_event",
            "reason": "no strong event pattern",
        }

    subject = detect_subject(text)
    event_date, date_label = extract_event_date(text, posted_date)
    topic = extract_topic(text, event_type)

    event = {
        "type": event_type,
        "subject": subject,
        "title": make_title(event_type, subject, topic),
        "topic": topic,
        "eventDate": event_date.isoformat() if event_date else None,
        "dateLabel": date_label,
        "priority": "high" if event_type in {"test", "exam", "notebook_submission"} else "normal",
        "needsReview": event_date is None,
    }

    # Only skip AI when the message is clear enough to trust deterministically.
    # Subject and date are the two fields most dangerous to guess incorrectly.
    if subject != "General" and event_date is not None:
        return {
            "decision": "local_event",
            "event": event,
            "reason": "strong type + explicit subject + deterministic date",
        }

    return {
        "decision": "needs_ai",
        "event": event,
        "reason": "event detected but subject/date is ambiguous",
    }
