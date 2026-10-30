"""End-to-end fixtures for the actual CI manifest script; no application runtime."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "test-artifact-manifest.py"
DESCRIPTOR = SCRIPT.parents[2] / "src/main/resources/paper-plugin.yml"


class ManifestFixtures(unittest.TestCase):
    def test_fail_closed_with_and_without_optimization(self):
        scenarios = ("plain", "single", "double", "wrong_version", "dirty", "duplicate_jar",
                     "malformed_xml", "entity_xml", "external_entity", "failed_suite", "no_reports", "bad_companion")
        for optimized in (False, True):
            for scenario in scenarios:
                with self.subTest(optimized=optimized, scenario=scenario):
                    self.run_fixture(optimized, scenario)

    def run_fixture(self, optimized, scenario):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = prepare_source(root)
            version = f"1.0.53-market-ui.{source[:12]}-UNMERGED-TEST"
            make_jar(root, version, scenario)
            make_reports(root, scenario)
            env = make_environment(root, version, scenario)
            result = run_manifest(root, env, optimized)
            valid = scenario in ("plain", "single", "double")
            self.assertEqual(result.returncode == 0, valid, result.stderr)
            manifest = root / "build/test-artifact/manifest.json"
            self.assertEqual(manifest.exists(), valid)
            if valid:
                self.check_manifest(manifest, source)

    def check_manifest(self, manifest, source):
        data = json.loads(manifest.read_text())
        self.assertEqual(data["source_sha"], source)
        self.assertEqual(data["test_suites"][0]["tests"], 1)


def prepare_source(root):
    def git(*args):
        return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()

    git("init", "--quiet")
    (root / "source.txt").write_text("fixture source\n")
    git("add", "source.txt")
    git("-c", "user.name=Manifest Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "--quiet", "-m", "fixture")
    return git("rev-parse", "HEAD")


def make_jar(root, version, scenario):
    literal = {"single": f"'{version}'", "double": f'"{version}"',
               "wrong_version": "WRONG"}.get(scenario, version)
    libs = root / "build/libs"
    libs.mkdir(parents=True)
    descriptor = DESCRIPTOR.read_text().replace("${version}", literal)
    with zipfile.ZipFile(libs / f"Market-{version}.jar", "w") as archive:
        archive.writestr("paper-plugin.yml", descriptor)
    if scenario == "duplicate_jar":
        (libs / f"Duplicate-{version}.jar").write_bytes(b"duplicate")


def make_reports(root, scenario):
    reports = root / "build/test-results/test"
    reports.mkdir(parents=True)
    xml = '<testsuite name="fixture" tests="1" failures="0" errors="0" skipped="0"/>'
    xml = {"malformed_xml": '<testsuite name="broken">',
           "entity_xml": '<!DOCTYPE testsuite [<!ENTITY x "expanded">]><testsuite name="&x;" tests="1"/>',
           "external_entity": '<!DOCTYPE testsuite SYSTEM "file:///never-read"><testsuite name="fixture" tests="1"/>',
           "failed_suite": '<testsuite name="failed" tests="1" failures="1"/>'}.get(scenario, xml)
    if scenario != "no_reports":
        (reports / "TEST-fixture.xml").write_text(xml)


def make_environment(root, version, scenario):
    evidence = root / "build/test-artifact"
    evidence.mkdir(parents=True)
    (evidence / "dependencies.json").write_text("[]\n")
    companion = root / "companion.jar"
    companion.write_bytes(b"fixture companion")
    if scenario == "dirty":
        (root / "source.txt").write_text("changed source\n")
    env = dict(os.environ, MARKET_TEST_VERSION=version, LUMAGUILDS_JAR=str(companion),
               LUMAGUILDS_SHA256=hashlib.sha256(companion.read_bytes()).hexdigest(),
               NEXUS_REF="fixture-nexus", LUMAGUILDS_REF="fixture-companion")
    if scenario == "bad_companion":
        env["LUMAGUILDS_SHA256"] = "0" * 64
    return env


def run_manifest(root, env, optimized):
    (root / "manifest.py").write_bytes(SCRIPT.read_bytes())
    if optimized:
        return subprocess.run(["python3", "-O", "manifest.py"], cwd=root, env=env,
                              capture_output=True, text=True, check=False)
    return subprocess.run(["python3", "manifest.py"], cwd=root, env=env,
                          capture_output=True, text=True, check=False)


if __name__ == "__main__":
    unittest.main()
