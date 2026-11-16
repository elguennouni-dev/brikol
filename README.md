# Brikol · بريكول (code name: sanai3i)

Moroccan marketplace connecting clients with artisans (painter, carpenter, mechanic, blacksmith…). Payment is **cash only** — no money flows through the app. UI is bilingual: Darija (RTL) + French.

```
backend/         Spring Boot 3 + MySQL (REST API + serves the built web app and the landing page)
frontend/        React (Vite) source of the web app; `npm run build` writes into backend/.../static/app
mobile/android/  Kotlin WebView shell
mobile/ios/      SwiftUI + WKWebView shell (XcodeGen)
docker-compose.yml
```

## Run the backend

**Quick, no MySQL (H2 in memory, demo data):**
```
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Open http://localhost:8080

**With MySQL via Docker:**
```
docker compose up --build
```
Env vars: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (long random string!), `APP_SEED` (demo data, `false` in production), `CORS_ORIGINS`, `PORT`.

**Tests:** `cd backend && mvn test` (full flow: request → offers → accept → complete → review).

## Demo accounts (when APP_SEED=true / dev profile)
Password `demo1234`
- Client: `0600000001` (Amine), `0600000002`, `0600000003`
- Artisans: `0611000001` Hassan (painter), `0611000002` Omar, `0611000003` Anas, `0611000004` Karim (carpenter), `0611000005` Youssef (mechanic), `0611000006` Rachid (blacksmith), `0611000007` Said (electrician), `0611000008` Mohamed (plumber), `0611000009` Brahim (tiler)

## API (JSON, `Authorization: Bearer <jwt>`)
| Method | Path | Notes |
|---|---|---|
| POST | /api/auth/register, /api/auth/login | phone + password; role CLIENT or ARTISAN |
| POST | /api/auth/google | Google ID token (+ phone, role, trade the first time) |
| GET | /api/me | |
| GET | /api/categories | public |
| GET | /api/artisans?category&q&city&availableToday | public |
| GET | /api/artisans/{id} | public |
| POST | /api/requests | client creates a job request (`lat`, `lng`, `address`, `city` from the map) |
| GET | /api/requests/mine?tab=active\|done | client |
| GET | /api/requests/{id} | owner only |
| POST | /api/requests/{id}/offers/{offerId}/accept | reveals artisan phone to client |
| POST | /api/requests/{id}/complete, /cancel, /review | |
| GET | /api/artisan/requests, /api/artisan/jobs | artisan: open requests / own jobs |
| POST | /api/requests/{id}/offers | artisan sends an offer |
| GET, PATCH | /api/artisan/me | profile |
| POST, DELETE | /api/artisan/services[/{id}] | price list |
| GET | /api/notifications | latest 50 + unread count |
| POST | /api/notifications/read-all, /api/notifications/{id}/read | |
| GET | /api/notifications/stream | live channel (Server-Sent Events, `Authorization: Bearer`) |

Errors are codes (e.g. `PHONE_TAKEN`) translated by the web app.

## Sign in with Google
Web and iPhone (PWA in Safari): button "Continuer avec Google" on the login page (Google Identity Services).
The browser gets an ID token, `POST /api/auth/google {credential}` verifies it on the server (Google's public keys, issuer,
audience = `GOOGLE_CLIENT_ID`, expiry). Known account (same Google id, or same verified e-mail): signed in. New account:
the server answers `428 GOOGLE_SIGNUP_NEEDED`, the app asks once for the phone number and the role (and trade for artisans),
then calls again with them. Client ID: `app.google.client-id` / env `GOOGLE_CLIENT_ID`; in Google Cloud add the site to
"Authorized JavaScript origins". Inside the Android/iOS app WebViews Google forbids this page, so the button is hidden there
(the apps add `Sanai3iApp/...` to the user agent); native Google sign-in in the apps is a next step.

## Location on a live map
When a client posts a request he picks the place on a map (Leaflet + OpenStreetMap tiles, no API key):
search as you type (Photon geocoder, limited to Morocco), "my location" (GPS), or move the map under the pin.
The request stores `lat`, `lng`, `address`, and the city found on the map (used to notify the artisans of that city;
city names are compared without accents: Fès = Fes). Artisans only see an approximate area (about 1 km, no street) until
their offer is accepted; then they get the exact address and an "Itinéraire" link (Google Maps). The Android app asks for
the location permission; iOS shows the message from `NSLocationWhenInUseUsageDescription`.
Public servers used: `tile.openstreetmap.org` and `photon.komoot.io` (fair use, fine for a small app; for heavy traffic run your own or use a paid tile provider).

## Git workflow
- Every push runs `mvn test` on GitHub (Actions > Backend tests).
- Server update: `bash deploy/update.sh` (git pull + rebuild + restart). `deploy/.env` and `deploy/downloads/` are not in Git.
- The built web app (`backend/src/main/resources/static/app`) is committed, so the server does not need Node. After changing `frontend/`, run `npm run build` before committing.

## Administration
Roles: `CLIENT`, `ARTISAN`, `ADMIN`. The admin can never be created from the public sign-up form.
The first admin is created at start-up from `ADMIN_PHONE` / `ADMIN_PASSWORD` / `ADMIN_NAME` (dev profile: `0699000000` / `admin12345`;
on the server `deploy.sh` generates them in `deploy/.env` and prints them). Panel: `/app/#/admin`.

- **Dashboard**: users by role, blocked, new this week, requests by status, offers, average rating, people online, 14-day charts, per-trade counts.
- **Users**: search/filter, detail, block / unblock (reason; the session is cut at once, login refused), temporary password reset, promote/demote admin, verify artisans.
- **Requests / reviews**: search, detail with offers, cancel a request (client and artisans are notified), delete an abusive review.
- **Trades**: add / rename / delete (refused while in use).
- **Audit log**: every login (also failed or blocked), sign-up, request, offer, acceptance, review and every admin action, with actor, detail and IP. Filter by action or text.
- **Tools**: announcement to all / clients / artisans (live notification), site settings (close registrations, banner message), system info, CSV export of users and requests.

| Method | Path |
|---|---|
| GET | /api/public/config (banner, registrations open) |
| GET | /api/admin/stats, /system, /users, /users/{id}, /requests, /requests/{id}, /reviews, /categories, /audit, /settings |
| POST | /api/admin/users/{id}/block, /unblock, /reset-password, /role; /artisans/{id}/verified; /requests/{id}/cancel; /broadcast; /categories |
| PUT, DELETE | /api/admin/categories/{id}, /settings, /reviews/{id} |
| GET | /api/admin/export/users.csv, /export/requests.csv |

## Live notifications
Events (and admin announcements) create a row in `notifications` and are pushed immediately over the open SSE connection of the user:
new request (to artisans of that trade and city), new offer, offer accepted/rejected, request cancelled, job done, new review.
The web app shows a toast, a badge on the bell, and refreshes the open list by itself. Behind Nginx the stream works because
the response sends `X-Accel-Buffering: no`; a heartbeat every 25 s keeps it open. The app reconnects automatically.

## Web layout
Under 960 px wide the app is the phone layout (used by the Android/iOS WebViews). From 960 px up it becomes a website:
top bar with navigation and a notifications popover, multi-column grids, sticky contact card on artisan pages.

## Mobile apps
Both apps just load the web app from the server and open `tel:`, WhatsApp, mailto and external links in the native apps.

**Android** (Android Studio, open `mobile/android`): debug build points to `http://10.0.2.2:8080/` (emulator → your computer). For a real phone use your computer's LAN IP. Set the HTTPS address of your server in `app/build.gradle.kts` (release `BASE_URL`) before publishing.

**iOS** (Mac + Xcode): `brew install xcodegen && cd mobile/ios && xcodegen generate && open Sanai3i.xcodeproj`. Dev address is in `Config/Debug.xcconfig`, production in `Config/Release.xcconfig`. Add a 1024×1024 app icon to `Assets.xcassets/AppIcon.appiconset`.

## Known limitations
- Search/filtering is done in memory (fine for thousands of artisans, not millions).
- Artisan phone numbers are public by design (simple marketplace).
- No push notifications, no image upload, no OTP phone verification yet.
- Not yet compiled or run in the authoring environment (Maven Central was unreachable) — run `mvn test` first.
