# Sudoku Master Trainer Repository Rules

## Release & Firebase Distribution Guardrail
- **Never distribute to Firebase App Distribution automatically.** Only upload builds to Firebase (via `Distribute to Family via Firebase` `workflow_dispatch`, pushing a `v*` tag, or running Firebase CLI `appdistribution:distribute`) when the user **explicitly asks** to push or release to Firebase.
- **Keep `.github/workflows/firebase-distribute.yml` manual/tag-only.** Do not add `branches: [main]` (or other branch push triggers) under `on.push` in `.github/workflows/firebase-distribute.yml`.
- **Standard Verification**: Pushing regular changes to `main` runs `.github/workflows/ci.yml` (`Android CI` unit tests + debug APK build) without creating a Firebase release.
- **Protect Signing & Cloud Credentials**: Never commit `release.jks`, `keystore.properties`, `app/google-services.json`, or `service-account-firebase.json`.
