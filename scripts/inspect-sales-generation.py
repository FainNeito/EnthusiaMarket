#!/usr/bin/env python3
"""Inspect an offline generation-control file; output is not recovery authorization."""
import argparse
import hashlib
import json
import os
import stat
import sys
import uuid


def inspect_generation(path):
    before = os.lstat(path)
    if not stat.S_ISREG(before.st_mode):
        raise ValueError("control path must be a regular, non-symlink file")
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    with os.fdopen(os.open(path, flags), "rb") as stream:
        opened = os.fstat(stream.fileno())
        raw = stream.read(65)
        after = os.fstat(stream.fileno())
    final = os.lstat(path)
    def identity(value):
        return (value.st_dev, value.st_ino, value.st_size, value.st_mtime_ns)
    if any(identity(item) != identity(before) for item in (opened, after, final)):
        raise ValueError("control file changed during inspection")
    if not 36 <= len(raw) <= 64:
        raise ValueError("invalid control file size")
    value = raw.decode("ascii").strip()
    parsed = uuid.UUID(value)
    if str(parsed) != value:
        raise ValueError("generation must be a canonical lowercase UUID")
    return {"generation": value, "file_sha256": hashlib.sha256(raw).hexdigest(),
            "status": "inspection_only", "proves_server_stopped": False,
            "proves_database_identity": False, "authorizes_activation": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("control_file", help="trusted offline copy of the external control file")
    args = parser.parse_args()
    try:
        print(json.dumps(inspect_generation(args.control_file), sort_keys=True))
    except (OSError, UnicodeError, ValueError) as error:
        print("inspection failed: " + str(error), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
