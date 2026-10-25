"""Publication tests fake GitHub CLI responses; they never publish a release."""
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
try:
    spec = importlib.util.spec_from_file_location("publish", SCRIPTS / "publish_release.py")
    p = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(p)
finally:
    sys.path.pop(0)


class PublishReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.package = self.root / "package"
        self.package.mkdir()
        (self.package / p.ASSET_NAME).write_bytes(b"synthetic signed apk")
        sha = hashlib.sha256((self.package / p.ASSET_NAME).read_bytes()).hexdigest()
        self.metadata = {"schemaVersion": 1, "versionCode": 2, "versionName": "1.1.0",
                         "minSdkVersion": 26, "apkAssetName": p.ASSET_NAME, "sha256": sha}
        self.receipt = {**self.metadata, "applicationId": p.APPLICATION_ID,
                        "signingCertificateSha256": "ab" * 32, "debuggable": False,
                        "signatureVerified": True, "alignmentVerified": True, "metadataVerified": True}
        (self.package / "sstc-update.json").write_text(json.dumps(self.metadata))
        (self.package / "packaging-verification.json").write_text(json.dumps(self.receipt))
        (self.package / "SHA256SUMS").write_text(f"{sha}  {p.ASSET_NAME}\n")
        self.env = {"GITHUB_ACTIONS": "true", "GITHUB_EVENT_NAME": "push", "GITHUB_REF_TYPE": "tag",
                    "GITHUB_REF_NAME": "v1.1.0-2", "GITHUB_REPOSITORY": p.REPOSITORY}
        self.calls = []
        self.previous_code = 1
        self.fail_upload = False
        self.corrupt_download = False

    def runner(self, args, **kwargs):
        self.assertTrue(kwargs["check"])
        self.assertTrue(kwargs["capture_output"])
        self.assertNotIn("shell", kwargs)
        self.calls.append(args)
        if args[1] == "api":
            body = [[{"tag_name": "old-tag", "draft": False, "prerelease": False,
                      "assets": [{"name": "sstc-update.json"}]}]]
            return subprocess.CompletedProcess(args, 0, json.dumps(body), "")
        if args[1:3] == ["release", "create"] and self.fail_upload:
            raise subprocess.CalledProcessError(1, args, stderr="synthetic private error")
        if args[1:3] == ["release", "download"]:
            destination = Path(args[args.index("--dir") + 1])
            if args[3] == "old-tag":
                (destination / "sstc-update.json").write_text(json.dumps({"versionCode": self.previous_code}))
            else:
                for file in p.FILES:
                    shutil.copyfile(self.package / file, destination / file)
                if self.corrupt_download:
                    (destination / p.ASSET_NAME).write_bytes(b"corrupt")
        return subprocess.CompletedProcess(args, 0, "", "")

    def test_tag_versions_and_rejects_ambiguous_or_unsafe_tags(self):
        self.assertEqual(p.tag_versions("v1.1.0-2"), (2, "1.1.0"))
        for tag in ("v1.1.0", "v1.1-2", "v1.1.0-0", "v1.1.0-02", "v1.1.0-2100000001",
                    "v1.1.0-2\n", "v1.1.0-beta-2", "v$(id)-2"):
            with self.subTest(tag=tag), self.assertRaises(p.PackagingError):
                p.tag_versions(tag)

    def test_resolves_tag_and_manual_packaging_versions_without_publication(self):
        output = self.root / "environment"
        p.write_versions({**self.env, "GITHUB_ENV": str(output)})
        self.assertEqual(output.read_text(), "SSTC_VERSION_CODE=2\nSSTC_VERSION_NAME=1.1.0\n")
        p.write_versions({"GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_REF": "refs/heads/main",
                          "GITHUB_ENV": str(output), "SSTC_VERSION_CODE": "3", "SSTC_VERSION_NAME": "1.2.0"})
        self.assertIn("SSTC_VERSION_CODE=3\n", output.read_text())
        self.assertEqual(self.calls, [])

    def test_rejects_untrusted_triggers_before_any_github_call(self):
        for key, value in (("GITHUB_ACTIONS", "false"), ("GITHUB_EVENT_NAME", "workflow_dispatch"),
                           ("GITHUB_REF_TYPE", "branch"), ("GITHUB_REPOSITORY", "other/repo")):
            with self.subTest(key=key), self.assertRaises(p.PackagingError):
                p.publish(self.package, {**self.env, key: value}, self.runner)
        self.assertEqual(self.calls, [])

    def test_rejects_corrupt_local_apk_before_any_github_call(self):
        (self.package / p.ASSET_NAME).write_bytes(b"corrupt")
        with self.assertRaisesRegex(p.PackagingError, "METADATA_MISMATCH"):
            p.publish(self.package, self.env, self.runner)
        self.assertEqual(self.calls, [])

    def test_rejects_bad_receipt_and_additional_files(self):
        for key, value in (("signatureVerified", False), ("alignmentVerified", 1), ("debuggable", True),
                           ("applicationId", "wrong.app"), ("versionCode", 3), ("signingCertificateSha256", "bad")):
            (self.package / "packaging-verification.json").write_text(json.dumps({**self.receipt, key: value}))
            with self.subTest(key=key), self.assertRaises(p.PackagingError):
                p.validate_package(self.package, self.env["GITHUB_REF_NAME"])
        (self.package / "unexpected").write_text("extra")
        with self.assertRaisesRegex(p.PackagingError, "INVALID_RELEASE_FILES"):
            p.validate_package(self.package, self.env["GITHUB_REF_NAME"])

    def test_publishes_only_after_draft_upload_and_roundtrip_verification(self):
        p.publish(self.package, self.env, self.runner)
        self.assertEqual([args[1:3] for args in self.calls], [
            ["api", "--paginate"], ["release", "download"], ["release", "create"],
            ["release", "download"], ["release", "edit"]])
        self.assertIn("--draft", self.calls[2])
        self.assertIn("--verify-tag", self.calls[2])
        self.assertIn("--draft=false", self.calls[-1])

    def test_rejects_nonincreasing_version_before_draft_creation(self):
        for code in (2, 3):
            self.previous_code = code
            with self.subTest(code=code), self.assertRaisesRegex(p.PackagingError, "NOT_INCREASING"):
                p.publish(self.package, self.env, self.runner)
        self.assertFalse(any(args[1:3] == ["release", "create"] for args in self.calls))

    def test_failed_upload_or_tampered_roundtrip_never_publishes(self):
        self.fail_upload = True
        with self.assertRaisesRegex(p.PackagingError, "COMMAND_FAILED"):
            p.publish(self.package, self.env, self.runner)
        self.fail_upload = False
        self.corrupt_download = True
        with self.assertRaisesRegex(p.PackagingError, "METADATA_MISMATCH"):
            p.publish(self.package, self.env, self.runner)
        self.assertFalse(any(args[1:3] == ["release", "edit"] for args in self.calls))


if __name__ == "__main__":
    unittest.main()
