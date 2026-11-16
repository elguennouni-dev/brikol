#!/usr/bin/env bash
# Run on the server, from this folder:  bash deploy.sh
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p downloads
if [ ! -f .env ]; then
  gen() { head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c "$1"; }
  cat > .env <<ENV
DB_PASSWORD=$(gen 24)
DB_ROOT_PASSWORD=$(gen 24)
JWT_SECRET=$(gen 64)
# true = creates demo artisans/clients (password demo1234). Set to false for real launch, then redeploy.
APP_SEED=true
ENV
  chmod 600 .env
  echo "Created .env with random secrets."
fi
gen() { head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c "$1"; }
# Admin account (created on first start if the phone is not registered yet)
if ! grep -q '^ADMIN_PASSWORD=' .env; then
  printf '\nADMIN_PHONE=0600000099\nADMIN_NAME=Administrateur\nADMIN_PASSWORD=%s\n' "$(gen 14)" >> .env
fi
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
echo
echo "Waiting for the app..."
for i in $(seq 1 60); do
  if curl -fs http://127.0.0.1:8080/api/categories >/dev/null; then
    echo "OK: backend is up"
    echo "Admin panel: https://snai3i.elguennouni.site/app/#/admin"
    echo "Admin phone:    $(grep '^ADMIN_PHONE=' .env | cut -d= -f2)"
    echo "Admin password: $(grep '^ADMIN_PASSWORD=' .env | cut -d= -f2)"
    exit 0
  fi
  sleep 3
done
echo "App did not answer in time. Logs:"; docker compose -f docker-compose.prod.yml logs --tail=80 app; exit 1
