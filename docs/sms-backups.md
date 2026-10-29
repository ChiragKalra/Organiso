# SMS backups

Settings → Backup provides two destinations:

- **Back up to this device** opens Android’s local file picker, writes an unencrypted JSON file, then reopens it and compares its SHA-256 checksum before recording success.
- **Google Drive connection** asks the user to select a Google account and authorize only `https://www.googleapis.com/auth/drive.appdata`. **Back up to Google Drive** creates a new file in the private app-data folder and verifies Google’s returned size and MD5 checksum. Older backups are retained. Turning the connection off revokes access; it does not remove existing backups.

Messages are read directly from Android’s SMS provider. Export does not modify messages or require Organiso to be the default SMS app, but Android must grant `READ_SMS`. MMS attachments, classification databases and Organiso settings are outside this format. Version 1 JSON contains `format`, `version`, `createdAt` (UTC), `sms` (provider columns, including nulls), and `messageCount`. This change does not add a restore flow.

Exports are streamed through a private temporary file, which is removed after success or failure. Last-success times change only after destination verification. Access tokens are kept out of preferences and logs; Google Play services manages token caching.

## Google OAuth setup

Enable the Google Drive API and configure the consent screen in the existing Google Cloud project identified by the local OAuth configuration. Staging and production use separate Android OAuth clients within that same project. Register each package and signing certificate:

- Release: `com.bruhascended.organiso` and its release/Play signing SHA-1.
- Debug: `com.bruhascended.organiso.staging` and the debug signing SHA-1 (`./gradlew :app:signingReport`). Debug uses a separate package and FileProvider authority so device testing can preserve an installed production app.

Google Identity Services resolves Android clients from the calling package and certificate; it does not accept an Android client ID as an `AuthorizationRequest` parameter. Do not pass the downloaded Android client ID as a web/server client ID or embed a client secret. The test runner accepts `.config.json` or a downloaded `client_secret_*.json` through `--google-config`, validates its endpoints, and records its SHA-256 and project ID in a private report. Both file patterns are ignored by Git. That preflight does not register the staging package or prove that OAuth consent/upload succeeds. A real Drive check additionally requires a signed-in Google test account, a matching registered client, and Drive API access. The app reports configuration and authorization failures without falsely enabling the connection.

Account selection uses the Google Play services account picker, which renders dark when the device uses dark mode. The older framework account chooser forces a light dialog. Google consent and Android's file picker retain their own system-managed appearance.

## Independent automatic schedules

Settings → Backup → Automatic backups provides separate **Off / Daily / Weekly / Monthly** settings for this device and Google Drive. Both default to Off. One destination can run daily while the other runs weekly, monthly, or remains disabled.

Device schedules require a folder selected through Android's directory picker. The app retains permission for that folder across restarts, creates a new JSON file for each occurrence, and reopens it to verify SHA-256. Drive schedules require a connected Google account, refresh access through Google Play services without displaying UI, and verify each upload's returned size and MD5. If Google needs consent again, the status asks the user to reconnect in the app. Disconnecting Drive also disables its schedule without affecting the device schedule.

WorkManager persists each destination's pending work across app/device restarts. Work begins around the local time the schedule was enabled, subject to Android's battery/storage restrictions. Drive can additionally wait for an unmetered connection; local backups do not require network access. Monthly scheduling uses calendar months, clamps days such as the 31st to the end of a short month, and returns to the original day in longer months. The screen displays each destination's next backup and outcome. “Run this schedule now” runs the same background backup flow without moving the next scheduled occurrence.

Transient errors retry up to twice with exponential backoff. Each destination has independent cancellation and unique occurrence identifiers, preventing duplicate future work after process restarts. Turning a schedule Off cancels only its work. Existing backup files and all SMS remain untouched. Automatic backup implementation is ready for subsequent device testing; no daily/weekly/monthly timing claims should be inferred from the earlier manual-backup E2E checks.

## Emulator verification

Keep actual SMS exports and emulator data outside the checkout. Never commit them. The fixture importer is instrumentation-only, rejects physical-device hardware, requires an explicit flag and an empty SMS provider, and never clears existing messages.

If a safety snapshot was taken with `adb shell content query` and accompanied by `manifest.json` (including counts and SHA-256) and `sms-ids.txt`, convert it without accessing the phone:

```sh
python3 tools/prepare_sms_fixture.py /private/path/to/safety-snapshot
```

Run on a fresh, dedicated Android emulator:

```sh
python3 tools/run_backup_e2e.py \
  --serial emulator-5554 \
  --google-config /Users/bruhascended/AndroidStudioProjects/Organiso/.config.json \
  --seed /private/path/to/emulator-sms-fixture.json
```

For subsequent test runs on the seeded emulator, omit `--seed`. The runner builds and installs only the staging/test APKs, validates emulator hardware before writing, and stores results and a verified JSON backup under `~/.organiso-device-backups/` with private filesystem permissions. It does not operate on a physical phone, uninstall apps, clear data, send SMS, or delete messages.

Coverage includes full fixture import comparison, JSON export, source-message fingerprint preservation, streamed Drive requests, checksum/status/redirect failure handling, cancellation, local file-picker export, and Google account-selection cancellation. Drive transport tests use a fake token and transport; a live OAuth/Drive upload must be checked separately with a signed-in test account.
