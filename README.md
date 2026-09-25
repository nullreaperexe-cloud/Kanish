# ClassPing 🔔

Smart school event detection and reminder backend for tests, notebook submissions, assignments, projects, exams, practicals, and other important academic events.

## Current backend flow

EduSecure → GitHub Actions every 5 minutes → local keyword pre-filter → OpenRouter free model → Firebase Firestore → ClassPing Android app → local scheduled reminders.

## Cost control

- AI is called only for new messages that pass the local academic-event filter.
- Messages are deduplicated in Firestore before another AI call.
- Multiple candidate messages are analyzed in batches.
- Default model: openrouter/free.

## Repository structure

- main.py — orchestration
- src/edusecure_reader.py — reuses the proven EduSecure Selenium reader
- src/ai_parser.py — OpenRouter event extraction
- src/firebase_store.py — Firestore writes and deduplication
- src/utils.py — cleaning, filtering, normalization
- test_classping.py — unit tests
- .github/workflows/classping-sync.yml — five-minute automation
- FIRESTORE_SCHEMA.md — data model
- SECRETS_SETUP.md — required secret names

## Required Actions secrets

- EDUSECURE_USERNAME
- EDUSECURE_PASSWORD
- OPENROUTER_API_KEY
- FIREBASE_SERVICE_ACCOUNT

The workflow validates successfully even before secrets are added and safely skips live syncing until all secrets exist.

## Security

Never commit Firebase Admin JSON, EduSecure credentials, OpenRouter keys, or Android google-services.json into this repository.
