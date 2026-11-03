#!/usr/bin/env python3
"""Read-only inventory of an OFFLINE Market history-outbox snapshot.

An empty result is not proof of database rollback safety or server quiescence.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import struct
import sys
import uuid


MAX_RECORD_BYTES = 4096
MAX_ENTRIES = 100_000


def signature(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def identity(info):
    # Windows path and descriptor creation-time precision can differ. Compare
    # ctime only against another observation obtained through the same API.
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size, info.st_mtime_ns)


def read_stable(path):
    before = path.lstat()
    if not stat.S_ISREG(before.st_mode) or stat.S_ISLNK(before.st_mode):
        raise ValueError("not_regular_file")
    if before.st_size < 33 or before.st_size > MAX_RECORD_BYTES:
        raise ValueError("invalid_size")
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(path, flags)
    try:
        opened = os.fstat(fd)
        if identity(before) != identity(opened):
            raise ValueError("changed_during_read")
        with os.fdopen(fd, "rb", closefd=False) as stream:
            data = stream.read(MAX_RECORD_BYTES + 1)
        if signature(opened) != signature(os.fstat(fd)) or signature(before) != signature(path.lstat()):
            raise ValueError("changed_during_read")
    finally:
        os.close(fd)
    if len(data) != before.st_size:
        raise ValueError("changed_during_read")
    return data


def inspect_pending(path):
    data = read_stable(path)
    payload, checksum = data[:-32], data[-32:]
    if hashlib.sha256(payload).digest() != checksum:
        raise ValueError("checksum_mismatch")
    if len(payload) < 6:
        raise ValueError("invalid_header")
    version, id_size = struct.unpack(">iH", payload[:6])
    if id_size != 36 or len(payload) < 6 + id_size:
        raise ValueError("invalid_record_id")
    try:
        identifier = payload[6:6 + id_size].decode("ascii")
        if str(uuid.UUID(identifier)) != identifier or path.name != f"{identifier}.pending":
            raise ValueError("record_id_mismatch")
    except (UnicodeDecodeError, ValueError) as error:
        raise ValueError("invalid_record_id") from error
    return f"version_{version}" if version in (1, 2, 3) else "unknown_version"


def audit(directory):
    directory = Path(directory)
    info = directory.lstat()
    if not stat.S_ISDIR(info.st_mode) or stat.S_ISLNK(info.st_mode):
        raise ValueError("not_regular_directory")
    entries = sorted(directory.iterdir(), key=lambda path: path.name)
    if len(entries) > MAX_ENTRIES:
        raise ValueError("too_many_entries")
    counts = {"version_1": 0, "version_2": 0, "version_3": 0,
              "unknown_version": 0, "invalid": 0, "temporary": 0, "corrupt": 0, "other": 0}
    for path in entries:
        if path.name.endswith(".pending"):
            try:
                counts[inspect_pending(path)] += 1
            except (OSError, ValueError):
                counts["invalid"] += 1
        elif path.name.endswith(".tmp"):
            counts["temporary"] += 1
        elif path.name.endswith(".corrupt"):
            counts["corrupt"] += 1
        else:
            counts["other"] += 1
    if signature(info) != signature(directory.lstat()) or [p.name for p in entries] != sorted(p.name for p in directory.iterdir()):
        raise ValueError("directory_changed_during_audit")
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot_directory", help="offline, backed-up history-outbox directory")
    args = parser.parse_args()
    try:
        counts = audit(args.snapshot_directory)
    except (OSError, ValueError) as error:
        print(json.dumps({"status": "audit_failed", "reason": type(error).__name__}))
        return 2
    print(json.dumps({"status": "review_required" if any(counts.values()) else "no_entries_observed",
                      "counts": counts,
                      "note": "Header and checksum inventory only; does not prove server quiescence, complete payload validity, or database rollback safety."}))
    return 2 if any(counts.values()) else 0


if __name__ == "__main__":
    sys.exit(main())
