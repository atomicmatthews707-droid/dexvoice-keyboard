# Run summary

DeX Voice builds cleanly (assembleDebug, 8 unit-test suites, lint with 0 errors) and CI publishes it as `DexVoice.apk` on the repo's **Releases > Latest build** page, which you can download and install from the phone. It has never been run on a real phone, so expect to report small on-device issues (DeX bubble placement, background recording from the hotkey, Samsung keyboard quirks); the Wispr Flow provider is not implemented, so use Groq or OpenAI.

## You must do by hand (HUMAN-ONLY)
- Wispr provider: not implemented because https://api-docs.wisprflow.ai could not be fetched in the unattended run. Someone must read the official docs and implement `WisprTranscriber`. Groq/OpenAI work meanwhile.
- Install the APK from the Releases page, allow "install unknown apps" once, and work through the in-app setup checklist (mic permission, enable and select the DeX Voice keyboard, paste a Groq API key).
- Optional: grant overlay permission for the floating bubble, and switch on the DeX Voice accessibility service for the keyboard shortcut (Samsung may turn it off after updates).

## Step 7 changes
- Bubble service now starts as a microphone + special-use foreground service when mic permission is granted (falls back to special-use only, then untyped), so recording from the hotkey, tile or notification is more likely to be allowed while another app is in front. It also stops cleanly if it cannot become a foreground service.
