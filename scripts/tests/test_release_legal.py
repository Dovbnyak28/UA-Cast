import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("release_legal", ROOT / "scripts/check-release-legal.py")
LEGAL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(LEGAL)


class ReleaseLegalTests(unittest.TestCase):
    def ready(self):
        return {"schemaVersion": 1,
                "checks": {name: {"approved": True, "evidence": "Verified fixture"}
                           for name in LEGAL.REQUIRED},
                "commerce": {"enabled": False, "sellerReviewApproved": False}}

    def test_unknown_or_missing_check_blocks_release(self):
        state = self.ready()
        del state["checks"]["native_corresponding_source"]
        self.assertTrue(LEGAL.assess(state, "releaseRuntimeClasspath\nBUILD SUCCESSFUL"))

    def test_approval_without_evidence_is_not_enough(self):
        state = self.ready()
        state["checks"]["raster_asset_rights"]["evidence"] = " "
        self.assertTrue(LEGAL.assess(state, "releaseRuntimeClasspath\nBUILD SUCCESSFUL"))

    def test_real_dependency_blocks_even_when_approvals_are_true(self):
        graph = "releaseRuntimeClasspath\n+--- io.github.anilbeesetti:nextlib-media3ext:1.10.1-0.13.0\nBUILD SUCCESSFUL"
        self.assertTrue(LEGAL.assess(self.ready(), graph))

    def test_incomplete_dependency_report_blocks(self):
        self.assertTrue(LEGAL.assess(self.ready(), "releaseRuntimeClasspath"))

    def test_live_checkout_needs_commercial_review(self):
        self.assertTrue(LEGAL.assess(self.ready(), "releaseRuntimeClasspath\nBUILD SUCCESSFUL", True))

    def test_verified_free_fixture_can_pass(self):
        self.assertEqual([], LEGAL.assess(self.ready(), "releaseRuntimeClasspath\nBUILD SUCCESSFUL"))

    def test_malformed_types_fail_closed_without_crashing(self):
        report = "releaseRuntimeClasspath\nBUILD SUCCESSFUL"
        for state in (None, [], "approved", 1):
            with self.subTest(state=state):
                self.assertTrue(LEGAL.assess(state, report))
        for malformed in (None, [], "approved", 1):
            state = self.ready()
            state["checks"] = malformed
            self.assertTrue(LEGAL.assess(state, report))

    def test_non_text_evidence_cannot_approve_a_check(self):
        for evidence in (None, True, 42, ["verified"], {"verified": True}):
            state = self.ready()
            state["checks"]["decoder_license_migration"]["evidence"] = evidence
            self.assertTrue(LEGAL.assess(state, "releaseRuntimeClasspath\nBUILD SUCCESSFUL"))

    def test_malformed_check_and_live_commerce_are_rejected(self):
        report = "releaseRuntimeClasspath\nBUILD SUCCESSFUL"
        state = self.ready()
        state["checks"]["cast_entry_point"] = True
        self.assertTrue(LEGAL.assess(state, report))
        for commerce in (None, [], True):
            state = self.ready()
            state["commerce"] = commerce
            self.assertTrue(LEGAL.assess(state, report, True))


if __name__ == "__main__":
    unittest.main()
