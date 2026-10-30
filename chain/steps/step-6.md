# Step 6: Onboarding checklist, error UX, README

Deliverables:
- `OnboardingActivity` (also the launcher entry when setup is incomplete): a live checklist that re-checks each item in `onResume` and shows a green check or a "Fix" button with a deep link:
  1. Microphone permission (runtime request).
  2. Keyboard enabled (`Settings.ACTION_INPUT_METHOD_SETTINGS`; detect via `InputMethodManager.enabledInputMethodList`).
  3. Keyboard selected as current (`imm.showInputMethodPicker()`; detect via `Settings.Secure.DEFAULT_INPUT_METHOD`).
  4. API key saved (opens SettingsActivity).
  5. Overlay permission for the bubble (`ACTION_MANAGE_OVERLAY_PERMISSION` with package URI), optional.
  6. Shortcut service enabled (`ACTION_ACCESSIBILITY_SETTINGS`, detect via enabled services string), optional. Explain plainly that it only listens for the shortcut.
  7. Notifications permission (API 33+), optional.
  8. Battery optimization exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, declare permission), optional.
  9. Voice input provider: button opening the system voice input settings (try `Settings.ACTION_VOICE_INPUT_SETTINGS`, fall back to general settings), optional.
  Plus a "Test it" box: an EditText with a big "Talk" button that runs the full record → transcribe → insert path so the owner can confirm everything works in one place.
- Error UX pass across the app: every failure (no mic permission, no internet, bad key, rate limit, too short, provider not configured, no active text field) shows a short human message in the IME status line, the bubble (toast), and the notification, and never crashes. Add a global uncaught-exception logger that writes to `filesDir/logs/` (rotating, small) and a "Share debug log" button in settings that redacts the API key.
- Polish: haptic tick on start/stop, optional start/stop sounds (default off), dark theme that matches DeX, large touch targets.
- Rewrite `README.md` for the owner (he is on his phone, no computer): what it is; install (open the repo's Releases page on the phone, download `DexVoice.apk`, allow install from Chrome, install, updates install over the top); setup (open the app, follow the checklist, get a free Groq API key at console.groq.com and paste it, or a Wispr key if he has one); daily use in DeX (click into a field → tap bubble / press shortcut / tap the keyboard's mic → talk → tap again); settings reference; troubleshooting (text goes nowhere: is DeX Voice the selected keyboard; bubble on the wrong screen: tap a field on the target screen first; shortcut not working: re-enable the shortcut service after updates, Samsung sometimes turns it off; app killed in the background: battery exemption; hardware keyboard hides the on-screen one: turn on "always show"; voice-input list doesn't change Samsung keyboard's mic: expected, use DeX Voice as the keyboard). Privacy: audio goes to the chosen provider only.

Done when: compiles, every README instruction matches a real screen/setting in the code (grep to confirm), and onboarding covers every permission the manifest declares.
