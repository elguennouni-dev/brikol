# Server hardening (from the pre-launch penetration test)

The application fixes ship with the code (`bash deploy/update.sh`). The steps below are on the server itself and
only need to be done once. Do them in this order.

## 1. Nginx: HTTPS only, modern TLS, real client IP (findings 1, 3, 10, 16)

1. aaPanel → **Website** → `snai3i.elguennouni.site` → **Config**.
2. Replace the whole file with [`deploy/nginx/brikol.conf`](nginx/brikol.conf). Keep the two `ssl_certificate` lines
   pointing to your certificate (aaPanel shows the paths under **SSL**).
3. Save. aaPanel tests the file and reloads Nginx. If it refuses, the old file stays: send me the error.

What it changes:
- `http://` answers `301` to `https://` (only the Let's Encrypt renewal path stays on port 80).
- TLS 1.2 and 1.3 only, forward-secret AES-GCM / ChaCha20 ciphers (no TLS 1.1, no CBC, no CCM8).
- `Strict-Transport-Security` on every answer.
- `X-Forwarded-For`, `-Proto`, `-Host`, `-Port`, `-Prefix` are **set by Nginx**, so a client can no longer fake its IP
  (rate limits, lockout and the audit log use the real address) or inject a host into redirects.

Check: `curl -sI http://snai3i.elguennouni.site/` → `301` with `Location: https://…`, and
`curl -sI https://snai3i.elguennouni.site/` shows `strict-transport-security`.

## 2. Unknown hosts / bare IP (finding 18)

Put [`deploy/nginx/default-server.conf`](nginx/default-server.conf) in `/www/server/panel/vhost/nginx/0.default.conf`
(replace the existing default site), then reload Nginx. Requests to `http://161.97.129.76/` get no answer instead of
the panel's default pages.

## 3. Firewall: close what the public does not need (finding 18)

aaPanel → **Security** (firewall):

| Port | Service | Action |
|---|---|---|
| 80, 443 | Nginx | keep open |
| 22 | SSH | keep, but key-only (step 4); or limit to your IP |
| 21 | Pure-FTPd | **close** (and stop FTP in **App Store** if you do not use it) |
| 888 | phpMyAdmin | **close** (open it for your IP only when needed) |
| 53 | PowerDNS | **close** unless this server is the DNS of your domains |
| 7800 (or your panel port) | aaPanel | limit to your IP, enable the panel's 2FA |

With plain `ufw` the same is:

```bash
ufw default deny incoming && ufw allow 80,443/tcp && ufw allow 22/tcp && ufw deny 21/tcp && ufw deny 888/tcp && ufw deny 53 && ufw enable
```

## 4. SSH with keys only

After checking that you can log in with your key:

```bash
sed -i 's/^#\?PasswordAuthentication .*/PasswordAuthentication no/; s/^#\?PermitRootLogin .*/PermitRootLogin prohibit-password/' /etc/ssh/sshd_config
systemctl reload ssh
```

## 5. Phone verification by SMS (findings 5, 13, 14)

Without SMS anyone can open an account with someone else's number, and "forgot password" cannot work. Add the Twilio
account in `deploy/.env`, then `bash deploy/update.sh`:

```
SMS_PROVIDER=twilio
TWILIO_ACCOUNT_SID=AC...
TWILIO_AUTH_TOKEN=...
TWILIO_FROM=+1...        # or a Messaging Service SID (MG...)
```

## 6. Remove the test accounts the testers left

```bash
cd /www/wwwroot/snai3i.elguennouni.site && sudo bash deploy/cleanup-pentest.sh
```

It backs up the database to `deploy/backups/` first, then deletes the 12 test phone numbers (`+2126009901xx`) with
their requests, offers, messages, reviews, reports and uploaded files, and clears the forged IPs from the audit log.

## 7. Re-test

Ask the testers to re-run the report on the deployed version. Expected changes are listed in `docs/SETUP.md`
(section "Security notes").
