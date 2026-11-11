# SurvivalOps analytics audit and proposed private diagnostics

Existing Mixpanel events:
- app_launched: version, OS, architecture, Java version/vendor, headless mode
- app_heartbeat: version every ten minutes when enabled
- app_shutdown: session duration and version
- emulator_connected: emulator type; profile_count: number
- bot_started: enabled tasks, number of profiles, emulator type, version
- bot_stopped: reason, duration, version
- task_started: name; task_completed: name, status, duration

The Mixpanel message includes a persistent random UUID. Neither recognition images,
OCR text, confidence scores nor actual task logs are included.
Some task_completed(success) reports mean only that Java returned normally, not
that an in-game action succeeded, because tasks may reschedule on internal errors.

Consent to external Mixpanel is not consent to upload screenshots.
For a private diagnostics inbox build a separate opt-in, authenticated HTTPS endpoint,
private server-side storage with retention and size caps, redaction of profile names,
access tokens and game chat, rate-limiting and deduplication.
Capture bounded failure context, action scores, expected transitions and optionally
redacted screenshots. Only sanitized technical IDs may be linked to public GitHub.
Evidence remains accessible via authenticated SSH during future interactive chats.
No unattended assistant monitoring is implied.

Existing local ZIP export requires a user-chosen destination and performs no upload.
