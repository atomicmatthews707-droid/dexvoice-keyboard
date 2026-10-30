# DeX Voice Keyboard: project spec (read this first, every step)

Owner: Adam Matthews (Tektomic). Solo developer, works ONLY from a Samsung phone running Samsung DeX on an external monitor. His computer is dead. There is no PC, no Android Studio, no adb. Everything is built here in the cloud and by GitHub Actions. He installs the finished APK from GitHub on the phone.

## The problem
Wispr Flow (third-party dictation app) does not work in Samsung DeX. Its floating bubble and text insertion rely on Accessibility and overlay features and fail on DeX's external display. The cause was never pinned down. We are NOT fixing Wispr. We are building our own app that does the same job.

## What we are building: an Android app "DeX Voice" (package `com.tektomic.dexvoice`)
Goal: tap a button, talk, tap again, and the transcribed text appears in whatever text field has focus, on the phone screen OR the DeX monitor. Transcription quality should match Wispr Flow (the owner loves it).

Core idea: the app is an Android **input method (keyboard)**. A keyboard is the one kind of app Android officially lets type into the focused field on any display via `InputConnection.commitText`, so the DeX display barrier that breaks Wispr does not apply. No ADB, no clipboard tricks, no accessibility text injection.

Components:
1. **IME** (`InputMethodService`): a minimal voice-first keyboard (big mic button, status line, backspace, space, enter, globe/switch-keyboard, settings). It must also be usable when a physical keyboard is attached (override `onEvaluateInputViewShown`, with a user setting "always show on-screen keyboard"). Owner's hardware keyboard has NO mic key and his on-screen keyboard shows no mic.
2. **RecognitionService** (`android.speech.RecognitionService`) with a `<recognition-service>` XML so the app is listed under Android's "Voice input" provider list next to Google and Samsung. It should also work when other apps call SpeechRecognizer. Caveat to document honestly: Samsung's own keyboard may ignore the system default recognizer, which is why the IME is the primary path.
3. **Floating mic bubble** (overlay service, `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL` so tapping it does NOT steal focus from the text field). Draggable, shows state (idle / recording / transcribing / error). Must be display-aware: show on the display where the active input field is (DeX monitor included) using a window context for that display, and follow focus if it moves.
4. **Global hotkey** via an `AccessibilityService` used ONLY to filter hardware key events (`canRetrieveWindowContent=false`, no content reading, no text injection). Default combo configurable by a "press the keys now" capture screen. Also add a persistent notification with a Start/Stop action and a Quick Settings tile as extra activators.
5. **Voice controller**: one shared state machine (IDLE, RECORDING, TRANSCRIBING, ERROR) used by the IME, bubble, hotkey, notification, tile and RecognitionService. Result text goes to the active IME's `InputConnection.commitText`. If no IME instance is active, copy to clipboard and show a toast "Copied, paste with Ctrl+V".
6. **Transcription providers** behind one interface:
   - `OpenAiCompatProvider` (default Groq: base `https://api.groq.com/openai/v1`, model `whisper-large-v3-turbo`; OpenAI works by changing base and model). Multipart POST to `/audio/transcriptions`.
   - `WisprProvider`: Wispr has a developer API (WebSocket + REST, key from wisprflow.ai/developers or enterprise@wisprflow.ai). Third-party pages disagree on endpoints, so the step that builds it MUST read the official docs at https://api-docs.wisprflow.ai (follow redirects) and implement exactly what they say. If the docs cannot be reached, ship the provider disabled with a clear TODO instead of guessing.
7. **Settings screen + first-run checklist** with deep links: enable keyboard (`ACTION_INPUT_METHOD_SETTINGS`), select keyboard, mic permission, overlay permission (`ACTION_MANAGE_OVERLAY_PERMISSION`), accessibility service, battery-optimization exemption, API key field (stored with EncryptedSharedPreferences, never logged, never in git).

## Tech decisions (already made, do not relitigate)
- Kotlin, minSdk 33 (Android 13+), targetSdk 34, compileSdk 34, Gradle Kotlin DSL, single `app` module, View-based UI (no Compose, keeps the IME simple). Use known-good AGP/Kotlin/Gradle versions (AGP 8.5.x, Kotlin 1.9.24 or 2.0.x, Gradle 8.7+).
- HTTP: OkHttp. Tests: JUnit4 + MockWebServer (+ Robolectric only if truly needed).
- Audio: `AudioRecord`, 16 kHz mono PCM16, written as WAV. Cap at 120 s. Trim leading/trailing silence only if trivial.
- Filter Whisper silence hallucinations ("Thank you.", "Thanks for watching!", lone punctuation) when they are the entire output. Optionally append a trailing space.
- No gradle wrapper (cannot download in all environments). CI uses `gradle/actions/setup-gradle` with `gradle-version: 8.7`.
- Signing: generate ONE keystore with `keytool` and commit it (`app/dexvoice-debug.keystore`, known password `android`, alias `dexvoice`) and use it for the debug build so every build has the same signature and updates install over the old version. This is a personal app in a private repo; that trade-off is accepted.
- CI (`.github/workflows/build.yml`): on push to main and on workflow_dispatch: setup-java 17, setup-gradle, `gradle assembleDebug testDebugUnitTest`, upload the APK as an artifact AND publish/update a GitHub Release tagged `latest` with `DexVoice.apk` attached (`permissions: contents: write`) so the owner can download it from his phone's browser.

## Owner constraints
- Everything the owner must do by hand must be tiny and in the README (install APK, enable keyboard, grant permissions, paste API key).
- Never put secrets in the repo. Never ask questions (nobody is watching). Pick the most reasonable option and log it in `chain/PROGRESS.md`.
- Can't test on a real phone here. Code must be defensive: every external failure becomes a visible, human-readable state, never a crash.
