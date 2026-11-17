# Brikol: setup and technical reference

The short version is in the [main README](../README.md). This file has every setting, endpoint and ops note.


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

## SMS codes and forgotten password (Twilio)
Off until configured: sign-up then works without code and "forgot password" says it is unavailable.
To turn on, in `deploy/.env`: `SMS_PROVIDER=twilio`, `TWILIO_ACCOUNT_SID=AC...`, `TWILIO_AUTH_TOKEN=...`,
`TWILIO_FROM=+1...` (a Twilio number) or `MG...` (Messaging Service), then `bash deploy/update.sh`.
Codes: 6 digits, 10 minutes, 5 tries, 1 per minute and 5 per hour per number. New accounts (password or Google) need the code;
`POST /api/auth/otp {phone, purpose: REGISTER|RESET}`, `POST /api/auth/reset {phone, otp, password}`.

## Rate limits
Per IP: login 20 / 10 min, sign-up 15 / h, SMS 6 / h, reset 10 / h, uploads 60 / 10 min, reports 15 / h, chat 40 / min (`429 RATE_LIMITED`).
Per number: 8 wrong passwords in 15 min lock the login (`TOO_MANY_ATTEMPTS`). Off in the `dev` profile (`RATE_LIMIT=false`).

## Push notifications (Firebase)
Every notification is also sent as a push (Android app, browsers, iPhone PWA 16.4+) when Firebase is configured:
1. console.firebase.google.com: create a project. Project settings > Service accounts > Generate new private key: save it on
   the server as `deploy/secrets/fcm.json` (deploy.sh adds `FCM_CREDENTIALS`).
2. Project settings > General > Add app > Web: copy `apiKey`, `appId`, `projectId`, `messagingSenderId` into `deploy/.env` as
   `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID`. Cloud Messaging > Web Push certificates >
   Generate key pair: `FIREBASE_VAPID_KEY`.
3. Add app > Android, package `ma.sanai3i.app`: download `google-services.json` into `mobile/android/app/` (or the GitHub secret
   `GOOGLE_SERVICES_JSON`, base64) and rebuild the APK. Without it the app works, push is just off.
Users enable it on the web from Account > "Activer les notifications"; the Android app asks at start.

## Chat, appointment, reviews both ways, reports
After acceptance: chat (`/app/#/chat/{id}`, live over SSE, push when away), appointment proposed by one side and confirmed by
the other, reminder one hour before. After the job the artisan rates the client; artisans see the client's rating on new requests.
"Signaler" on artisans, offers, requests and chat messages; Admin > Signalements to resolve or dismiss.

## Subscription (revenue)
Off by default. Admin > Outils > Revenus: turn on, monthly price, free days, "featured" price, how to pay (CashPlus, RIB...).
Artisans pay outside the app; the admin records the payment on the user page, which extends the subscription (or featured).
Without an active subscription an artisan can see requests but not send offers. Featured artisans are listed first.

## Pages for Google
`/artisans` (index), `/artisans/{trade}/{city}` (e.g. `/artisans/paint/casablanca`: number of verified artisans, usual
price, how it works, schema.org `Service` data; never artisan names), `/sitemap.xml`, `/robots.txt`. Old `/pro/{id}` links
redirect (301) to the trade + city page. Submit the sitemap in Google Search Console.

## Requests: photos, video, voice notes
`POST /api/uploads` (multipart: `file`, `purpose`). Clients attach up to 6 files to a request (`mediaIds`): photos (resized to
1600 px in the browser, JPEG/PNG/WebP, 10 MB), video (MP4/MOV/WebM/3GP, 60 MB), voice notes recorded in the app (WebM/MP4 audio, 15 MB).
Files live in `UPLOADS_DIR` (production: `deploy/uploads`, a Docker volume, not in Git) and are served at `/media/{uuid}`.
Artisan ID-card images (`ID_FRONT`, `ID_BACK`) are private: `/api/media/{id}` for the owner and admins only.
Behind Nginx raise the upload limit (aaPanel: site > Config, `client_max_body_size 100m;`).

## Prices and negotiation
Each trade has hard bounds (`categories.min_price/max_price`, editable by the admin). `PricingService` narrows them from real
accepted prices of the last 12 months when there are 5+ (60 % of the 10th percentile to 160 % of the 90th). The range is frozen
on the request (`price_min/price_max`) and every budget, offer and counter-offer must be inside it (`PRICE_OUT_OF_RANGE`).
Prices are typed by hand. Negotiation on an offer: the client counters (`POST /api/requests/{id}/offers/{offerId}/counter`), the
artisan counters back (`POST /api/artisan/offers/{offerId}/counter`) or agrees (`.../agree`, the deal is closed like an acceptance).
6 counter-offers max per offer. The client can accept only the artisan's latest price.

## Artisan activation
New artisans are `DRAFT`. They fill a file (`/app/#/onboarding`, `GET/PUT /api/artisan/me/onboarding`): full name, CIN + photos
(front/back), photo, years, description, trade, workshop location on the map and travel radius, 2+ portfolio photos, then send it
(`POST .../submit`, status `PENDING`, admins notified). Admin > Artisan files: approve (`APPROVED`, verified badge) or reject with a
reason (`REJECTED`, the artisan fixes and resends). Until approved an artisan is not listed and cannot see requests or send offers.
Approved artisans only see requests of their trade within their radius (same city when a position is missing), nearest first,
with the distance; the exact address appears once their offer is accepted.

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
**Team**: the account in `ADMIN_PHONE` is the owner (super admin, every right, cannot be changed by anyone). Only the owner
manages the team (Admin > Équipe, `/api/admin/staff`): creates staff accounts (name, phone, password) with permissions
`FILES, USERS, REQUESTS, REPORTS, CATEGORIES, BILLING, BROADCAST, SETTINGS, AUDIT`, edits them, suspends, resets the password,
takes an account out of the team (back to a client account) or deletes it. Nobody can promote an existing user to admin.
Permissions are checked on every administration call; staff only see the tabs they may use.

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
