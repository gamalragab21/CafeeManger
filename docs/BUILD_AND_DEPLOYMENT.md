# Waselak - Build, Configuration & Deployment Guide

## Table of Contents

1. [Project Overview](#project-overview)
2. [Quick Start (Development)](#quick-start-development)
3. [How to Change Environment Variables](#how-to-change-environment-variables)
4. [Secret Variables Explained](#secret-variables-explained)
5. [HMAC Secret - Mobile & Backend Must Match](#hmac-secret---mobile--backend-must-match)
6. [Build Variants (Debug vs Release)](#build-variants-debug-vs-release)
7. [Mobile Apps Configuration](#mobile-apps-configuration)
8. [Backend Configuration](#backend-configuration)
9. [Database Configuration](#database-configuration)
10. [Android APK Generation](#android-apk-generation)
11. [Desktop App Generation](#desktop-app-generation)
12. [Backend Deployment](#backend-deployment)
13. [Version Management](#version-management)
14. [Android Release Signing](#android-release-signing)
15. [Running Tests](#running-tests)
16. [All Gradle Tasks Reference](#all-gradle-tasks-reference)
17. [File Reference](#file-reference)

---

## Project Overview

| Module | Description | Platforms |
|--------|-------------|-----------|
| `app-manager` | Restaurant manager dashboard | Android, Desktop (macOS/Windows/Linux), iOS |
| `app-cashier` | Cashier POS system | Android, Desktop, iOS |
| `app-delivery` | Delivery driver app | Android, Desktop, iOS |
| `backend` | Ktor REST API + PostgreSQL | JVM (Docker) |

---

## Quick Start (Development)

```bash
# 1. Start the backend (includes PostgreSQL via Docker)
cd backend && chmod +x run-dev.sh && ./run-dev.sh --docker

# 2. Run a mobile app (in another terminal)
cd .. && ./gradlew :app-manager:run          # Desktop
# or
./gradlew :app-manager:assembleDebug         # Android APK
```

That's it. Debug config auto-loads from `env/debug.properties` and the backend `run-dev.sh` already has matching secrets.

---

## How to Change Environment Variables

### Where to Edit (Quick Reference)

| What you want to change | Debug / Development | Release / Production |
|--------------------------|--------------------|--------------------|
| Mobile API URL, HMAC, Sentry | `env/debug.properties` | `env/release.properties` |
| Backend secrets (JWT, HMAC, Admin) | `backend/run-dev.sh` | `backend/.env.prod` |
| Backend secrets (Docker) | `backend/docker-compose.yml` | `backend/.env.prod` |
| Database URL/user/password | `backend/docker-compose.yml` or `application.conf` | `backend/.env.prod` |
| App version | `gradle.properties` | `gradle.properties` |
| Android signing keystore | N/A (uses debug key) | `keystore/keystore.properties` |

### Changing Backend Variables (Development)

**If you run with `./run-dev.sh`** (Gradle directly):

Edit `backend/run-dev.sh` lines 29-34:

```bash
export HMAC_SECRET="your-new-hmac-secret"
export JWT_SECRET="your-new-jwt-secret"
export ADMIN_JWT_SECRET="your-new-admin-jwt-secret"
export ADMIN_NAME="Your Name"
export ADMIN_EMAIL="your@email.com"
export ADMIN_PASSWORD="your-password"
```

**If you run with `./run-dev.sh --docker`** (Docker Compose):

Edit `backend/docker-compose.yml` under `backend > environment`:

```yaml
environment:
  HMAC_SECRET: your-new-hmac-secret
  JWT_SECRET: your-new-jwt-secret
  ADMIN_JWT_SECRET: your-new-admin-jwt-secret
  ADMIN_NAME: Your Name
  ADMIN_EMAIL: your@email.com
  ADMIN_PASSWORD: "your-password"
```

### Changing Backend Variables (Production)

Edit `backend/.env.prod`:

```properties
HMAC_SECRET=your-production-hmac-secret
JWT_SECRET=your-production-jwt-secret
ADMIN_JWT_SECRET=your-production-admin-jwt-secret
ADMIN_NAME=Admin
ADMIN_EMAIL=admin@yourdomain.com
ADMIN_PASSWORD=strong-production-password
```

Then restart: `./run-prod.sh --docker`

### Changing Mobile Variables

Edit `env/debug.properties` (for debug) or `env/release.properties` (for release):

```properties
BASE_URL=https://your-api-url.com
HMAC_SECRET=must-match-backend-hmac-secret
SENTRY_DSN=https://your-sentry-dsn
```

Then rebuild the app.

---

## Secret Variables Explained

### All Variables and What They Do

| Variable | Where Used | What It Does |
|----------|-----------|--------------|
| **`HMAC_SECRET`** | Mobile + Backend | Signs every API request. Mobile signs the request, backend verifies it. **Must be identical in both.** |
| **`JWT_SECRET`** | Backend only | Signs user login tokens (JWT). Used to authenticate cashiers, delivery drivers, managers. |
| **`ADMIN_JWT_SECRET`** | Backend only | Signs admin dashboard tokens. Separate from user JWT for extra security. |
| **`ADMIN_NAME`** | Backend only | Name of the initial admin account created on first startup. |
| **`ADMIN_EMAIL`** | Backend only | Email for admin login. |
| **`ADMIN_PASSWORD`** | Backend only | Password for admin login. |
| **`BASE_URL`** | Mobile only | The API URL that mobile apps connect to (e.g., ngrok URL for dev, domain for prod). |
| **`SENTRY_DSN`** | Mobile only | Crash reporting endpoint. |
| **`DATABASE_URL`** | Backend only | PostgreSQL connection string. |
| **`DATABASE_USER`** | Backend only | PostgreSQL username. |
| **`DATABASE_PASSWORD`** | Backend only | PostgreSQL password. |

### How to Generate New Secrets

```bash
# Generate a random 64-character hex secret
openssl rand -hex 32

# Generate a random base64 password
openssl rand -base64 24
```

---

## HMAC Secret - Mobile & Backend Must Match

The HMAC secret is the **one variable that must be identical** between mobile apps and the backend. If they don't match, all API requests will fail with signature verification errors.

### Current Values (Development)

| File | HMAC_SECRET Value |
|------|-------------------|
| `env/debug.properties` | `0003e100bafedf7a06d298c612cce6560bfd29dfad656d23303b7b0f05ac4ab2` |
| `backend/run-dev.sh` | `0003e100bafedf7a06d298c612cce6560bfd29dfad656d23303b7b0f05ac4ab2` |
| `backend/docker-compose.yml` | `0003e100bafedf7a06d298c612cce6560bfd29dfad656d23303b7b0f05ac4ab2` |

### Current Values (Production)

| File | HMAC_SECRET Value |
|------|-------------------|
| `env/release.properties` | `2316e41b2ad2b7aa1952839d417d3de4a9e0154ed47e3c7ab6551c2ce6a7e42c` |
| `backend/.env.prod` | `2316e41b2ad2b7aa1952839d417d3de4a9e0154ed47e3c7ab6551c2ce6a7e42c` |

### How to Change the HMAC Secret

```bash
# 1. Generate a new secret
openssl rand -hex 32
# Output: a1b2c3d4e5f6...

# 2. Update BOTH mobile and backend with the SAME value:
#    For debug:  env/debug.properties  AND  backend/run-dev.sh  AND  backend/docker-compose.yml
#    For release: env/release.properties  AND  backend/.env.prod
```

---

## Build Variants (Debug vs Release)

### Summary Table

| Aspect | Debug | Release |
|--------|-------|---------|
| Mobile config | `env/debug.properties` | `env/release.properties` |
| Backend config | `run-dev.sh` / `docker-compose.yml` | `run-prod.sh` / `docker-compose.prod.yml` |
| Backend database | `waselak_db` (port 5432) | `waselak_prod` (port 5433) |
| BuildConfig.IS_DEBUG | `true` | `false` |
| Android signing | Debug keystore (auto) | Release keystore (`keystore/`) |
| Android minification | Off | R8/ProGuard enabled |
| API URL | ngrok / localhost | `https://api.waselak.net` |

### How Auto-Detection Works

The build system reads the Gradle task name and picks the environment automatically:

| You run this task | It loads |
|-------------------|----------|
| `assembleDebug`, `run` | `env/debug.properties` |
| `assembleRelease`, `packageDmg`, `packageMsi`, `packageDeb` | `env/release.properties` |

**Force a specific environment:**

```bash
./gradlew :app-manager:run -PBUILD_ENV=release    # Force release config on debug task
./gradlew :app-manager:assembleDebug -PBUILD_ENV=release  # Force release config
```

---

## Mobile Apps Configuration

### Config Files

```
env/
  debug.properties            # Development config (committed to git)
  release.properties          # Production config (git-ignored)
  release.properties.template # Template for onboarding (committed)
```

### debug.properties (current values)

```properties
BASE_URL=https://orogenetic-pained-lasandra.ngrok-free.dev
HMAC_SECRET=0003e100bafedf7a06d298c612cce6560bfd29dfad656d23303b7b0f05ac4ab2
SENTRY_DSN=https://0dbf8a87fc27547d8546a9ba65761cee@o4510973812015104.ingest.de.sentry.io/4511000890572880
```

### release.properties (current values)

```properties
BASE_URL=https://api.waselak.net
HMAC_SECRET=2316e41b2ad2b7aa1952839d417d3de4a9e0154ed47e3c7ab6551c2ce6a7e42c
SENTRY_DSN=https://0dbf8a87fc27547d8546a9ba65761cee@o4510973812015104.ingest.de.sentry.io/4511000890572880
```

### Generated BuildConfig (accessible in code)

```kotlin
import net.marllex.waselak.config.BuildConfig

BuildConfig.BASE_URL       // API URL
BuildConfig.HMAC_SECRET    // HMAC secret for request signing
BuildConfig.SENTRY_DSN     // Sentry crash reporting
BuildConfig.IS_DEBUG       // true = debug, false = release
BuildConfig.VERSION_NAME   // e.g., "1.0.0"
BuildConfig.VERSION_CODE   // e.g., 1
```

### Setting Up on a New Machine

```bash
cp env/release.properties.template env/release.properties
# Edit env/release.properties with production values
# Make sure HMAC_SECRET matches backend/.env.prod
```

---

## Backend Configuration

### application.conf (the main config file)

Location: `backend/src/main/resources/application.conf`

This file uses HOCON format. Variables marked `${VAR}` are **required** (backend won't start without them). Variables marked `${?VAR}` are **optional** (use the default if not set).

```hocon
ktor {
    deployment {
        host = "0.0.0.0"        # Override: HOST env var
        port = 8080              # Override: PORT env var
    }
}

database {
    url = "jdbc:postgresql://localhost:5432/waselak_db"   # Override: DATABASE_URL
    user = "waselak_db"                                   # Override: DATABASE_USER
    password = "waselak_db_dev"                           # Override: DATABASE_PASSWORD
}

hmac {
    secret = ${HMAC_SECRET}       # REQUIRED - must match mobile apps
}

jwt {
    secret = ${JWT_SECRET}        # REQUIRED - user token signing
    issuer = "waselak"            # Override: JWT_ISSUER
    audience = "waselak-api"      # Override: JWT_AUDIENCE
}

admin-jwt {
    secret = ${ADMIN_JWT_SECRET}  # REQUIRED - admin token signing
}

admin {
    name = ${ADMIN_NAME}          # REQUIRED - initial admin name
    email = ${ADMIN_EMAIL}        # REQUIRED - initial admin email
    password = ${ADMIN_PASSWORD}  # REQUIRED - initial admin password
}
```

### Development Backend (run-dev.sh)

```bash
cd backend && ./run-dev.sh           # Gradle (PostgreSQL must be running)
cd backend && ./run-dev.sh --docker  # Docker (includes PostgreSQL)
```

Current dev values in `run-dev.sh`:

| Variable | Value |
|----------|-------|
| HMAC_SECRET | `0003e100bafe...4ab2` (matches `env/debug.properties`) |
| JWT_SECRET | `ec1e287139629ae6...b28f` |
| ADMIN_JWT_SECRET | `c629b29f1ada461a...6ef7` |
| ADMIN_NAME | Gamal Ragab |
| ADMIN_EMAIL | gamalragab217@gmail.com |
| ADMIN_PASSWORD | 123456 |

### Production Backend (.env.prod)

```bash
cd backend && ./run-prod.sh           # Gradle
cd backend && ./run-prod.sh --docker  # Docker (recommended)
```

Current production values in `.env.prod`:

| Variable | Value |
|----------|-------|
| POSTGRES_USER | `waselak_prod` |
| POSTGRES_PASSWORD | `B/QPiOT+O29Zc2bdW0NpgrYfAAdYHXZp` |
| POSTGRES_DB | `waselak_prod` |
| JWT_SECRET | `7f30d400062c...66a4` |
| ADMIN_JWT_SECRET | `c1c565b394a0...1326` |
| HMAC_SECRET | `2316e41b2ad2...e42c` (matches `env/release.properties`) |
| ADMIN_NAME | Gamal Ragab |
| ADMIN_EMAIL | gamalragab217@gmail.com |
| ADMIN_PASSWORD | `3LPonfO8DOmaYe0aH35V7Q==` |

---

## Database Configuration

### Where Is the Database URL?

**File:** `backend/src/main/resources/application.conf` line 14

```hocon
database {
    url = "jdbc:postgresql://localhost:5432/waselak_db"
    url = ${?DATABASE_URL}
}
```

The second line means: if `DATABASE_URL` env var exists, use it instead.

### How to Change the Database

**Development (local):**

```bash
# Option 1: Change default in application.conf
url = "jdbc:postgresql://localhost:5432/my_custom_db"

# Option 2: Set env var before running
export DATABASE_URL="jdbc:postgresql://localhost:5432/my_custom_db"
cd backend && ./gradlew run
```

**Development (Docker):**

Edit `backend/docker-compose.yml`:

```yaml
postgres:
  environment:
    POSTGRES_DB: my_custom_db        # Change database name
    POSTGRES_USER: my_user           # Change username
    POSTGRES_PASSWORD: my_password   # Change password

backend:
  environment:
    DATABASE_URL: jdbc:postgresql://postgres:5432/my_custom_db
    DATABASE_USER: my_user
    DATABASE_PASSWORD: my_password
```

**Production:**

Edit `backend/.env.prod`:

```properties
POSTGRES_DB=waselak_prod
POSTGRES_USER=waselak_prod
POSTGRES_PASSWORD=your-strong-password
```

The `docker-compose.prod.yml` and `run-prod.sh` automatically read from `.env.prod`.

### Two Separate Databases

| Environment | Database | Port | Docker Container |
|-------------|----------|------|-----------------|
| Development | `waselak_db` | 5432 | `waselak-postgres` |
| Production | `waselak_prod` | 5433 | `waselak-postgres-prod` |

You can run both simultaneously. They don't interfere with each other.

---

## Android APK Generation

### Debug APKs

```bash
# Single app
./gradlew :app-manager:assembleDebug
./gradlew :app-cashier:assembleDebug
./gradlew :app-delivery:assembleDebug

# All 3 apps at once
./gradlew assembleAllDebug
```

Output:

```
app-manager/build/outputs/apk/debug/app-manager-debug.apk
app-cashier/build/outputs/apk/debug/app-cashier-debug.apk
app-delivery/build/outputs/apk/debug/app-delivery-debug.apk
```

### Release APKs (signed + minified)

```bash
# Single app
./gradlew :app-manager:assembleRelease
./gradlew :app-cashier:assembleRelease
./gradlew :app-delivery:assembleRelease

# All 3 apps at once
./gradlew assembleAllRelease
```

Output:

```
app-manager/build/outputs/apk/release/app-manager-release.apk
app-cashier/build/outputs/apk/release/app-cashier-release.apk
app-delivery/build/outputs/apk/release/app-delivery-release.apk
```

### AAB Bundles (for Google Play Store)

```bash
./gradlew :app-manager:bundleRelease
./gradlew :app-cashier:bundleRelease
./gradlew :app-delivery:bundleRelease
```

Output:

```
app-manager/build/outputs/bundle/release/app-manager-release.aab
app-cashier/build/outputs/bundle/release/app-cashier-release.aab
app-delivery/build/outputs/bundle/release/app-delivery-release.aab
```

---

## Desktop App Generation

All desktop builds automatically use **release** config (`env/release.properties`).

### macOS (.dmg)

```bash
./gradlew :app-manager:packageDmg
./gradlew :app-cashier:packageDmg
./gradlew :app-delivery:packageDmg

# All 3 at once
./gradlew packageAllDesktopDmg
```

Output: `app-{name}/build/compose/binaries/main/dmg/`

### Windows (.msi)

```bash
./gradlew :app-manager:packageMsi
./gradlew :app-cashier:packageMsi
./gradlew :app-delivery:packageMsi

# All 3 at once
./gradlew packageAllDesktopMsi
```

Output: `app-{name}/build/compose/binaries/main/msi/`

### Linux (.deb)

```bash
./gradlew :app-manager:packageDeb
./gradlew :app-cashier:packageDeb
./gradlew :app-delivery:packageDeb

# All 3 at once
./gradlew packageAllDesktopDeb
```

Output: `app-{name}/build/compose/binaries/main/deb/`

### Uber JAR (runs on any OS with Java)

```bash
./gradlew :app-manager:packageUberJarForCurrentOS
./gradlew :app-cashier:packageUberJarForCurrentOS
./gradlew :app-delivery:packageUberJarForCurrentOS
```

Output: `app-{name}/build/compose/jars/`

Run: `java -jar app-manager-macos-arm64-1.0.0.jar`

---

## Backend Deployment

### Development

```bash
cd backend

# Option A: Docker (recommended, includes PostgreSQL)
./run-dev.sh --docker

# Option B: Gradle only (PostgreSQL must already be running on localhost:5432)
./run-dev.sh
```

### Production

```bash
cd backend

# 1. Create production secrets (one-time)
cp .env.prod.template .env.prod
# Edit .env.prod with real values

# 2. Start
./run-prod.sh --docker

# 3. View logs
docker compose -f docker-compose.prod.yml logs -f

# 4. Stop
docker compose -f docker-compose.prod.yml down

# 5. Restart after changing .env.prod
docker compose -f docker-compose.prod.yml down
./run-prod.sh --docker
```

---

## Version Management

Single source of truth in `gradle.properties`:

```properties
APP_VERSION_NAME=1.0.0
APP_VERSION_CODE=1
```

Used automatically by:
- **Android**: `versionName` + `versionCode` in manifest
- **Desktop**: `packageVersion` in DMG/MSI/DEB filenames
- **Code**: `BuildConfig.VERSION_NAME` + `BuildConfig.VERSION_CODE`

### Bump the Version

Edit `gradle.properties`:

```properties
APP_VERSION_NAME=1.1.0
APP_VERSION_CODE=2
```

All apps automatically pick up the new version on next build.

---

## Android Release Signing

### Setup (one-time)

```bash
# 1. Generate keystore (skip if keystore/waselak-release.jks already exists)
keytool -genkeypair -v \
  -keystore keystore/waselak-release.jks \
  -alias waselak \
  -keyalg RSA -keysize 2048 \
  -validity 10000

# 2. Create credentials file
cp keystore/keystore.properties.template keystore/keystore.properties
```

Edit `keystore/keystore.properties`:

```properties
STORE_FILE=../keystore/waselak-release.jks
STORE_PASSWORD=your-store-password
KEY_ALIAS=waselak
KEY_PASSWORD=your-key-password
```

### Git-Ignored Files

| File | Status | Description |
|------|--------|-------------|
| `keystore/waselak-release.jks` | **Ignored** | The actual keystore |
| `keystore/keystore.properties` | **Ignored** | Keystore credentials |
| `keystore/keystore.properties.template` | Committed | Template for new devs |

---

## Running Tests

### Backend (48 tests)

```bash
cd backend && ./gradlew test
```

| Test Class | Tests | Covers |
|------------|-------|--------|
| OrderServiceTest | 15 | Order status transitions per channel |
| PinServiceTest | 20 | PIN validation, hashing, rate limiting |
| QrCodeServiceTest | 11 | QR generation, image output, validation |
| HealthRouteTest | 2 | Health endpoint status + content type |

### KMP Core Model

```bash
./gradlew :core:core-model:desktopTest
```

Covers: OrderStatus transitions, `getAvailableStatuses()`, `parse()`, enum coverage.

### Feature ViewModel (28 tests)

```bash
./gradlew :feature:feature-manager-orders:desktopTest
```

Covers: filters, state management, dialog lifecycle, error handling.

### Run Everything

```bash
./gradlew :backend:test :core:core-model:desktopTest :feature:feature-manager-orders:desktopTest
```

---

## All Gradle Tasks Reference

### Build All Apps

| Command | What It Does |
|---------|-------------|
| `./gradlew assembleAllDebug` | Debug APKs for manager, cashier, delivery |
| `./gradlew assembleAllRelease` | Signed release APKs for all 3 apps |
| `./gradlew packageAllDesktopDmg` | macOS DMG for all 3 apps |
| `./gradlew packageAllDesktopMsi` | Windows MSI for all 3 apps |
| `./gradlew packageAllDesktopDeb` | Linux DEB for all 3 apps |

### Build Single App - Manager

| Command | Output |
|---------|--------|
| `./gradlew :app-manager:assembleDebug` | Debug APK |
| `./gradlew :app-manager:assembleRelease` | Signed release APK |
| `./gradlew :app-manager:bundleRelease` | AAB for Play Store |
| `./gradlew :app-manager:packageDmg` | macOS DMG |
| `./gradlew :app-manager:packageMsi` | Windows MSI |
| `./gradlew :app-manager:packageDeb` | Linux DEB |
| `./gradlew :app-manager:packageUberJarForCurrentOS` | Cross-platform JAR |
| `./gradlew :app-manager:run` | Run desktop app (debug) |

### Build Single App - Cashier

| Command | Output |
|---------|--------|
| `./gradlew :app-cashier:assembleDebug` | Debug APK |
| `./gradlew :app-cashier:assembleRelease` | Signed release APK |
| `./gradlew :app-cashier:bundleRelease` | AAB for Play Store |
| `./gradlew :app-cashier:packageDmg` | macOS DMG |
| `./gradlew :app-cashier:run` | Run desktop app (debug) |

### Build Single App - Delivery

| Command | Output |
|---------|--------|
| `./gradlew :app-delivery:assembleDebug` | Debug APK |
| `./gradlew :app-delivery:assembleRelease` | Signed release APK |
| `./gradlew :app-delivery:bundleRelease` | AAB for Play Store |
| `./gradlew :app-delivery:packageDmg` | macOS DMG |
| `./gradlew :app-delivery:run` | Run desktop app (debug) |

### Backend

| Command | What It Does |
|---------|-------------|
| `cd backend && ./run-dev.sh` | Run backend (development, Gradle) |
| `cd backend && ./run-dev.sh --docker` | Run backend + PostgreSQL (Docker) |
| `cd backend && ./run-prod.sh` | Run backend (production, Gradle) |
| `cd backend && ./run-prod.sh --docker` | Run backend + PostgreSQL (Docker, production) |
| `cd backend && ./gradlew test` | Run all backend tests |

### Tests

| Command | What It Does |
|---------|-------------|
| `cd backend && ./gradlew test` | Backend tests (48 tests) |
| `./gradlew :core:core-model:desktopTest` | Core model tests |
| `./gradlew :feature:feature-manager-orders:desktopTest` | ViewModel tests (28 tests) |

### Environment Override

```bash
# Force release config on any task
./gradlew :app-manager:run -PBUILD_ENV=release
```

---

## File Reference

### Config Files - Where to Change What

| What to Change | Debug File | Production File |
|----------------|-----------|-----------------|
| Mobile API URL, HMAC, Sentry | `env/debug.properties` | `env/release.properties` |
| Backend JWT, HMAC, Admin (Gradle) | `backend/run-dev.sh` | `backend/.env.prod` |
| Backend JWT, HMAC, Admin (Docker) | `backend/docker-compose.yml` | `backend/.env.prod` |
| Database connection | `backend/docker-compose.yml` | `backend/.env.prod` |
| App version | `gradle.properties` | `gradle.properties` |
| Android signing | N/A (debug key) | `keystore/keystore.properties` |

### All Config Files

| File | Git | Purpose |
|------|-----|---------|
| `env/debug.properties` | Committed | Mobile debug config |
| `env/release.properties` | **Ignored** | Mobile production config |
| `env/release.properties.template` | Committed | Template for onboarding |
| `gradle.properties` | Committed | App version + Gradle settings |
| `backend/src/main/resources/application.conf` | Committed | Backend HOCON config |
| `backend/run-dev.sh` | Committed | Dev startup script with secrets |
| `backend/run-prod.sh` | Committed | Production startup script |
| `backend/docker-compose.yml` | Committed | Dev Docker setup |
| `backend/docker-compose.prod.yml` | Committed | Production Docker setup |
| `backend/.env.prod` | **Ignored** | Production secrets |
| `backend/.env.prod.template` | Committed | Template for production secrets |
| `keystore/waselak-release.jks` | **Ignored** | Android signing keystore |
| `keystore/keystore.properties` | **Ignored** | Keystore credentials |
| `keystore/keystore.properties.template` | Committed | Template for signing setup |

### Build Logic

| File | Purpose |
|------|---------|
| `build-logic/.../KmpApplicationConventionPlugin.kt` | Auto-detects env, generates BuildConfig, configures signing |
| `build-logic/.../KmpFeatureConventionPlugin.kt` | Feature module dependencies + test deps |
| `build-logic/.../KmpConfiguration.kt` | KMP target configuration (Android, Desktop, iOS) |

### ProGuard Rules

| File | Includes |
|------|----------|
| `app-manager/proguard-rules.pro` | Serialization, Ktor, Koin, Coil, SQLDelight, Sentry, ZXing |
| `app-cashier/proguard-rules.pro` | Serialization, Ktor, Koin, Coil, SQLDelight, Sentry |
| `app-delivery/proguard-rules.pro` | Serialization, Ktor, Koin, Coil, SQLDelight, Sentry, Google Maps |
