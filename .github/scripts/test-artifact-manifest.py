"""Record successful CI TEST output without including caches, secrets or runtime data."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
from defusedxml import ElementTree
import zipfile


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def suite_counts(report):
    suite = ElementTree.parse(report, forbid_dtd=True, forbid_entities=True, forbid_external=True).getroot()
    require(suite.tag == "testsuite", "Expected testsuite root")
    counts = {key: int(suite.attrib.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
    require(all(value >= 0 for value in counts.values()), "Negative test count")
    require(counts["failures"] == counts["errors"] == 0, "Failed test suite: " + report.name)
    return {"suite": suite.attrib["name"], **counts}


source = git("rev-parse", "HEAD")
version = f"1.0.53-market-ui.{source[:12]}-UNMERGED-TEST"
require(os.environ["MARKET_TEST_VERSION"] == version, "Unexpected TEST version")
require(not git("status", "--porcelain", "--untracked-files=no"), "Tracked source is dirty")
output = Path("build/test-artifact")
output.mkdir(parents=True, exist_ok=True)
jars = list(Path("build/libs").glob(f"*-{version}.jar"))
require(len(jars) == 1, f"Expected one labelled shadow JAR, found {len(jars)}")
jar = jars[0]
with zipfile.ZipFile(jar) as archive:
    descriptor = archive.read("paper-plugin.yml").decode()
require(any(line.strip().strip("'\"") == version for line in (
    line.split(":", 1)[1] for line in descriptor.splitlines() if line.startswith("version:")
)), "Plugin descriptor version does not match CI source")
suites = []
for report in sorted(Path("build/test-results/test").glob("TEST-*.xml")):
    suites.append(suite_counts(report))
require(bool(suites) and sum(row["tests"] for row in suites) > 0, "No executed test suites")
companion = Path(os.environ["LUMAGUILDS_JAR"])
require(sha256(companion).lower() == os.environ["LUMAGUILDS_SHA256"].lower(), "Companion hash mismatch")
manifest = {
    "purpose": "UNMERGED TEST artifact; no release or deployment authorization",
    "source_sha": source, "source_tree": git("rev-parse", "HEAD^{tree}"),
    "version": version, "jar": {"file": jar.name, "sha256": sha256(jar)},
    "nexus_source": os.environ["NEXUS_REF"],
    "companion": {"source_ref": os.environ["LUMAGUILDS_REF"], "sha256": sha256(companion)},
    "dependency_inventory_sha256": sha256(output / "dependencies.json"),
    "test_suites": suites,
}
(output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
print(json.dumps({"source_sha": source, "version": version, "jar_sha256": sha256(jar)}))
