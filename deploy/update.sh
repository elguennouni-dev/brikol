#!/usr/bin/env bash
# On the server: pulls the latest version from GitHub and restarts the app.
#   bash deploy/update.sh
set -euo pipefail
cd "$(dirname "$0")/.."
echo "Current version: $(git log -1 --format='%h %s')"
git fetch --quiet origin
git reset --hard origin/main      # local edits to tracked files are discarded; deploy/.env and downloads/ are kept (ignored)
echo "New version:     $(git log -1 --format='%h %s')"
bash deploy/deploy.sh
