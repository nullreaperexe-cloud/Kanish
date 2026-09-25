from __future__ import annotations

import json
import os
from typing import Any, Dict, List

import requests

from .utils import clean_student_text, extract_json_object

OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
FREE_MODEL = os.environ.get("OPENROUTER_MODEL", "openrouter/free")

EVENT_SCHEMA = {
    "type": "object",
    "properties": {
        "results": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "id": {"type": "string"},
                    "relevant": {"type": "boolean"},
                    "events": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "type": {
                                    "type": "string",
                                    "enum": [
                                        "test",
                                        "notebook_submission",
                                        "homework",
                                        "assignment",
                                        "project",
                                        "exam",
                                        "practical",
                                        "important",
                                        "other",
                                    ],
                                },
                                "subject": {"type": "string"},
                                "title": {"type": "string"},
                                "topic": {"type": "string"},
                                "eventDate": {
                                    "anyOf": [
                                        {"type": "string"},
                                        {"type": "null"},
                                    ]
                                },
                                "dateLabel": {"type": "string"},
                                "priority": {
                                    "type": "string",
                                    "enum": ["high", "normal"],
                                },
                                "needsReview": {"type": "boolean"},
                            },
                            "required": [
                                "type",
                                "subject",
                                "title",
                                "topic",
                                "eventDate",
                                "dateLabel",
                                "priority",
                                "needsReview",
                            ],
                            "additionalProperties": False,
                        },
                    },
                },
                "required": ["id", "relevant", "events"],
                "additionalProperties": False,
            },
        }
    },
    "required": ["results"],
    "additionalProperties": False,
}


def analyze_messages(messages: List[Dict[str, Any]]) -> Dict[str, Any]:
    api_key = os.environ.get("OPENROUTER_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("Missing OPENROUTER_API_KEY GitHub secret")

    compact = [
        {
            "id": item["id"],
            "postedDate": item["postedDate"],
            "text": clean_student_text(item["text"])[:2500],
        }
        for item in messages
    ]

    system_prompt = """
You are the ClassPing school-event parser.
Convert EduSecure school messages into structured data for a student reminder app.

Rules:
- Never invent a date, subject, chapter, exercise, syllabus, or task.
- If an exact calendar date cannot be derived, eventDate must be null and needsReview=true.
- Preserve Day 5, tomorrow, or a weekday in dateLabel when useful, but never guess a calendar date.
- Revision/class/unit/weekly test, quiz, assessment => test.
- Notebook checking/submission/bring completed notebook => notebook_submission.
- Exam => exam; project => project; assignment => assignment; practical/viva => practical.
- Remove greetings/closings such as Dear Students, Dear Parents, Regards, and Thank you.
- Keep title normally under 60 characters.
- Use canonical subjects such as Mathematics, Science, Social Science, English, Hindi,
  Punjabi, French, Computer, Artificial Intelligence, GK, or General.
- One message may contain multiple events.
- If not actionable, relevant=false and events=[].
- Do not generate reminder jokes. The Android app creates those locally.
""".strip()

    response = requests.post(
        OPENROUTER_URL,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "HTTP-Referer": "https://github.com/nullreaperexe-cloud/Kanish",
            "X-Title": "ClassPing",
        },
        json={
            "model": FREE_MODEL,
            "messages": [
                {"role": "system", "content": system_prompt},
                {
                    "role": "user",
                    "content": json.dumps(
                        {"messages": compact},
                        ensure_ascii=False,
                    ),
                },
            ],
            "temperature": 0,
            "max_tokens": 1400,
            "response_format": {
                "type": "json_schema",
                "json_schema": {
                    "name": "classping_events",
                    "strict": True,
                    "schema": EVENT_SCHEMA,
                },
            },
            "provider": {
                "require_parameters": True,
            },
        },
        timeout=90,
    )
    response.raise_for_status()

    body = response.json()
    message = body.get("choices", [{}])[0].get("message", {})
    content = message.get("content")

    if isinstance(content, dict):
        return content

    if isinstance(content, list):
        pieces = []
        for part in content:
            if isinstance(part, dict) and isinstance(part.get("text"), str):
                pieces.append(part["text"])
            elif isinstance(part, str):
                pieces.append(part)
        content = "".join(pieces)

    if not isinstance(content, str) or not content.strip():
        raise ValueError("OpenRouter returned empty structured output")

    parsed = extract_json_object(content)
    if not isinstance(parsed.get("results"), list):
        raise ValueError("OpenRouter structured output is missing results")
    return parsed
