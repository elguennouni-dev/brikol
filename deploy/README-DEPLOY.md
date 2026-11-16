# Deploy on the Contabo VPS (aaPanel + Docker)

Site: https://snai3i.elguennouni.site  (already created in aaPanel)

1. **Upload**: aaPanel > Files > go to `/www/` > Upload `sanai3i.zip` > right-click > Unzip here → folder `/www/sanai3i` (the zip already contains that folder).
2. **Terminal**: aaPanel > Terminal (or SSH):
   ```
   cd /www/sanai3i/deploy && bash deploy.sh
   ```
   The first build takes 3-6 minutes. It must end with `OK: backend is up`.
   (If `docker` is missing: aaPanel > Docker > install.)
3. **Reverse proxy**: aaPanel > Website > snai3i.elguennouni.site > Reverse proxy > Add:
   - Target URL: `http://127.0.0.1:8080`
   - Send domain: `$host`
4. **HTTPS**: same site > SSL > Let's Encrypt > tick the domain > Apply, then enable "Force HTTPS".
5. Open https://snai3i.elguennouni.site → landing page; /app/ → the app.

## Android APK
Put the signed APK at `/www/sanai3i/deploy/downloads/sanai3i.apk` (no restart needed).
The landing page enables the download button automatically.

## Update later
Upload the new zip, unzip over the folder, then `cd /www/sanai3i/deploy && bash deploy.sh` again. The data stays in the Docker volume.

## Real launch
Edit `deploy/.env`, set `APP_SEED=false`, rerun `bash deploy.sh`. (Demo users already created stay in the DB; to start clean: `docker compose -f docker-compose.prod.yml down -v` — this DELETES all data.)

## Backup
`docker compose -f docker-compose.prod.yml exec db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" sanai3i' > backup.sql`

## Admin account
`deploy.sh` adds `ADMIN_PHONE` (default 0600000099) and a random `ADMIN_PASSWORD` to `deploy/.env` and prints them at the end.
Sign in on the site with them, then open `https://snai3i.elguennouni.site/app/#/admin`. Change the values in `.env` and redeploy only before the first start:
the admin is created once (if the phone is not registered). Afterwards use "reset password" in the panel or the database.
