# Distribution & Release Guide — Sudoku Master Trainer

This repository uses the two-workflow Android CI + Firebase App Distribution setup:
- **Standard CI (`.github/workflows/ci.yml`)**: Runs on every push and pull request to `main`, executing unit tests (`testDirectApkDebugUnitTest`) and building the debug APK (`assembleDirectApkDebug`) without distributing a Firebase release.
- **Firebase App Distribution (`.github/workflows/firebase-distribute.yml`)**: Runs **only** on manual `workflow_dispatch` or when pushing a `v*` git tag, defaulting to standalone signed **`directApk`** (`.apk`) builds while keeping **`playBundle`** (`.aab`) available for devices enforcing **Android Advanced Protection Mode**.

---

## 1. Gradle Flavors Overview

| Flavor | Output | Update Mechanism | Play Protect / Advanced Protection |
| --- | --- | --- | --- |
| **`directApk`** *(Default release flavor)* | Signed `.apk` | In-app updates via `firebase-appdistribution` SDK (**Check for app updates** icon in the top bar) + Firebase App Distribution link | Works on standard Play Protect devices. |
| **`playBundle`** *(Optional / Advanced Protection)* | Signed `.aab` | Google Play (Internal App Sharing / Testing) | Zero `REQUEST_INSTALL_PACKAGES`; compatible with **OS Advanced Protection Mode**. |

---

## 2. Provisioned Firebase & Release Signing Configuration

The following Firebase project, Android app, signing keystore metadata, and CI distributor service account are provisioned for `com.arendon.sudokumaster`:

- **Firebase Project ID**: `sudoku-master-261119` (Project Number `901277894359`)
- **Firebase Android App ID**: `1:901277894359:android:93c7b06cee79c17f881292`
- **Tester Group**: `family` (`Family`)
- **CI Service Account**: `firebase-app-distro@sudoku-master-261119.iam.gserviceaccount.com` (`roles/firebaseappdistro.admin`)
- **Release Keystore Alias**: `sudokumaster`
- **Keystore SHA-1**: `3A:13:C1:5F:9E:3F:C0:28:65:29:6C:6C:5C:C4:59:02:FF:07:0A:9C`
- **Keystore SHA-256**: `FA:54:6E:9B:55:91:E1:82:FD:36:CE:38:C2:03:DC:90:CB:D5:4D:A3:95:33:B7:6F:5B:09:77:88:C9:EF:F7:17`

Local credential and keystore files (`release.jks`, `keystore.properties`, `app/google-services.json`, and `service-account-firebase.json`) are strictly **gitignored** via `.gitignore` and must never be committed. Keep a secure offline backup of `release.jks` and `keystore.properties` so future APK upgrades preserve the same signing identity.

> **Upgrade note:** Because this configuration replaces the old publicly committed demo keystore (`sudoku-master.p12`) with a fresh private `release.jks`, devices with older builds installed must perform a **one-time uninstall** before installing their first Firebase App Distribution release.

---

## 3. GitHub Actions Repository Secrets

Configured in GitHub → **Settings → Secrets and variables → Actions** for `arendon/sudoku-master-trainer`:

| Secret Name | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Single-line base64 of `release.jks` |
| `KEYSTORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `KEY_ALIAS` | `sudokumaster` |
| `KEY_PASSWORD` | `keyPassword` from `keystore.properties` |
| `GOOGLE_SERVICES_JSON_BASE64` | Single-line base64 of `app/google-services.json` |
| `FIREBASE_APP_ID` | `1:901277894359:android:93c7b06cee79c17f881292` |
| `FIREBASE_CREDENTIAL_FILE_CONTENT` | Full JSON content of `service-account-firebase.json` |

---

## 4. Triggering a Firebase App Distribution Release

- **Manual Workflow Dispatch (Recommended)**:
  GitHub → **Actions → Distribute to Family via Firebase → Run workflow** (choose `directApk` or `playBundle` and optional release notes).
- **Via Version Tag**:
  ```bash
  git tag v1.2.0
  git push origin v1.2.0
  ```
  Sets `VERSION_NAME=1.2.0`, runs unit tests, builds the signed `directApk` release, and uploads it to the `family` tester group in Firebase App Distribution.
