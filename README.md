# Android template

Minimal Android APK foundation extracted from the working **xiaqui/wolf-chess** build configuration. No game logic, history, or LAN code.

- Kotlin / Android Gradle Plugin 8.5.2 / Gradle 8.7 / Java 17
- Android compile/target SDK 35; minimum SDK 26 (Android 8)
- GitHub Actions builds a debug APK and uploads it as an artifact.
- If all four GitHub Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) are set, the debug APK uses the supplied signing key; otherwise it uses the standard debug key.
- Google Drive delivery is **not yet configured**. The original wolf-chess workflow only uploaded GitHub artifacts; Drive distribution requires a separate integration.

Run locally with Gradle 8.7 and Android SDK 35: `gradle testDebugUnitTest assembleDebug`.

Before deriving a new app, change the application ID, namespace, app label, and signing credentials. Do not commit secrets or keystores.
