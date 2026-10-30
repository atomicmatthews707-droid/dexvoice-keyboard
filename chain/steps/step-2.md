# Step 2: Settings, secure storage, audio recorder

Deliverables (package `com.tektomic.dexvoice`):
- `Settings` class over SharedPreferences, plus `SecretStore` over EncryptedSharedPreferences for the API key(s) (fall back gracefully with a logged warning if the keystore is unavailable; never log secrets). Keys: provider (`openai_compat` | `wispr`), api base URL, model, language ("" = auto), vocabulary hint prompt, trailing space (default true), always-show-keyboard (default true), hotkey (key code + meta state), max record seconds (default 120), bubble enabled.
- `WavRecorder`: `AudioRecord` 16 kHz mono PCM16 on a background thread, writes a WAV file into `cacheDir/recordings/`. `start()`, `stop(): File?`, `isRecording`, amplitude callback (for the UI pulse), auto-stop at the max length, returns null for recordings shorter than ~0.4 s, handles permission-missing and `AudioRecord` init failure with typed errors (`RecorderError`). Delete recordings older than 1 day on start. Handle audio focus politely (request transient focus, release after).
- `WavWriter` helper (header writing) with unit tests (header bytes, sizes, tiny silence file).
- `SettingsActivity` (View-based, simple vertical form): provider dropdown, base URL, model, language, API key field (masked, "Show" toggle, never prefilled with the stored value; show "Key saved" instead), hint prompt, toggles. A "Test microphone" button that records 2 s and shows the peak level. Make MainActivity open or embed settings.
- Unit tests for Settings defaults and WavWriter.

Done when: code compiles (if a local SDK exists, build passes), unit tests exist for WavWriter and Settings, and no code path logs or prints the API key.
