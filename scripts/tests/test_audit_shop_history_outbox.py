import hashlib
import importlib.util
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import uuid


SCRIPT = Path(__file__).parents[1] / "audit-shop-history-outbox.py"
spec = importlib.util.spec_from_file_location("audit_shop_history_outbox", SCRIPT)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def write_record(directory, version, identifier=None):
    identifier = identifier or str(uuid.uuid4())
    raw_id = identifier.encode("ascii")
    payload = struct.pack(">iH", version, len(raw_id)) + raw_id + b"fixture"
    path = directory / f"{identifier}.pending"
    path.write_bytes(payload + hashlib.sha256(payload).digest())
    return path


class OutboxAuditTest(unittest.TestCase):
    def test_empty_snapshot_has_no_entries(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertFalse(any(audit.audit(temp).values()))

    def test_command_exit_status_never_certifies_nonempty_outbox(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            empty = subprocess.run([sys.executable, "-B", str(SCRIPT), str(root)], capture_output=True, text=True)
            self.assertEqual(0, empty.returncode)
            self.assertIn('"no_entries_observed"', empty.stdout)
            self.assertIn("does not prove", empty.stdout)
            write_record(root, 1)
            pending = subprocess.run([sys.executable, "-B", str(SCRIPT), str(root)], capture_output=True, text=True)
            self.assertEqual(2, pending.returncode)
            self.assertIn('"review_required"', pending.stdout)

    def test_inventory_preserves_all_bytes_and_counts_formats(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for version in (1, 2, 3, 99):
                write_record(root, version)
            (root / "unfinished.tmp").write_bytes(b"partial")
            (root / "old.corrupt").write_bytes(b"bad")
            (root / "unknown.txt").write_bytes(b"other")
            before = {path.name: path.read_bytes() for path in root.iterdir()}
            counts = audit.audit(root)
            self.assertEqual([1, 1, 1, 1], [counts[f"version_{x}"] for x in (1, 2, 3)] + [counts["unknown_version"]])
            self.assertEqual((1, 1, 1), (counts["temporary"], counts["corrupt"], counts["other"]))
            self.assertEqual(before, {path.name: path.read_bytes() for path in root.iterdir()})

    def test_bad_checksum_and_mismatched_filename_are_invalid(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            damaged = write_record(root, 1)
            original = damaged.read_bytes()
            damaged.write_bytes(original[:-1] + bytes([original[-1] ^ 1]))
            renamed = write_record(root, 3)
            renamed.rename(root / f"{uuid.uuid4()}.pending")
            self.assertEqual(2, audit.audit(root)["invalid"])

    def test_descriptor_creation_time_precision_can_differ_from_path(self):
        with tempfile.TemporaryDirectory() as temp:
            path = write_record(Path(temp), 3)
            actual_fstat = os.fstat
            def descriptor_stat(fd):
                info = actual_fstat(fd)
                values = {key: getattr(info, key) for key in (
                    "st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_ctime_ns")}
                values["st_ctime_ns"] += 100
                return SimpleNamespace(**values)
            with patch.object(audit.os, "fstat", side_effect=descriptor_stat):
                self.assertEqual("version_3", audit.inspect_pending(path))

    def test_symlink_is_never_opened_as_a_pending_record(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            target = root / "outside.txt"
            target.write_bytes(b"private fixture")
            try:
                (root / f"{uuid.uuid4()}.pending").symlink_to(target)
            except (OSError, NotImplementedError):
                self.skipTest("symlink creation unavailable")
            self.assertEqual(1, audit.audit(root)["invalid"])
            self.assertEqual(b"private fixture", target.read_bytes())

    def test_changed_directory_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_record(root, 1)
            real_inspect = audit.inspect_pending
            def change(path):
                result = real_inspect(path)
                (root / "added.tmp").write_bytes(b"change")
                return result
            with patch.object(audit, "inspect_pending", side_effect=change):
                with self.assertRaisesRegex(ValueError, "directory_changed_during_audit"):
                    audit.audit(root)


if __name__ == "__main__":
    unittest.main()
