#!/usr/bin/env python3
"""Offline regression tests for immutable manual-only release publication."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PUBLISH = ROOT / "scripts" / "publish_immutable_release.sh"
WORKFLOW = ROOT / ".github" / "workflows" / "build.yml"


class ImmutableReleasePolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.workdir = Path(self.temp.name)
        (self.workdir / "dist").mkdir()
        with zipfile.ZipFile(self.workdir / "dist" / "SF_BetterChests1.0.3.jar", "w") as jar:
            jar.writestr("plugin.yml", "name: BetterChests\\nversion: 1.0.3\\n")
        fakebin = self.workdir / "bin"
        fakebin.mkdir()
        gh = fakebin / "gh"
        gh.write_text(
            "#!/bin/sh\n"
            "printf '%s\\n' \"$*\" >> \"$GH_CALL_LOG\"\n"
            "if [ \"$1\" = release ] && [ \"$2\" = view ]; then\n"
            "  [ \"$GH_RELEASE_EXISTS\" = 1 ]; exit $?\n"
            "fi\n"
            "if [ \"$1\" = release ] && [ \"$2\" = create ]; then\n"
            "  [ \"$GH_CREATE_FAIL\" != 1 ]; exit $?\n"
            "fi\n"
            "exit 93\n"
        )
        gh.chmod(0o755)
        self.call_log = self.workdir / "gh-calls.log"
        self.env = dict(os.environ)
        self.env.update({
            "PATH": str(fakebin) + os.pathsep + os.environ["PATH"],
            "GH_CALL_LOG": str(self.call_log),
            "GH_RELEASE_EXISTS": "0",
            "GH_CREATE_FAIL": "0",
            "GITHUB_REPOSITORY": "wickidcow/SF_BetterChests",
            "GITHUB_SHA": "f" * 40,
            "GITHUB_REF": "refs/tags/v1.0.3",
            "GITHUB_EVENT_NAME": "workflow_dispatch",
            "VERSION": "1.0.3",
            "OUTPUT_NAME": "SF_BetterChests1.0.3.jar",
        })

    def execute(self):
        return subprocess.run(
            ["bash", str(PUBLISH)], cwd=self.workdir, env=self.env,
            capture_output=True, text=True, check=False
        )

    def calls(self):
        return self.call_log.read_text().splitlines() if self.call_log.exists() else []

    def test_exact_manual_tag_creates_once_without_upload_or_clobber(self):
        result = self.execute()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(2, len(self.calls()))
        self.assertIn("release view v1.0.3 --repo wickidcow/SF_BetterChests", self.calls()[0])
        created = self.calls()[1]
        self.assertIn("release create v1.0.3 dist/SF_BetterChests1.0.3.jar", created)
        self.assertIn("--verify-tag", created)
        self.assertIn("--target " + "f" * 40, created)
        self.assertNotIn("--clobber", created)
        self.assertNotIn("release upload", created)

    def test_existing_release_refused_even_when_artifact_is_present(self):
        self.env["GH_RELEASE_EXISTS"] = "1"
        result = self.execute()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("already exists", result.stderr)
        self.assertEqual(1, len(self.calls()))
        self.assertNotIn("create", self.calls()[0])

    def test_main_push_never_reaches_github(self):
        self.env.update(GITHUB_EVENT_NAME="push", GITHUB_REF="refs/heads/main")
        self.assertNotEqual(0, self.execute().returncode)
        self.assertEqual([], self.calls())

    def test_tag_push_never_publishes_automatically(self):
        self.env["GITHUB_EVENT_NAME"] = "push"
        self.assertNotEqual(0, self.execute().returncode)
        self.assertEqual([], self.calls())

    def test_manual_branch_run_never_publishes(self):
        self.env["GITHUB_REF"] = "refs/heads/main"
        self.assertNotEqual(0, self.execute().returncode)
        self.assertEqual([], self.calls())

    def test_manual_other_version_tag_refused(self):
        self.env["GITHUB_REF"] = "refs/tags/v1.0.5"
        self.assertNotEqual(0, self.execute().returncode)
        self.assertEqual([], self.calls())

    def test_artifact_version_mismatch_refused(self):
        self.env["OUTPUT_NAME"] = "SF_BetterChests1.0.5.jar"
        self.assertNotEqual(0, self.execute().returncode)
        self.assertEqual([], self.calls())

    def test_missing_jar_fails_before_github(self):
        (self.workdir / "dist" / "SF_BetterChests1.0.3.jar").unlink()
        self.assertNotEqual(0, self.execute().returncode)
        self.assertEqual([], self.calls())

    def test_invalid_archive_is_refused_before_github(self):
        (self.workdir / "dist" / "SF_BetterChests1.0.3.jar").write_bytes(b"invalid archive")
        self.assertNotEqual(0, self.execute().returncode)
        self.assertEqual([], self.calls())

    def test_github_create_failure_does_not_claim_success(self):
        self.env["GH_CREATE_FAIL"] = "1"
        self.assertNotEqual(0, self.execute().returncode)
        self.assertEqual(2, len(self.calls()))

    def test_workflow_calls_the_guard_only_for_manual_version_tags(self):
        text = WORKFLOW.read_text()
        self.assertIn(
            "if: github.event_name == 'workflow_dispatch' && "
            "startsWith(github.ref, 'refs/tags/v')", text
        )
        self.assertIn("run: bash scripts/publish_immutable_release.sh", text)
        self.assertIn("python3 -m unittest discover -s scripts "
                      "-p 'test_publish_immutable_release.py' -v", text)
        self.assertNotIn("gh release upload", text)
        self.assertNotIn("--clobber", text)


if __name__ == "__main__":
    unittest.main()
