"""Exercise the actual signer predicates with public, local metadata fixtures."""
import json
from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[2]
SIGNER = ROOT / ".github/workflows/android-projects-signed-bundle.yml"


def predicate(filename):
    text = SIGNER.read_text()
    match = re.search(r" '([^']+)' \"\$RUNNER_TEMP/" + re.escape(filename) + r'\"', text)
    if not match:
        raise AssertionError("Missing actual signer metadata predicate")
    return match.group(1)


def permits(expression, data, arguments):
    result = subprocess.run(["jq", "-e", *arguments, expression], input=json.dumps(data),
                            text=True, capture_output=True, check=False)
    return result.returncode == 0


class SourceContractTests(unittest.TestCase):
    def setUp(self):
        self.run = {"id": 42, "head_sha": "a" * 40, "head_branch": "main",
                    "status": "completed", "conclusion": "success", "event": "workflow_dispatch",
                    "path": ".github/workflows/android-ci.yml",
                    "repository": {"full_name": "acciento89-bot/MAGCalc"},
                    "head_repository": {"full_name": "acciento89-bot/MAGCalc"}}
        self.run_args = ["--arg", "sha", "a" * 40, "--arg", "repo", "acciento89-bot/MAGCalc",
                         "--argjson", "run", "42"]

    def run_allowed(self, value):
        return permits(predicate("source-run.json"), value, self.run_args)

    def test_exact_audited_android_main_source_run_is_accepted(self):
        self.assertTrue(self.run_allowed(self.run))

    def test_different_green_workflow_is_rejected(self):
        for path in [".github/workflows/ci.yml", ".github/workflows/ios-build.yml", None]:
            with self.subTest(path=path):
                self.assertFalse(self.run_allowed({**self.run, "path": path}))

    def test_nonmanual_source_run_is_rejected(self):
        for event in ["push", "pull_request", "workflow_run", None]:
            with self.subTest(event=event):
                self.assertFalse(self.run_allowed({**self.run, "event": event}))

    def test_same_sha_successful_other_workflow_is_rejected(self):
        unrelated = {**self.run, "path": ".github/workflows/unrelated-green.yml",
                     "event": "workflow_run"}
        self.assertFalse(self.run_allowed(unrelated))

    def test_source_sha_branch_status_and_repository_remain_exact(self):
        variants = [{"id": 43}, {"head_sha": "b" * 40}, {"head_branch": "feature/release"},
                    {"status": "in_progress"}, {"conclusion": "failure"}, {"conclusion": "cancelled"},
                    {"repository": {"full_name": "other/repo"}},
                    {"head_repository": {"full_name": "other/repo"}}]
        for delta in variants:
            with self.subTest(delta=delta):
                self.assertFalse(self.run_allowed({**self.run, **delta}))

    def test_artifact_id_digest_name_source_run_and_expiry_remain_exact(self):
        data = {"id": 77, "name": "MAGCalc-1.2.0-7-unsigned-aab", "expired": False,
                "digest": "sha256:" + "b" * 64,
                "workflow_run": {"id": 42, "head_sha": "a" * 40, "head_branch": "main"}}
        args = ["--arg", "digest", data["digest"], "--arg", "name", data["name"],
                "--arg", "sha", "a" * 40, "--argjson", "run", "42", "--argjson", "artifact", "77"]
        expression = predicate("source-artifact.json")
        self.assertTrue(permits(expression, data, args))
        variants = [{"id": 78}, {"name": "MAGCalc-1.1.0-5-unsigned-aab"}, {"expired": True},
                    {"digest": "sha256:" + "c" * 64},
                    {"workflow_run": {"id": 43, "head_sha": "a" * 40, "head_branch": "main"}},
                    {"workflow_run": {"id": 42, "head_sha": "b" * 40, "head_branch": "main"}},
                    {"workflow_run": {"id": 42, "head_sha": "a" * 40, "head_branch": "feature/release"}}]
        for delta in variants:
            with self.subTest(delta=delta):
                self.assertFalse(permits(expression, {**data, **delta}, args))


if __name__ == "__main__":
    unittest.main()
