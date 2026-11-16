# Android APK (no Android Studio needed)
1. Create a signing key once (any computer with Java):
   `keytool -genkeypair -v -keystore release.jks -alias sanai3i -keyalg RSA -keysize 2048 -validity 10000`
   Keep release.jks safe: updates must be signed with the same key.
2. `base64 -w0 release.jks` → copy the output.
3. In your GitHub repo: Settings > Secrets and variables > Actions, add:
   KEYSTORE_BASE64 (the base64), KEYSTORE_PASSWORD, KEY_ALIAS (sanai3i), KEY_PASSWORD.
4. Actions > "Android APK" > Run workflow. Download `sanai3i-apk`, rename the file `sanai3i.apk`, upload it to
   `/www/sanai3i/deploy/downloads/` on the server.
The release build already points to https://snai3i.elguennouni.site/app/.

# iOS
No Apple Developer account → no App Store/TestFlight. iPhone users install the PWA: Safari > Share > Add to Home Screen
(explained on the landing page).
