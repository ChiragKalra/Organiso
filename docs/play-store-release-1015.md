# Organiso 1.2 (1015): Play Console declarations

## Upload

Replace the older bundle in the draft release with `organiso-1.2-1015-play.aab`.
Version 1015 carries forward removal of the unused `FOREGROUND_SERVICE_SPECIAL_USE`
permission. It also checks Android's SMS role directly on Android 10+ so SMS save and
delete operations are not skipped when the legacy default-app API returns null.
User-initiated maintenance notifications request immediate display on Android 12+.
Hidden conversation lists stop binding paging updates while Settings is open, and
empty-state database checks run off the UI thread. The 20,000-conversation
reclassification scenario now completes and returns to the category list without
an input-dispatch timeout.
The app still uses `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`.

## Dialog readability fix

AppCompat and framework alert dialogs now use matching light/dark surfaces and
text colors. The dark title's rendered contrast increased from 1.16:1 to 14.04:1;
light titles reach 13.74:1. Title and button contrast is verified in eight cases
covering both dialog APIs, both app themes and both system night-mode settings.

## Foreground service declaration

Open App content > Foreground service permissions. Declare Data sync for
user-initiated local processing, using the actual features below. Do not select
cloud backup as the foreground-service use case: scheduled backups use WorkManager,
and manual backups use a ViewModel coroutine, rather than these foreground services.

Suggested functionality description:

> Organiso uses a dataSync foreground service for two local SMS-processing tasks
> explicitly started by the user in Settings > Messages: deleting existing expired
> OTP messages after confirmation, and reclassifying existing personal conversations
> when the user enables contacts-only personal classification. Each operation shows
> a foreground notification describing the work and stops its service when the task
> is complete. These services process messages locally and do not upload SMS data.

Suggested explanation of delay or interruption:

> The user requests these operations to update existing messages immediately.
> Deferring the work leaves the requested cleanup or classification incomplete.
> Interrupting it can leave only part of the existing message history processed;
> a foreground notification makes the ongoing operation visible while it finishes.

The prepared demonstration is in
`~/Downloads/Organiso-Play-Demo-1015/Organiso-Foreground-Service-Demo-1015.mp4`.
It shows both settings flows, the readable OTP confirmation, the foreground
notifications and completed operations using synthetic messages on Android 16.
The 79.3-second video shortens processing waits and labels those edits. The OTP
flow verifies 100 expired messages removed from both stores and two recent messages
preserved; reclassification moves 20,000 conversations to Priority while preserving
all 20,002 SMS messages. Raw recordings and test logs are in the accompanying
`evidence` folder. Host the video at a reviewer-accessible URL, then supply that URL
in the foreground-service declaration.

## Government apps declaration

Answer No: Organiso is an independently developed SMS organiser, not an app
developed by or on behalf of a government.

## Deobfuscation warning

The release has `minifyEnabled false`. No R8/ProGuard mapping file is produced;
there is no missing mapping file to upload for this build.

## Release notes

```text
<en-GB>
- Improved compatibility and screen layouts on Android 16.
- Fixed confirmation-dialog readability in light and dark mode.
- Added optional SMS backups to your device and Google Drive.
- Added separate daily, weekly and monthly backup schedules.
- Improved search prompts, dual-SIM handling and notification permissions.
</en-GB>
```

References:
- https://support.google.com/googleplay/android-developer/answer/13392821
- https://support.google.com/googleplay/android-developer/answer/9514050
