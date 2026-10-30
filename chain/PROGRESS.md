# Progress log

Chain started 2026-10-29. Each step appends 2-4 lines below.
- Step 1: scaffold (AGP 8.5.2, Kotlin 1.9.24, Gradle 8.7), manifest+permissions, vector mic adaptive icon, committed debug keystore wired to debug signing, smoke test, CI workflow (builds, uploads artifact, updates `latest` release with DexVoice.apk). YAML parses.
- Local build WAS possible: Android SDK installed at /opt/android-sdk (ephemeral per session; reinstall via cmdline-tools from dl.google.com, ~2 min, then `echo sdk.dir=/opt/android-sdk > local.properties`). `gradle assembleDebug testDebugUnitTest` passes. Maven Central returned HTTP 429 on first tries; just retry after a pause.
- Note: add_repo tool was not exposed this run; the repo was already cloned and git push worked.
- Step 2: added `Settings` (all prefs), `SecretStore` (EncryptedSharedPreferences, private-file fallback with warning, keys never logged), `WavWriter`, `RecorderError`, `WavRecorder` (16 kHz mono WAV, 120 s cap, <0.4 s => null, audio focus, old-file cleanup, amplitude callback), `SettingsActivity` (form, masked key with Show toggle and "Key saved", Test microphone 2 s), MainActivity now opens settings. Unit tests for WavWriter, Settings, SecretStore pass; `gradle assembleDebug testDebugUnitTest` passes locally (SDK reinstalled; Maven 429s needed a retry).
- Decisions: the API key is saved on pause; the key field is never prefilled. Settings keys (`Settings.K_*`, `Settings.PROVIDER_*`) and `SecretStore.keyFor(provider)` are what later steps should use. Hotkey fields are stored but their capture UI is left to step 5.
