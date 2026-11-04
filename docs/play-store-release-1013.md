# Organiso 1.2 (1013): Play Console declarations

## Upload

Replace version 1012 in the draft release with `organiso-1.2-1013-play.aab`.
Version 1013 removes the unused `FOREGROUND_SERVICE_SPECIAL_USE` permission.
The app still uses `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`.

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

If Play Console requests a demonstration video, record both settings flows on a
test device containing synthetic messages. Show the user action, OTP confirmation,
foreground notification while the task runs, and completed operation. Do not expose
real SMS messages or account details. Supply the actual video URL in the form.

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
- Added optional SMS backups to your device and Google Drive.
- Added separate daily, weekly and monthly backup schedules.
- Improved search prompts, dual-SIM handling and notification permissions.
</en-GB>
```

References:
- https://support.google.com/googleplay/android-developer/answer/13392821
- https://support.google.com/googleplay/android-developer/answer/9514050
