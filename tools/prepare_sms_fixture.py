#!/usr/bin/env python3
"""Convert a checksum-verified `adb shell content query` safety export to an emulator fixture.

Input and output contain private SMS. Keep both outside the repository. Does not use adb.
Refuses inconsistent or ambiguous column separators instead of guessing message contents.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re


def convert(backup_dir: Path) -> Path:
    manifest = json.loads((backup_dir / "manifest.json").read_text())
    raw = (backup_dir / "sms-full.txt").read_bytes()
    assert hashlib.sha256(raw).hexdigest() == manifest["files"]["sms-full.txt"]["sha256"], "Safety export checksum mismatch"
    # Android prints one newline after each row. Preserve any newlines within the body.
    rows = re.split(r"(?m)^Row: (\d+) _id=", raw.decode("utf-8"))
    assert rows[0] == "", "Unrecognized content-provider export"
    first = "_id=" + rows[2]
    columns = re.findall(r"(?:^|, )([a-zA-Z_][a-zA-Z_0-9]*)=", first)
    assert columns[0] == "_id" and "body" in columns
    messages = []
    for index in range(1, len(rows), 2):
        assert int(rows[index]) == len(messages), "Unexpected row sequence"
        text = "_id=" + rows[index + 1]
        assert text.endswith("\n"), "Truncated export row"
        text = text[:-1]
        message = {}
        for position, column in enumerate(columns):
            assert text.startswith(column + "="), f"Unexpected column at row {len(messages)}"
            text = text[len(column) + 1:]
            if position + 1 < len(columns):
                delimiter = ", " + columns[position + 1] + "="
                assert text.count(delimiter) == 1, f"Ambiguous column separator at row {len(messages)}"
                value, text = text.split(delimiter, 1)
                text = columns[position + 1] + "=" + text
            else:
                value = text
            # Preserve text verbatim; only the documented numeric SMS fields are converted.
            numeric = {"_id", "thread_id", "person", "date", "date_sent", "protocol", "read",
                       "status", "type", "reply_path_present", "locked", "sub_id", "error_code", "seen"}
            message[column] = None if value == "NULL" else int(value) if column in numeric else value
        messages.append(message)
    assert len(messages) == manifest["files"]["sms-full.txt"]["rows"], "Message count mismatch"
    ids = [message["_id"] for message in messages]
    assert len(set(ids)) == len(ids), "Duplicate SMS IDs"
    expected_ids = [int(value) for value in re.findall(r"(?m)^Row: \d+ _id=(\d+)$", (backup_dir / "sms-ids.txt").read_text())]
    assert ids == expected_ids, "Exported IDs do not match independent safety snapshot"
    destination = backup_dir / "emulator-sms-fixture.json"
    document = {"format": "organiso-sms-backup", "version": 1, "createdAt": manifest["created_utc"],
                "source": "checksum-verified-adb-export", "sms": messages, "messageCount": len(messages)}
    destination.write_text(json.dumps(document, ensure_ascii=False), encoding="utf-8")
    destination.chmod(0o600)
    print(f"Prepared {len(messages)} SMS records: {destination}")
    print(f"SHA-256: {hashlib.sha256(destination.read_bytes()).hexdigest()}")
    return destination


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("backup_directory", type=Path)
    convert(parser.parse_args().backup_directory)
