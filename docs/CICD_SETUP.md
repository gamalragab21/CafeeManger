# CI/CD Setup Guide

This guide explains how to set up the GitHub Actions CI/CD pipeline and Google Drive upload for the Waselak project.

---

## Table of Contents

1. [Pipeline Overview](#pipeline-overview)
2. [Step 1: Create Google Cloud Service Account](#step-1-create-google-cloud-service-account)
3. [Step 2: Share Google Drive Folder](#step-2-share-google-drive-folder)
4. [Step 3: Encode the Keystore](#step-3-encode-the-keystore)
5. [Step 4: Add GitHub Secrets](#step-4-add-github-secrets)
6. [Step 5: Push and Verify](#step-5-push-and-verify)
7. [Triggering the Pipeline](#triggering-the-pipeline)
8. [Artifact Naming Convention](#artifact-naming-convention)
9. [Google Drive Folder Structure](#google-drive-folder-structure)
10. [Troubleshooting](#troubleshooting)

---

## Pipeline Overview

The CI/CD pipeline runs 5 jobs:

```
lint ──┐
       ├──> build-android ──┐
test ──┘                    ├──> upload-to-drive
       ├──> build-desktop ──┘
       │    (linux, macos, windows)
```

| Job | Runner | What It Does |
|-----|--------|-------------|
| lint | ubuntu | Android lint on all 3 apps |
| test | ubuntu | Backend tests (48) + KMP tests (28+) |
| build-android | ubuntu | Debug + Release APKs for manager, cashier, delivery |
| build-desktop | ubuntu, macos, windows | .deb, .dmg, .msi for all 3 apps |
| upload-to-drive | ubuntu | Uploads all 15 artifacts to Google Drive |

---

## Step 1: Create Google Cloud Service Account

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project (or use existing) — name it e.g., `waselak-ci`
3. Navigate to **APIs & Services > Library**
4. Search for **Google Drive API** and click **Enable**
5. Navigate to **APIs & Services > Credentials**
6. Click **Create Credentials > Service Account**
7. Fill in:
   - Name: `waselak-drive-uploader`
   - ID: auto-generated
8. Click **Done** (skip the optional role/user steps)
9. Click on the created service account name
10. Go to the **Keys** tab
11. Click **Add Key > Create new key > JSON**
12. A JSON file will download — save it securely
13. Note the `client_email` field in the JSON file — you'll need it in Step 2

### Encode the Service Account Key

```bash
# On macOS
base64 -i your-downloaded-key.json | tr -d '\n' | pbcopy
# The result is now in your clipboard

# On Linux
base64 -w 0 your-downloaded-key.json
# Copy the output
```

Save this base64 string — it becomes the `GOOGLE_DRIVE_CREDENTIALS` secret.

---

## Step 2: Share Google Drive Folder

1. Open [Google Drive](https://drive.google.com/)
2. Create a folder called `Waselak Releases` (or any name you prefer)
3. Right-click the folder > **Share**
4. Paste the service account email (from Step 1, e.g., `waselak-drive-uploader@project-id.iam.gserviceaccount.com`)
5. Set permission to **Editor**
6. Click **Send**
7. Open the shared folder and copy the **folder ID** from the URL:

```
https://drive.google.com/drive/folders/1aBcDeFgHiJkLmNoPqRsTuVwXyZ
                                       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                                       This is the GOOGLE_DRIVE_FOLDER_ID
```

---

## Step 3: Encode the Keystore

The Android release keystore (`keystore/waselak-release.jks`) is git-ignored, so the CI pipeline needs to reconstruct it from a GitHub secret.

```bash
# Encode the keystore to base64
base64 -i keystore/waselak-release.jks | tr -d '\n'
# Copy the output — this becomes KEYSTORE_BASE64
```

---

## Step 4: Add GitHub Secrets

1. Go to your GitHub repository
2. Navigate to **Settings > Secrets and variables > Actions**
3. Click **New repository secret** for each:

### Release Config Secrets

| Secret Name | Value | Example |
|-------------|-------|---------|
| `RELEASE_BASE_URL` | Production API URL | `https://api.waselak.net` |
| `RELEASE_HMAC_SECRET` | HMAC secret (must match backend `.env.prod`) | `2316e41b2ad2b7aa...` |
| `RELEASE_SENTRY_DSN` | Sentry DSN | `https://xxx@sentry.io/xxx` |

### Android Signing Secrets

| Secret Name | Value | How to Get |
|-------------|-------|-----------|
| `KEYSTORE_BASE64` | Base64-encoded `.jks` file | See Step 3 |
| `KEYSTORE_STORE_PASSWORD` | Store password | From keystore creation |
| `KEYSTORE_KEY_ALIAS` | Key alias | Usually `waselak` |
| `KEYSTORE_KEY_PASSWORD` | Key password | From keystore creation |

### Google Drive Secrets

| Secret Name | Value | How to Get |
|-------------|-------|-----------|
| `GOOGLE_DRIVE_CREDENTIALS` | Base64-encoded service account JSON | See Step 1 |
| `GOOGLE_DRIVE_FOLDER_ID` | Drive folder ID | See Step 2 |

**Total: 9 secrets**

---

## Step 5: Push and Verify

```bash
# Commit the workflow file
git add .github/workflows/ci.yml
git commit -m "Add CI/CD pipeline with Google Drive upload"
git push origin main
```

Then:
1. Go to your repository on GitHub
2. Click the **Actions** tab
3. You should see the "Waselak CI/CD" workflow running
4. Click on it to see each job's progress
5. After completion, check your Google Drive folder for the uploaded artifacts

---

## Triggering the Pipeline

### Automatic Triggers

| Event | Behavior |
|-------|----------|
| Push to `main` | Full pipeline (lint + test + build + upload) |
| Pull request to `main` | Lint + test only (no build, no upload) |

### Manual Trigger

1. Go to **Actions** tab in GitHub
2. Select **Waselak CI/CD** from the left sidebar
3. Click **Run workflow**
4. Optionally check **Skip Google Drive upload** if you just want to build
5. Click **Run workflow**

---

## Artifact Naming Convention

Format: `{app}-v{version}-{DDMMYY}-{debug|release}-{platform}.{ext}`

| Part | Source | Example |
|------|--------|---------|
| `{app}` | Module name | `manager`, `cashier`, `delivery` |
| `{version}` | `gradle.properties` → `APP_VERSION_NAME` | `1.0.0` |
| `{DDMMYY}` | Build date | `110326` (March 11, 2026) |
| `{debug\|release}` | Build type | `debug` or `release` |
| `{platform}` | Target platform | `android`, `linux`, `macos`, `windows` |

### All 15 Artifacts Per Version

**Android (6 files):**
```
manager-v1.0.0-110326-debug-android.apk
manager-v1.0.0-110326-release-android.apk
cashier-v1.0.0-110326-debug-android.apk
cashier-v1.0.0-110326-release-android.apk
delivery-v1.0.0-110326-debug-android.apk
delivery-v1.0.0-110326-release-android.apk
```

**Desktop (9 files):**
```
manager-v1.0.0-110326-release-linux.deb
cashier-v1.0.0-110326-release-linux.deb
delivery-v1.0.0-110326-release-linux.deb
manager-v1.0.0-110326-release-macos.dmg
cashier-v1.0.0-110326-release-macos.dmg
delivery-v1.0.0-110326-release-macos.dmg
manager-v1.0.0-110326-release-windows.msi
cashier-v1.0.0-110326-release-windows.msi
delivery-v1.0.0-110326-release-windows.msi
```

---

## Google Drive Folder Structure

```
Waselak Releases/
  v1.0.0/
    debug/
      manager-v1.0.0-110326-debug-android.apk
      cashier-v1.0.0-110326-debug-android.apk
      delivery-v1.0.0-110326-debug-android.apk
    release/
      manager-v1.0.0-110326-release-android.apk
      cashier-v1.0.0-110326-release-android.apk
      delivery-v1.0.0-110326-release-android.apk
      manager-v1.0.0-110326-release-linux.deb
      cashier-v1.0.0-110326-release-linux.deb
      delivery-v1.0.0-110326-release-linux.deb
      manager-v1.0.0-110326-release-macos.dmg
      cashier-v1.0.0-110326-release-macos.dmg
      delivery-v1.0.0-110326-release-macos.dmg
      manager-v1.0.0-110326-release-windows.msi
      cashier-v1.0.0-110326-release-windows.msi
      delivery-v1.0.0-110326-release-windows.msi
  v1.1.0/
    debug/
      ...
    release/
      ...
```

Each version folder is **auto-created** by the CI pipeline. The `debug/` subfolder contains Android debug APKs, and the `release/` subfolder contains all release builds (Android + Desktop).

---

## Troubleshooting

### "No signing config" error
Make sure all 4 keystore secrets are set: `KEYSTORE_BASE64`, `KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS`, `KEYSTORE_KEY_PASSWORD`.

### "Google Drive upload failed"
1. Verify `GOOGLE_DRIVE_CREDENTIALS` is the **base64-encoded** JSON (not raw JSON)
2. Verify the Drive folder is shared with the service account email
3. Verify `GOOGLE_DRIVE_FOLDER_ID` is the correct folder ID

### Desktop build fails on one platform
Each platform builds independently (`fail-fast: false`). A Windows failure won't cancel Linux/macOS builds. Check the specific job's logs.

### Pipeline skips build jobs on PRs
This is by design. PRs only run lint + test. Build + upload only run on pushes to `main`.

### How to rebuild without re-running tests
Use manual dispatch: **Actions > Waselak CI/CD > Run workflow**. All jobs will run from scratch.
