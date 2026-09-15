BKFS 1.2 — final hosted setup client

Source: BKFS_ANDROID_BUILD_READY_V1.zip, recovered from the earlier Android project.
Loads https://bkfs-fresh-production.up.railway.app/login and the existing server UI.
Package com.bkfs.app. No credentials or business records are embedded.
The separate in.bkfs.demo app is not upgraded or migrated.
This client requires internet. Offline data entry and sync are NOT implemented.
Customer layout is served by the existing final Railway deployment, not copied from the unrelated browser demo.
System insets, HTTPS navigation, main-page errors, retry, user-selected file inputs, and Call/WhatsApp navigation are handled by the Android wrapper.

Build: Gradle 8.9, JDK 17, Android SDK 35.
gradle -p android-live :app:assembleDebug :app:lintDebug

Validation limits: APK compilation/lint/signature are checked in CI. Authenticated server flows and on-device behavior require testing with the existing account. Do not assume offline sync. Debug signing is not production signing.
