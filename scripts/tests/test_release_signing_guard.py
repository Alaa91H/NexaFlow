from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
GRADLE = ROOT / "app" / "build.gradle.kts"
WORKFLOW = ROOT / ".github" / "workflows" / "nexaflow-ci.yml"


class ReleaseSigningGuardTest(unittest.TestCase):
    def test_debug_signing_is_opt_in_for_release_builds(self):
        source = GRADLE.read_text(encoding="utf-8")
        self.assertIn('providers.gradleProperty("allowDebugSigning")', source)
        self.assertIn("release builds require production signing", source.lower())
        self.assertIn("allowDebugSigning=true", source)

    def test_untagged_ci_explicitly_opts_in_but_tagged_release_does_not(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("Build untagged APKs with explicit debug signing opt-in", workflow)
        self.assertIn("./gradlew assembleDebug assembleRelease -PallowDebugSigning=true", workflow)
        self.assertIn("Build production-signed tag APKs", workflow)
        self.assertIn("./gradlew assembleDebug assembleRelease", workflow)


if __name__ == "__main__":
    unittest.main()
