import importlib.util
import pathlib
import tempfile
import unittest
import uuid

SCRIPT = pathlib.Path(__file__).parents[1] / "inspect-sales-generation.py"
SPEC = importlib.util.spec_from_file_location("inspect_sales_generation", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class InspectGenerationTest(unittest.TestCase):
    def test_valid_control_is_read_only_and_does_not_authorize_recovery(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "generation"
            value = str(uuid.uuid4())
            original = (value + "\n").encode("ascii")
            path.write_bytes(original)
            report = MODULE.inspect_generation(path)
            self.assertEqual(value, report["generation"])
            self.assertEqual("inspection_only", report["status"])
            self.assertFalse(report["proves_server_stopped"])
            self.assertFalse(report["proves_database_identity"])
            self.assertFalse(report["authorizes_activation"])
            self.assertEqual(original, path.read_bytes())

    def test_invalid_or_oversized_control_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "generation"
            for raw in (b"invalid", b"x" * 65, b"A" * 36, b"\xff" * 36):
                path.write_bytes(raw)
                with self.assertRaises((ValueError, UnicodeError)):
                    MODULE.inspect_generation(path)

    def test_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            target = root / "target"
            target.write_text(str(uuid.uuid4()), encoding="ascii")
            link = root / "link"
            try:
                link.symlink_to(target)
            except OSError as error:
                self.skipTest("symlink creation unavailable: " + str(error))
            with self.assertRaises(ValueError):
                MODULE.inspect_generation(link)


if __name__ == "__main__":
    unittest.main()
