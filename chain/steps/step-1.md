# Step 1: Scaffold, signing, CI

Deliverables:
- Gradle Kotlin DSL project: `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties` (AndroidX on, jvmargs), `app/build.gradle.kts`. Package `com.tektomic.dexvoice`, minSdk 33, targetSdk/compileSdk 34, versionName 0.1.0. Dependencies: androidx.core-ktx, androidx.appcompat, material, okhttp, androidx.security:security-crypto, junit, mockwebserver (test).
- `app/src/main/AndroidManifest.xml` with the application, a launcher `MainActivity` (blank placeholder screen with the app name), and the permissions the spec needs: RECORD_AUDIO, INTERNET, SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE, POST_NOTIFICATIONS. Components get added by later steps.
- App icon (simple vector mic on a dark rounded square, adaptive icon) and string resources.
- Signing: run `keytool -genkeypair -v -keystore app/dexvoice-debug.keystore -alias dexvoice -keyalg RSA -keysize 2048 -validity 36500 -storepass android -keypass android -dname "CN=DeX Voice, O=Tektomic"` and wire it as the signing config for the `debug` build type. Commit the keystore.
- `.github/workflows/build.yml` as described in SPEC.md (push to main + workflow_dispatch; Java 17; setup-gradle with gradle-version 8.7; `gradle assembleDebug testDebugUnitTest`; upload artifact; create/update the `latest` release with `DexVoice.apk`; `permissions: contents: write`). Rename the output APK to `DexVoice.apk` in a step before release upload.
- One trivial unit test so the test task has something to run.
- Try once (10 minute cap) to install a minimal Android SDK locally (cmdline-tools from dl.google.com, then `sdkmanager "platforms;android-34" "build-tools;34.0.0"`, accept licenses, write `local.properties`). If it works, run `gradle assembleDebug testDebugUnitTest` and fix errors. If not, say so in PROGRESS.md and carefully review the Gradle files by eye.

Done when: files exist and are consistent (versions compatible: AGP 8.5.x needs Gradle 8.7+ and JDK 17), workflow YAML parses (`python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/build.yml'))"`), and the local build passes if the SDK was obtainable. Record in PROGRESS.md whether a local build was possible.
