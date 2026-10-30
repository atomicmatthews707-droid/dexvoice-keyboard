# Step 3: Transcription providers

Deliverables:
- `interface Transcriber { suspend fun transcribe(wav: File, settings: Settings): Result<String> }` plus a `TranscriptionException` with a short human-readable `userMessage` (e.g. "API key rejected, check Settings", "No internet", "Recording too long").
- `OpenAiCompatTranscriber` (OkHttp): multipart POST to `{base}/audio/transcriptions` with fields `file` (audio/wav), `model`, `response_format=json`, optional `language`, optional `prompt`; Bearer auth from SecretStore. Timeouts 60 s. Retry up to 2 extra times with 1 s then 3 s backoff on IOException, 429 and 5xx. Do NOT retry 400/401/403/413. Map status codes to the friendly messages. Never include the key in exceptions or logs.
- `WisprTranscriber`: FIRST fetch and read the official Wispr Flow API docs at https://api-docs.wisprflow.ai (it redirects; also try https://wisprflow.ai/developers and the WebSocket and REST quickstarts it links to). Implement exactly what the official docs say (auth, endpoint, audio format, response shape). Ignore third-party blog posts and skills pages, they disagree with each other. Record in PROGRESS.md which doc URL you followed. If the official docs cannot be reached or are unclear, implement the class so it returns a clear "Wispr provider not configured" error, mark it TODO in the settings UI help text, and note it as `HUMAN-ONLY:` (needs the owner's Wispr API key and docs check).
- `TextPostProcessor`: trim, collapse whitespace, drop silence hallucinations when they are the entire output ("Thank you.", "Thanks for watching!", "you", ".", "Bye."), return "" for empty, append a single trailing space if the setting is on.
- `TranscriberFactory.forSettings(settings)`.
- Unit tests with MockWebServer: success, 401 mapping, 429-then-success retry, 500 exhaustion, hallucination filter, multipart fields present, key never appears in exception messages.

Done when: compiles, tests written and (if a local SDK exists) green.
