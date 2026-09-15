# BKFS Management

Loan and collection management prototype for Android and web. Use fictional records only. Demo login: `admin` / `1234`.

## Android APK

GitHub Actions workflow **Build BKFS APK** builds and signs a debug APK on main-branch pushes and manual runs. Open the successful run and download **BKFS-Android-APK** under Artifacts, extract the ZIP, and install `BKFS-demo.apk`. Build success must be verified in Actions.

The Android app bundles the web interface and runs without internet, including first launch. Local records survive closing the app. Backup export and restore use Android's document picker. Uninstalling or clearing app data deletes local records; export a backup first. Debug signing is for testing and may change between builds; this is not a production release.

Build locally with JDK 17, Android SDK 35 and Gradle 8.9: `gradle -p android :app:assembleDebug :app:lintDebug`.

## Remaining work

Server authentication, protected storage, cloud sync between phones/web, validated amortization schedules and new-device approval are not implemented. The demo login does not provide a security boundary. Reducing interest is not implemented. Packaging does not make this suitable for real customer data.
