#!/bin/bash
# =============================================================================
# VPS Initial Setup Script (run ONCE on a fresh Hostinger VPS)
# =============================================================================
# Usage: ssh root@YOUR_VPS_IP 'bash -s' < scripts/vps-setup.sh
#
# What it does:
# 1. Updates system packages
# 2. Installs Docker + Docker Compose
# 3. Installs Nginx + Certbot (SSL)
# 4. Creates deploy user
# 5. Sets up firewall
# 6. Creates app directory structure
# =============================================================================

set -euo pipefail

DEPLOY_USER="deploy"
APP_DIR="/opt/waselak"

echo "═══════════════════════════════════════════"
echo "  Waselak VPS Setup"
echo "═══════════════════════════════════════════"

# ── 1. Update system ─────────────────────────
echo "📦 Updating system..."
apt-get update -y && apt-get upgrade -y

# ── 2. Install Docker ────────────────────────
echo "🐳 Installing Docker..."
if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com | sh
    systemctl enable docker
    systemctl start docker
    echo "✅ Docker installed"
else
    echo "✅ Docker already installed"
fi

# Install Docker Compose plugin
apt-get install -y docker-compose-plugin 2>/dev/null || true

# ── 3. Install Nginx ─────────────────────────
echo "🌐 Installing Nginx..."
apt-get install -y nginx certbot python3-certbot-nginx
systemctl enable nginx
echo "✅ Nginx installed"

# ── 4. Create deploy user ───────────────────
echo "👤 Creating deploy user..."
if ! id "$DEPLOY_USER" &>/dev/null; then
    useradd -m -s /bin/bash "$DEPLOY_USER"
    usermod -aG docker "$DEPLOY_USER"
    mkdir -p /home/$DEPLOY_USER/.ssh
    # Copy root SSH keys to deploy user
    if [ -f /root/.ssh/authorized_keys ]; then
        cp /root/.ssh/authorized_keys /home/$DEPLOY_USER/.ssh/
        chown -R $DEPLOY_USER:$DEPLOY_USER /home/$DEPLOY_USER/.ssh
        chmod 700 /home/$DEPLOY_USER/.ssh
        chmod 600 /home/$DEPLOY_USER/.ssh/authorized_keys
    fi
    echo "✅ Deploy user created"
else
    echo "✅ Deploy user already exists"
fi

# ── 5. Setup firewall ───────────────────────
echo "🔒 Setting up firewall..."
apt-get install -y ufw
ufw default deny incoming
ufw default allow outgoing
ufw allow ssh
ufw allow http
ufw allow https
ufw --force enable
echo "✅ Firewall configured"

# ── 6. Create app directory ─────────────────
echo "📁 Creating app directory..."
mkdir -p "$APP_DIR"
chown -R $DEPLOY_USER:$DEPLOY_USER "$APP_DIR"

# Create Nginx config
cat > /etc/nginx/sites-available/waselak << 'NGINX'
server {
    listen 80;
    server_name _;

    # Backend API
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
        proxy_read_timeout 90s;
        client_max_body_size 50M;
    }
}
NGINX

ln -sf /etc/nginx/sites-available/waselak /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

echo ""
echo "═══════════════════════════════════════════"
echo "  ✅ VPS Setup Complete!"
echo "═══════════════════════════════════════════"
echo ""
echo "Next steps:"
echo "1. Point your domain to this VPS IP"
echo "2. Run SSL setup:"
echo "   certbot --nginx -d api.yourdomain.com"
echo ""
echo "3. Create .env.prod on the VPS:"
echo "   cp $APP_DIR/.env.prod.template $APP_DIR/.env.prod"
echo "   nano $APP_DIR/.env.prod"
echo ""
echo "4. Add these GitHub Secrets:"
echo "   VPS_HOST     = your VPS IP or domain"
echo "   VPS_USER     = deploy"
echo "   VPS_SSH_KEY  = (private SSH key for deploy user)"
echo ""
echo "5. Push to main → auto-deploys! 🚀"
echo ""
