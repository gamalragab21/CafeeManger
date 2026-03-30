# Waselak Deployment Guide

## Full Deployment Cycle

```
YOU: Make code changes → commit → push to main
YOU: Create tag → git tag v1.5.0 → git push origin v1.5.0
  ↓
CI/CD (GitHub Actions — automatic):
  ├── Job 1: Lint (5 min)
  ├── Job 2: Test (5 min)
  ├── Job 3: Build Android APKs — debug + release (15 min)
  ├── Job 4: Build Backend JAR (5 min)
  ├── Job 5: Build Desktop — Linux, macOS, Windows (20 min)
  └── Job 6: Release + Deploy (10 min)
       ├── Upload ALL files to GitHub Releases
       ├── Send APKs + links to Telegram group
       └── Call VPS webhook → deploy backend
           ↓
VPS (automatic):
  ├── Download waselak-backend.jar from GitHub Releases
  ├── Replace old JAR
  ├── Restart release backend (port 8080)
  ├── Restart debug backend (port 8081)
  ├── Health check both
  └── Log to /opt/waselak/history/deploys.log
```

## Commands You Run (only 3)

```bash
# Step 1: Commit your changes
git add -A && git commit -m "your message"

# Step 2: Push to main
git push origin main

# Step 3: Create and push tag
git tag -a v1.5.0 -m "v1.5.0" && git push origin v1.5.0
```

Everything else is automatic.

---

## GitHub Repository Secrets

| Secret | Purpose |
|--------|---------|
| `KEYSTORE_BASE64` | Sign Android APKs |
| `KEYSTORE_STORE_PASSWORD` | Sign Android APKs |
| `KEYSTORE_KEY_ALIAS` | Sign Android APKs |
| `KEYSTORE_KEY_PASSWORD` | Sign Android APKs |
| `RELEASE_BASE_URL` | Release app backend URL |
| `RELEASE_HMAC_SECRET` | Release app HMAC signature |
| `RELEASE_SENTRY_DSN` | Sentry error tracking |
| `TELEGRAM_BOT_TOKEN` | Send builds to Telegram |
| `TELEGRAM_CHAT_ID` | Telegram group ID |
| `VPS_DEPLOY_TOKEN` | Trigger VPS deploy webhook |

## VPS Files (`/opt/waselak/`)

| File | Purpose |
|------|---------|
| `waselak-backend.jar` | The running backend |
| `release.env` | Production config (HMAC, DB, JWT secrets) |
| `debug.env` | Debug config (different HMAC, DB) |
| `deploy.sh` | Download JAR + restart services |
| `webhook.py` | HTTP listener for CI/CD deploy trigger |
| `CURRENT_VERSION` | Which tag is currently running |
| `history/deploys.log` | All deploy history with timestamps |
| `release.log` | Release backend output logs |
| `debug.log` | Debug backend output logs |

## VPS Services (systemd)

| Service | What | Port |
|---------|------|------|
| `waselak-release` | Production backend | 8080 |
| `waselak-debug` | Debug backend | 8081 |
| `waselak-webhook` | Deploy webhook listener | 9090 |
| `postgres-prod` (Docker) | Production PostgreSQL | 5432 |
| `postgres-dev` (Docker) | Debug PostgreSQL | 5433 |

## VPS URLs

| URL | Purpose |
|-----|---------|
| `http://187.124.47.222:8080` | Release backend (production) |
| `http://187.124.47.222:8081` | Debug backend (development) |
| `http://187.124.47.222:9090` | Deploy webhook (CI/CD only) |

## Local Config Files

| File | In Git? | Purpose |
|------|---------|---------|
| `env/debug.properties` | Yes | Debug app: BASE_URL + HMAC |
| `env/release.properties` | No (git-ignored) | Release app: BASE_URL + HMAC |
| `env/backend-debug.env` | Yes | Backend debug: DB, JWT, HMAC |
| `env/backend-release.env` | No (git-ignored) | Backend production: DB, JWT, HMAC |

## How Secrets Flow

### Debug Apps:
```
env/debug.properties → BASE_URL=http://187.124.47.222:8081
                      → HMAC=0003e1...
App talks to → VPS :8081 (debug backend)
Backend reads → /opt/waselak/debug.env → HMAC=0003e1... ✅ MATCH
```

### Release Apps:
```
env/release.properties → BASE_URL=http://187.124.47.222:8080
                        → HMAC=2316e4...
CI/CD gets from → RELEASE_BASE_URL + RELEASE_HMAC_SECRET secrets
App talks to → VPS :8080 (release backend)
Backend reads → /opt/waselak/release.env → HMAC=2316e4... ✅ MATCH
```

## HMAC Keys

| Environment | HMAC Secret |
|-------------|-------------|
| Debug | `0003e100bafedf7a06d298c612cce6560bfd29dfad656d23303b7b0f05ac4ab2` |
| Release | `2316e41b2ad2b7aa1952839d417d3de4a9e0154ed47e3c7ab6551c2ce6a7e42c` |

Debug apps only talk to debug backend. Release apps only talk to release backend.
They cannot mix — different HMAC secrets.

## VPS Manual Commands

```bash
# Check current version
cat /opt/waselak/CURRENT_VERSION

# Check deploy history
cat /opt/waselak/history/deploys.log

# Check service status
systemctl status waselak-release
systemctl status waselak-debug
systemctl status waselak-webhook

# View logs
tail -50 /opt/waselak/release.log
tail -50 /opt/waselak/debug.log

# Restart services
systemctl restart waselak-release
systemctl restart waselak-debug

# Manual deploy specific tag
/opt/waselak/deploy.sh v1.5.0

# Update admin password (change 'NEW_PASSWORD' to your password)
python3 -c "
import bcrypt
password = 'NEW_PASSWORD'
hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt(12)).decode()
print(hashed)
"
# If bcrypt not installed:
apt install -y python3-bcrypt 2>/dev/null || python3 -m pip install bcrypt -q
# Copy the hash output, then run:
docker exec -it postgres-prod psql -U waselak_prod -d waselak_prod -c "UPDATE admin_users SET password_hash = 'PASTE_HASH_HERE' WHERE email = 'gamalragab217@gmail.com';"
# For debug database:
docker exec -it postgres-dev psql -U waselak_db -d waselak_db -c "UPDATE admin_users SET password_hash = 'PASTE_HASH_HERE' WHERE email = 'gamalragab217@gmail.com';"

# Reset database completely (WARNING: deletes all data)
docker stop postgres-prod && docker rm postgres-prod && docker volume rm pgdata_prod
docker run -d --name postgres-prod -e POSTGRES_USER=waselak_prod -e POSTGRES_PASSWORD=W4s3l4kPr0d2026 -e POSTGRES_DB=waselak_prod -p 5432:5432 --restart unless-stopped -v pgdata_prod:/var/lib/postgresql/data postgres:16-alpine
nano /opt/waselak/release.env    # update ADMIN_PASSWORD before restart
systemctl restart waselak-release

# Update any config (HMAC, DB, JWT, etc.)
nano /opt/waselak/release.env    # edit release config
nano /opt/waselak/debug.env      # edit debug config
systemctl restart waselak-release waselak-debug

# Health check
curl http://localhost:8080/health
curl http://localhost:8081/health

# Check Docker containers (PostgreSQL)
docker ps

# Check disk usage
df -h /

# Full VPS status (one command)
echo "=== Current Version ===" && cat /opt/waselak/CURRENT_VERSION 2>/dev/null || echo "Not deployed yet" && echo "" && echo "=== JAR File ===" && ls -lh /opt/waselak/waselak-backend.jar && echo "" && echo "=== Release Env ===" && cat /opt/waselak/release.env && echo "" && echo "=== Debug Env ===" && cat /opt/waselak/debug.env && echo "" && echo "=== Deploy History ===" && cat /opt/waselak/history/deploys.log 2>/dev/null || echo "No deploys yet" && echo "" && echo "=== Services Status ===" && systemctl is-active waselak-release waselak-debug waselak-webhook && echo "" && echo "=== Health ===" && curl -s http://localhost:8080/health && echo "" && curl -s http://localhost:8081/health
```

## Hostinger Firewall Rules

| Protocol | Port | Source | Purpose |
|----------|------|--------|---------|
| TCP | 22 | any | SSH |
| TCP | 2222 | any | SSH (alternate) |
| TCP | 80 | any | HTTP |
| TCP | 443 | any | HTTPS |
| TCP | 8080 | any | Release backend |
| TCP | 8081 | any | Debug backend |
| TCP | 9090 | any | Deploy webhook |
| ICMP | any | any | Ping |

## Sentry Configuration

- **DSN:** Same for all apps and backend
- **Environment:** `development` (debug) or `production` (release)
- **Tags:** `app` (cashier/manager/kds/delivery/backend), `platform` (android/macos/windows/linux/server)
- **Dashboard:** https://sentry.io → Login → Select project
