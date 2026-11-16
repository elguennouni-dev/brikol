#!/usr/bin/env bash
# Run on the server, from this folder:  bash deploy.sh
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p downloads
# The secrets must never change once the database exists (MySQL keeps the password it was created with).
# A copy of .env is kept outside the project folder, so a fresh clone / unzip / git reset cannot lose it.
SAFE_DIR="${SANAI3I_SECRETS:-$HOME/.sanai3i}"
SAFE="$SAFE_DIR/deploy.env"
VOLUME="$(basename "$PWD")_dbdata"
if [ ! -f .env ] && [ -f "$SAFE" ]; then
  cp "$SAFE" .env && chmod 600 .env
  echo "Restored .env from $SAFE"
fi
if [ ! -f .env ]; then
  if docker volume inspect "$VOLUME" >/dev/null 2>&1; then
    echo "STOP: the database ($VOLUME) already exists but deploy/.env is missing."
    echo "New random passwords would not match the database. Either:"
    echo "  - put the old .env back in $(pwd)/.env  (search: find / -name .env -path '*deploy*' 2>/dev/null)"
    echo "  - or start from an EMPTY database (deletes all data):"
    echo "      docker compose -f docker-compose.prod.yml down -v && bash deploy.sh"
    exit 1
  fi
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
mkdir -p "$SAFE_DIR" && chmod 700 "$SAFE_DIR" && cp .env "$SAFE" && chmod 600 "$SAFE"
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
echo "App did not answer in time. Logs:"; docker compose -f docker-compose.prod.yml logs --tail=80 app
if docker compose -f docker-compose.prod.yml logs app 2>/dev/null | grep -q "Access denied for user"; then
  echo
  echo "=> The database was created with other passwords than the ones in deploy/.env."
  echo "   Put the old .env back, or reset the database (deletes all data):"
  echo "   docker compose -f docker-compose.prod.yml down -v && bash deploy.sh"
fi
exit 1
