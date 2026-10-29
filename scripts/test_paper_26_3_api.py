#!/usr/bin/env python3
"""Contract checks for an actual stable Paper 26.3 build/runtime CI lane."""
from pathlib import Path
import unittest

WORKFLOW = (Path(__file__).resolve().parents[1] / ".github" / "workflows" / "build.yml")


class StablePaper263ContractTest(unittest.TestCase):
    def setUp(self):
        self.source = WORKFLOW.read_text(encoding="utf-8")

    def test_compiles_against_published_stable_paper_263(self):
        self.assertIn("-Dpaper.version=26.3.build.205-stable clean verify", self.source)
        self.assertNotIn("26.3-rc-3.build.1-alpha", self.source)

    def test_native_paper_matrix_preserves_old_and_new_floor(self):
        matrix = self.source.split("        paper_version:\n", 1)[1].split("    steps:", 1)[0]
        for version in ("1.21.11", "26.2", "26.3"):
            self.assertIn(f"          - '{version}'", matrix)
        self.assertIn('No stable Paper ${PAPER_VERSION} build returned.', self.source)

    def test_publication_still_requires_manual_exact_tag(self):
        self.assertIn("if: github.event_name == 'workflow_dispatch' && startsWith(github.ref, 'refs/tags/v')", self.source)
        self.assertIn("run: bash scripts/publish_immutable_release.sh", self.source)
        self.assertNotIn("--clobber", self.source)


if __name__ == "__main__":
    unittest.main()
