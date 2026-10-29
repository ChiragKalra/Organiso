#!/usr/bin/env python3
"""Build and verify SMS backup on an explicitly named emulator. Never targets a physical phone."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.bruhascended.organiso.staging"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--google-config", type=Path, default=ROOT / ".config.json")
    parser.add_argument("--seed", type=Path, help="Import this fixture; the emulator SMS provider must be empty")
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("Only emulator serials are accepted. No physical-device operations are supported.")
    adb = ["adb", "-s", args.serial]

    def run(command, **kwargs):
        return subprocess.run(command, check=True, text=True, **kwargs)

    hardware = run(adb + ["shell", "getprop", "ro.hardware"], capture_output=True).stdout.strip()
    if hardware not in ("ranchu", "goldfish"):
        parser.error("Emulator hardware check failed")
    config_bytes = args.google_config.read_bytes()
    config = json.loads(config_bytes).get("installed", {})
    if not config.get("client_id", "").endswith(".apps.googleusercontent.com") or not config.get("project_id"):
        parser.error("Expected a Google Android OAuth installed-client configuration")
    if config.get("auth_uri") != "https://accounts.google.com/o/oauth2/auth" or config.get("token_uri") != "https://oauth2.googleapis.com/token":
        parser.error("Unexpected OAuth endpoints in test configuration")
    output = args.output or Path.home() / ".organiso-device-backups" / (
        "emulator-verification-" + datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ"))
    output.mkdir(parents=True, exist_ok=True, mode=0o700)
    output.chmod(0o700)
    report = {"emulator": args.serial, "google_config_sha256": hashlib.sha256(config_bytes).hexdigest(),
              "google_project": config["project_id"], "package": PACKAGE,
              "live_drive_upload": "Requires a signed-in Google test account and matching Android OAuth registration"}
    if not args.skip_build:
        with (output / "build.log").open("w") as log:
            run([str(ROOT / "gradlew"), ":app:assembleDebug", ":app:assembleDebugAndroidTest",
                 "-PdebugApplicationIdSuffix=.staging", "-PdebugVersionNameSuffix=-staging",
                 "-PdebugAppLabel=Organiso Staging", "--console=plain"],
                cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    metadata = json.loads((ROOT / "app/build/outputs/apk/debug/output-metadata.json").read_text())
    if metadata.get("applicationId") != PACKAGE:
        parser.error("APK is not the isolated device-test build. Rebuild without --skip-build.")
    for apk in ("app/build/outputs/apk/debug/app-debug.apk",
                "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
        run(adb + ["install", "-r", str(ROOT / apk)])
    run(adb + ["shell", "cmd", "role", "add-role-holder", "--user", "0", "android.app.role.SMS", PACKAGE])
    run(adb + ["shell", "pm", "grant", PACKAGE, "android.permission.READ_SMS"])

    def instrument(test_class, filename, extra=None):
        command = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                         "com.bruhascended.organiso.backup." + test_class] + (extra or []) + [RUNNER]
        with (output / filename).open("w") as log:
            run(command, stdout=log, stderr=subprocess.STDOUT)
        result = (output / filename).read_text()
        if "FAILURES!!!" in result or "INSTRUMENTATION_FAILED" in result or "OK (" not in result:
            raise RuntimeError(f"Instrumentation failed. See {output / filename}")
        print(result.strip())

    if args.seed:
        run(adb + ["push", str(args.seed), "/data/local/tmp/organiso-seed.json"])
        run(adb + ["shell", "run-as", PACKAGE, "mkdir", "-p", "files"])
        run(adb + ["shell", "run-as", PACKAGE, "cp", "/data/local/tmp/organiso-seed.json", "files/emulator-sms-fixture.json"])
        instrument("EmulatorSmsSeedTest", "seed.log", ["-e", "allowEmulatorSmsSeed", "true"])
    instrument("BackupDeviceTest", "tests.log")
    for name in ("verified-sms-safety-backup.json", "verified-sms-safety-backup-manifest.json"):
        destination = output / name
        with destination.open("wb") as stream:
            subprocess.run(adb + ["exec-out", "run-as", PACKAGE, "cat", "files/" + name], stdout=stream, check=True)
        destination.chmod(0o600)
    manifest = json.loads((output / "verified-sms-safety-backup-manifest.json").read_text())
    actual = hashlib.sha256((output / "verified-sms-safety-backup.json").read_bytes()).hexdigest()
    assert actual == manifest["sha256"], "Pulled backup checksum mismatch"
    report.update({"sms_count": manifest["count"], "verified_backup_sha256": actual, "tests": "passed"})
    (output / "report.json").write_text(json.dumps(report, indent=2))
    (output / "report.json").chmod(0o600)
    print(f"Verified {manifest['count']} SMS records. Private report: {output / 'report.json'}")


if __name__ == "__main__":
    main()
