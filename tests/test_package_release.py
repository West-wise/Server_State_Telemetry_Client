"""Offline tests use fake SDK tools and synthetic credentials; no signing occurs."""
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("packaging", Path(__file__).resolve().parents[1] / "scripts/package_release.py")
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)

CERT = "ab" * 32
BADGING = ("package: name='" + p.APPLICATION_ID + "' versionCode='2' "
           "versionName='1.1' platformBuildVersionName='15'\nsdkVersion:'26'\n")


class PackagingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.unsigned = self.root / "unsigned.apk"
        self.unsigned.write_bytes(b"unsigned fixture")
        self.output = self.root / "result"
        self.tools = {}
        for name in ("aapt", "zipalign", "apksigner"):
            tool = self.root / name
            tool.write_text("fake")
            tool.chmod(0o700)
            self.tools[name] = tool
        self.env = dict(zip(p.SECRET_NAMES, (
            base64.b64encode(bytes.fromhex("feedfeed") + b"synthetic").decode(),
            "synthetic-store-password", "synthetic-alias", "synthetic-key-password", CERT)))
        self.calls = []
        self.private_paths = []
        self.failure = None
        self.badging = BADGING
        self.certs = "Signer #1 certificate SHA-256 digest: " + CERT + "\n"

    def runner(self, args, **kwargs):
        self.calls.append(args)
        self.assertTrue(kwargs["capture_output"])
        self.assertTrue(kwargs["text"])
        self.assertFalse(any(key in kwargs["env"] for key in p.SECRET_NAMES))
        self.assertNotIn(self.env["SSTC_KEYSTORE_PASSWORD"], args)
        self.assertNotIn(self.env["SSTC_KEY_PASSWORD"], args)
        operation = Path(args[0]).name + (" " + args[1] if len(args) > 1 else "")
        if Path(args[0]).name == "zipalign" and "-f" in args:
            self.private_paths.append(Path(args[-1]).parent)
        if self.failure == operation:
            return subprocess.CompletedProcess(args, 1, "sensitive output", "sensitive error")
        if Path(args[0]).name == "zipalign" and "-f" in args:
            Path(args[-1]).write_bytes(b"aligned fixture")
        if Path(args[0]).name == "apksigner" and args[1] == "sign":
            store = Path(args[args.index("--ks") + 1])
            self.private_paths.append(store.parent)
            self.assertEqual(store.stat().st_mode & 0o777, 0o600)
            self.assertEqual(store.parent.stat().st_mode & 0o777, 0o700)
            for option in ("--ks-pass", "--key-pass"):
                value = args[args.index(option) + 1]
                self.assertTrue(value.startswith("file:"))
                password = Path(value[5:])
                self.assertEqual(password.stat().st_mode & 0o777, 0o600)
            Path(args[args.index("--out") + 1]).write_bytes(b"signed fixture")
        stdout = self.badging if Path(args[0]).name == "aapt" else self.certs
        return subprocess.CompletedProcess(args, 0, stdout, "")

    def package(self):
        p.package_release(self.unsigned, self.output, "2", "1.1", self.tools, self.env, self.runner)

    def test_versions_and_bounds(self):
        self.assertEqual(p.validate_versions("2100000000", "v 1.0"), (2100000000, "v 1.0"))
        for code in ("", "0", "01", "-1", "1.0", " 2", "2100000001", "9" * 100, "２"):
            with self.subTest(code=code), self.assertRaises(p.PackagingError):
                p.validate_versions(code, "v")
        for name in ("", " ", "a" * 65, "x\n", "x\t", "한글", "\x7f", 'v"1', "v'1", "v\\1"):
            with self.subTest(name=name), self.assertRaises(p.PackagingError):
                p.validate_versions("1", name)

    def test_valid_version_boundaries_and_printable_names(self):
        for code in ("1", "2100000000"):
            for name in ("a", "a" * 64, " v 1 ", "v-$()&;=!"):
                with self.subTest(code=code, name=name):
                    self.assertEqual(p.validate_versions(code, name), (int(code), name))
                    badging = BADGING.replace("versionCode='2'", "versionCode='" + code + "'")
                    badging = badging.replace("versionName='1.1'", "versionName='" + name + "'")
                    self.assertEqual(p.inspect_apk(badging, int(code), name)["versionName"], name)

    def test_signing_missing_inputs_never_runs_tools(self):
        for field in p.SECRET_NAMES:
            for value in (None, ""):
                env = self.env.copy()
                if value is None:
                    env.pop(field)
                else:
                    env[field] = value
                with self.subTest(field=field, value=value):
                    with self.assertRaisesRegex(p.PackagingError, "MISSING_SIGNING_INPUT"):
                        p.package_release(self.unsigned, self.output, "2", "1.1",
                                          self.tools, env, self.runner)
                    self.assertFalse(self.calls)
                    self.assertFalse(self.output.exists())

    def test_signed_apk_tool_sequence_and_targets(self):
        self.package()
        self.assertEqual([(Path(args[0]).name, args[1]) for args in self.calls],
                         [("zipalign", "-P"), ("apksigner", "sign"),
                          ("zipalign", "-c"), ("apksigner", "verify"), ("aapt", "dump")])
        signed = self.calls[1][self.calls[1].index("--out") + 1]
        self.assertEqual(self.calls[1][-1], self.calls[0][-1])
        for args in self.calls[2:]:
            self.assertEqual(args[-1], signed)

    def test_missing_and_malformed_signing(self):
        for field in p.SECRET_NAMES:
            env = self.env.copy()
            env.pop(field)
            with self.subTest(field=field), self.assertRaisesRegex(p.PackagingError, "MISSING_SIGNING_INPUT"):
                p.validate_signing(env)
        for field, value in (("SSTC_KEYSTORE_BASE64", "%%%"),
                             ("SSTC_KEYSTORE_BASE64", base64.b64encode(b"junk").decode()),
                             ("SSTC_KEYSTORE_PASSWORD", "a\nsecret"),
                             ("SSTC_KEY_PASSWORD", "a\x00secret"),
                             ("SSTC_KEY_ALIAS", "a\rsecret"),
                             ("SSTC_SIGNING_CERT_SHA256", "xx" * 32)):
            with self.subTest(field=field), self.assertRaises(p.PackagingError):
                p.validate_signing({**self.env, field: value})
        env = {**self.env, "SSTC_SIGNING_CERT_SHA256": ":".join(["AB"] * 32)}
        self.assertEqual(p.validate_signing(env)[1], CERT)

    def test_success_exact_artifact_sha_certificate_and_cleanup(self):
        self.package()
        self.assertEqual({f.name for f in self.output.iterdir()},
                         {p.ASSET_NAME, "sstc-update.json", "SHA256SUMS", "packaging-verification.json"})
        metadata = json.loads((self.output / "sstc-update.json").read_text())
        digest = hashlib.sha256(b"signed fixture").hexdigest()
        self.assertEqual(metadata, {"schemaVersion": 1, "versionCode": 2, "versionName": "1.1",
                                   "minSdkVersion": 26, "apkAssetName": p.ASSET_NAME, "sha256": digest})
        self.assertEqual((self.output / "SHA256SUMS").read_text(), digest + "  " + p.ASSET_NAME + "\n")
        receipt = json.loads((self.output / "packaging-verification.json").read_text())
        self.assertTrue(receipt["signatureVerified"])
        self.assertEqual(receipt["signingCertificateSha256"], CERT)
        self.assertFalse(receipt["deviceUpdateVerified"])
        self.assertTrue(all(not path.exists() for path in self.private_paths))
        self.assertTrue(any(args[1:4] == ["verify", "--verbose", "--print-certs"] for args in self.calls))

    def test_each_tool_failure_closes_and_cleans(self):
        for operation in ("zipalign -P", "apksigner sign", "zipalign -c", "apksigner verify", "aapt dump"):
            self.failure = operation
            with self.subTest(operation=operation), self.assertRaises(p.PackagingError) as caught:
                self.package()
            self.assertNotIn("sensitive", str(caught.exception))
            self.assertFalse(self.output.exists())
            self.assertTrue(all(not path.exists() for path in self.private_paths))

    def test_identity_debug_and_badging_fail_closed(self):
        for badging in (BADGING + "application-debuggable\n",
                        BADGING.replace(p.APPLICATION_ID, "other.app"),
                        BADGING.replace("versionCode='2'", "versionCode='3'"),
                        BADGING.replace("versionName='1.1'", "versionName='wrong'"),
                        BADGING.replace("sdkVersion:'26'", "sdkVersion:'27'"), ""):
            self.badging = badging
            with self.subTest(badging=badging), self.assertRaises(p.PackagingError):
                self.package()
            self.assertFalse(self.output.exists())
            self.assertTrue(all(not path.exists() for path in self.private_paths))

    def test_certificate_mismatch_missing_or_multiple(self):
        for certs in ("", self.certs.replace(CERT, "cd" * 32), self.certs + self.certs):
            self.certs = certs
            with self.subTest(certs=certs), self.assertRaisesRegex(p.PackagingError, "SIGNING_CERTIFICATE_MISMATCH"):
                self.package()
            self.assertFalse(self.output.exists())
            self.assertTrue(all(not path.exists() for path in self.private_paths))

    def test_metadata_mismatch_and_apk_tampering(self):
        identity = p.inspect_apk(BADGING, 2, "1.1")
        metadata = p.make_metadata(identity, self.unsigned)
        for field in metadata:
            wrong = {**metadata, field: None}
            with self.subTest(field=field), self.assertRaisesRegex(p.PackagingError, "METADATA_MISMATCH"):
                p.validate_metadata(wrong, identity, self.unsigned)
        self.unsigned.write_bytes(b"changed")
        with self.assertRaisesRegex(p.PackagingError, "METADATA_MISMATCH"):
            p.validate_metadata(metadata, identity, self.unsigned)

    def test_metadata_failure_leaves_no_artifact(self):
        with patch.object(p, "validate_metadata", side_effect=p.PackagingError("METADATA_MISMATCH")):
            with self.assertRaises(p.PackagingError):
                self.package()
        self.assertFalse(self.output.exists())
        self.assertTrue(all(not path.exists() for path in self.private_paths))

    def test_missing_tools_and_subprocess_errors(self):
        for error in (FileNotFoundError(), subprocess.TimeoutExpired("tool", 1)):
            def missing(*args, **kwargs):
                raise error
            with self.assertRaisesRegex(p.PackagingError, "TOOL_FAILED"):
                p.run_tool(["absent"], "TOOL_FAILED", missing, self.env)
        self.tools["aapt"].unlink()
        with self.assertRaisesRegex(p.PackagingError, "MISSING_ANDROID_TOOLS"):
            self.package()
        with self.assertRaisesRegex(p.PackagingError, "MISSING_ANDROID_TOOLS"):
            p.find_tools(self.root)

    def test_build_order_and_failure_prevents_release(self):
        with patch.dict(os.environ, self.env):
            p.build_unsigned("2", "1.1", self.runner)
        self.assertEqual(self.calls[0], ["bash", "./gradlew", "testDebugUnitTest", "assembleDebug"])
        self.assertIn("assembleRelease", self.calls[1])
        self.assertIn("-PsstcVersionName=1.1", self.calls[1])
        self.calls.clear()
        self.failure = "bash ./gradlew"
        with self.assertRaisesRegex(p.PackagingError, "DEBUG_TEST_BUILD_FAILED"):
            p.build_unsigned("2", "1.1", self.runner)
        self.assertEqual(len(self.calls), 1)

    def test_release_build_failure(self):
        def fail_release(args, **kwargs):
            result = self.runner(args, **kwargs)
            if "assembleRelease" in args:
                return subprocess.CompletedProcess(args, 1, "private detail", "")
            return result
        with self.assertRaisesRegex(p.PackagingError, "UNSIGNED_RELEASE_BUILD_FAILED"):
            p.build_unsigned("2", "1.1", fail_release)
        self.assertEqual(len(self.calls), 2)
        self.assertFalse(self.output.exists())

    def test_missing_unsigned_apk(self):
        self.unsigned.unlink()
        with self.assertRaisesRegex(p.PackagingError, "MISSING_UNSIGNED_APK"):
            self.package()
        self.assertFalse(self.calls)
        self.assertFalse(self.output.exists())

    def test_partial_publication_failure_cleanup(self):
        original = Path.write_bytes
        def fail_publication(path, data):
            if path.parent == self.output and path.name == "sstc-update.json":
                raise OSError("synthetic disk failure")
            return original(path, data)
        with patch.object(Path, "write_bytes", fail_publication):
            with self.assertRaises(OSError):
                self.package()
        self.assertFalse(self.output.exists())
        self.assertTrue(all(not path.exists() for path in self.private_paths))

    def test_existing_output_is_preserved(self):
        self.output.mkdir()
        marker = self.output / "operator-file"
        marker.write_text("preserve")
        with self.assertRaisesRegex(p.PackagingError, "OUTPUT_ALREADY_EXISTS"):
            self.package()
        self.assertEqual(marker.read_text(), "preserve")


if __name__ == "__main__":
    unittest.main()
