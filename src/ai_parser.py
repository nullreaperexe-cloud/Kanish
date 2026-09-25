from __future__ import annotations

import json
import os
from typing import Any, Dict, List

import requests

from .utils import clean_student_text, extract_json_object

OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
FREE_MODEL = os.environ.get("OPENROUTER_MODEL", "openrouter/free")


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
Convert EduSecure school messages into compact structured JSON for an Android reminder app.
Return ONLY valid JSON. No Markdown and no explanation.

OUTPUT:
{"results":[{"id":"same id","relevant":true,"events":[
{"type":"test|notebook_submission|homework|assignment|project|exam|practical|important|other",
"subject":"canonical subject or General",
"title":"short student-facing title",
"topic":"short syllabus/item or empty string",
"eventDate":"YYYY-MM-DD or null",
"dateLabel":"useful original day/date wording or empty string",
"priority":"high|normal",
"needsReview":false}]}]}

RULES:
- Never invent a date, subject, chapter, exercise, syllabus, or task.
- If an exact calendar date cannot be derived, eventDate=null and needsReview=true.
- Preserve Day 5, tomorrow, or a weekday in dateLabel when useful, but never guess a calendar date.
- Revision/class/unit/weekly test, quiz, assessment => test.
- Notebook checking/submission/bring completed notebook => notebook_submission.
- Exam => exam; project => project; assignment => assignment; practical/viva => practical.
- Remove greetings/closings such as Dear Students, Dear Parents, Regards, and Thank you.
- Keep title normally under 60 characters.
- Use canonical subjects: Mathematics, Science, Social Science, English, Hindi, Punjabi,
  French, Computer, Artificial Intelligence, GK, or General.
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
        },
        timeout=75,
    )
    response.raise_for_status()

    body = response.json()
    content = body["choices"][0]["message"]["content"]
    return extract_json_object(content)
