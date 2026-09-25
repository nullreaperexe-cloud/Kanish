# GitHub Secrets for ClassPing

Add these under Repository Settings > Secrets and variables > Actions:

1. EDUSECURE_USERNAME
2. EDUSECURE_PASSWORD
3. OPENROUTER_API_KEY
4. FIREBASE_SERVICE_ACCOUNT

FIREBASE_SERVICE_ACCOUNT must contain the entire downloaded Firebase Admin SDK JSON file as the secret value.
Never commit any of these values into repository files.

The workflow safely skips the live sync until all four secrets exist.
