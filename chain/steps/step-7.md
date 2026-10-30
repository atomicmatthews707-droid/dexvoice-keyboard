# Step 7: Full verification and repair, then summary

You are the final quality gate. Assume earlier steps left bugs.

1. Read the whole codebase end to end, not just recent diffs. Look for: compile errors, wrong imports, unresolved references, missing resources/strings referenced in code or XML, manifest problems (exported flags, permission names, intent filters, foreground service types required on API 34), threading bugs (UI from background, recorder never released), null-safety holes around `currentInputConnection`, leaked overlays/services, places an exception could crash the IME, any logging of the API key or dictated text, leftover TODOs/placeholders that make a feature dead.
2. Build and test: if a local Android SDK works, run `gradle assembleDebug testDebugUnitTest lintDebug` and fix everything until green. If not, push your fixes and check GitHub Actions (`gh run list` / `gh run view --log-failed` for the repo). If CI fails, read the log, fix, push, re-check, up to 4 rounds. If you cannot see CI at all, review with extra care and write "CI result unverified" in PROGRESS.md.
3. Confirm the workflow produces the `latest` release with `DexVoice.apk` (check `gh release view latest` if possible).
4. Cross-check README against the code one more time.
5. Write `RUN_SUMMARY.md` (one or two plain sentences: does the app build, where to download it, what the owner must do by hand, anything broken or unverified), list every `HUMAN-ONLY:` item from PROGRESS.md under it, commit and push.
6. Your final RESULT line must be that same one-to-two sentence summary, so the owner's notification says it.
