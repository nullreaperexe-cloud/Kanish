# ClassPing 🔔

ClassPing is a smart school reminder system for upcoming tests, notebook submissions, assignments, projects, exams, and other important academic events.

## Pipeline

EduSecure → GitHub Actions → deterministic pre-filter → OpenRouter free model → Firebase Firestore → ClassPing Android app → local scheduled reminder.

## Security

- Never commit Firebase service-account JSON, EduSecure credentials, or OpenRouter keys.
- All credentials must live in GitHub Actions Secrets.
- The Android app must never contain the OpenRouter API key or Firebase Admin private key.

## Firestore collections

- `events` — normalized upcoming academic events
- `source_messages` — hashes/metadata for deduplication and audit
- `automation_logs` — backend run summaries
- `users` — per-user settings (written by the app)
- `app_config/main` — global app configuration

## Required GitHub Actions secrets

- `EDUSECURE_USERNAME`
- `EDUSECURE_PASSWORD`
- `OPENROUTER_API_KEY`
- `FIREBASE_SERVICE_ACCOUNT`

## AI cost policy

AI is called only for new messages that pass the local academic-event pre-filter. The default OpenRouter model is `openrouter/free`, and the parser expects compact JSON only.
