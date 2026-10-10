"""Offline tests use fake SDK tools and synthetic credentials; no signing occurs."""
import base64
import hashlib
import importlib.util
import io
from contextlib import redirect_stderr, redirect_stdout
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
        for name in ("aapt", "zipalign", "apksigner", "java", "javac", "apksig"):
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
        self.certs = json.dumps({"verified": True, "signerCount": 1, "sha256": CERT, "rotation": False})

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
        if Path(args[0]).name == "javac":
            classes = Path(args[args.index("-d") + 1])
            self.private_paths.append(classes.parent)
            (classes / "VerifyApkCertificate.class").write_bytes(b"synthetic class")
        stdout = self.badging if Path(args[0]).name == "aapt" else (
            self.certs if Path(args[0]).name == "java" else "unparsed signer output")
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
                          ("zipalign", "-c"), ("apksigner", "verify"),
                          ("javac", "-encoding"), ("java", "-cp"), ("aapt", "dump")])
        signed = self.calls[1][self.calls[1].index("--out") + 1]
        self.assertEqual(self.calls[1][-1], self.calls[0][-1])
        for args in self.calls[2:]:
            if Path(args[0]).name != "javac":
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
        self.assertTrue(any(args[1:3] == ["verify", "--verbose"] for args in self.calls))

    def test_each_tool_failure_closes_and_cleans(self):
        for operation in ("zipalign -P", "apksigner sign", "zipalign -c", "apksigner verify", "javac -encoding", "java -cp", "aapt dump"):
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
        for certs, code in (
                ("", "SIGNING_CERTIFICATE_EXTRACTION_FAILED"),
                (self.certs.replace(CERT, "cd" * 32), "SIGNING_CERTIFICATE_MISMATCH"),
                (json.dumps({"verified": True, "signerCount": 2, "sha256": None, "rotation": False}),
                 "SIGNING_CERTIFICATE_MULTIPLE_DIGESTS")):
            self.certs = certs
            with self.subTest(code=code), self.assertRaisesRegex(p.PackagingError, code):
                self.package()
            self.assertFalse(self.output.exists())
            self.assertTrue(all(not path.exists() for path in self.private_paths))

    def test_command_certificate_diagnostics_are_safe_and_fail_closed(self):
        sensitive = "subject=CN=synthetic-private-subject alias=synthetic-alias /private/synthetic-path"
        cases = (
            ("", "EXTRACTION_FAILED", 0, None, "UNRECOGNIZED"),
            (sensitive, "EXTRACTION_FAILED", 0, None, "UNRECOGNIZED"),
            ("Signer #1 certificate SHA-256 digest: " + CERT, "EXTRACTION_FAILED", 0, None, "UNRECOGNIZED"),
            (self.certs + sensitive, "EXTRACTION_FAILED", 0, None, "UNRECOGNIZED"),
            (json.dumps({"verified": True, "signerCount": 0, "sha256": None, "rotation": False}),
             "EXTRACTION_FAILED", 0, None, "SDK_CERTIFICATE"),
            (json.dumps({"verified": True, "signerCount": 2, "sha256": None, "rotation": False}),
             "MULTIPLE_DIGESTS", 2, None, "SDK_CERTIFICATE"),
            (self.certs.replace(CERT, "cd" * 32), "MISMATCH", 1, False, "SDK_CERTIFICATE"),
            (self.certs.replace('"rotation": false', '"rotation": true'),
             "ROTATION_UNSUPPORTED", 1, None, "SDK_CERTIFICATE"),
        )
        original_package = p.package_release
        def fake_package(unsigned, output, code, name, tools, env):
            return original_package(unsigned, output, code, name, tools, env, self.runner)
        env = {**self.env, "SSTC_VERSION_CODE": "2", "SSTC_VERSION_NAME": "1.1", "ANDROID_SDK_ROOT": str(self.root)}
        argv = ["package_release.py", "sign", "--unsigned", str(self.unsigned), "--output", str(self.output)]
        for raw, suffix, count, matches, label in cases:
            self.certs = raw
            stdout, stderr = io.StringIO(), io.StringIO()
            with self.subTest(code=suffix), patch.dict(os.environ, env, clear=True), \
                    patch.object(p.sys, "argv", argv), patch.object(p, "find_tools", return_value=self.tools), \
                    patch.object(p, "package_release", side_effect=fake_package), \
                    redirect_stdout(stdout), redirect_stderr(stderr):
                self.assertEqual(p.main(), 1)
                self.assertEqual(stdout.getvalue(), "")
                if raw:
                    self.assertNotIn(raw, stderr.getvalue())
                lines = stderr.getvalue().splitlines()
                self.assertEqual(lines[0], "SIGNING_CERTIFICATE_" + suffix)
                self.assertEqual(len(lines), 2)
                self.assertEqual(json.loads(lines[1][len("SIGNING_CERTIFICATE_DIAGNOSTIC "):]), {
                    "extracted": count > 0, "recognized_count": count, "matches": matches, "format": label})
                for value in (*self.env.values(), "cd" * 32, "synthetic-private-subject", "/private/synthetic-path",
                              str(self.root), *(str(path) for path in self.private_paths)):
                    self.assertNotIn(value, stderr.getvalue())
                self.assertFalse(self.output.exists())
                self.assertTrue(all(not path.exists() for path in self.private_paths))
                self.assertFalse(any(Path(args[0]).name == "aapt" for args in self.calls))

    def test_helper_contract_rejects_duplicate_fields_wrong_types_and_unverified(self):
        valid = {"verified": True, "signerCount": 1, "sha256": CERT, "rotation": False}
        invalid = ["[]", "null", json.dumps(valid) + json.dumps(valid), "x" * 2049,
                   json.dumps({**valid, "verified": 1}), json.dumps({**valid, "signerCount": True}),
                   json.dumps({**valid, "signerCount": -1}), json.dumps({**valid, "rotation": None}),
                   json.dumps({**valid, "sha256": "xx" * 32}), json.dumps({**valid, "extra": "secret"}),
                   json.dumps(valid).replace('"verified": true', '"verified": true, "verified": false')]
        for raw in invalid:
            with self.subTest(raw=raw), self.assertRaisesRegex(p.PackagingError, "EXTRACTION_FAILED"):
                p.verify_certificate(raw, CERT)
        with self.assertRaisesRegex(p.PackagingError, "SIGNATURE_VERIFICATION_FAILED"):
            p.verify_certificate(json.dumps({"verified": False, "signerCount": 0, "sha256": None, "rotation": False}), CERT)

    def test_pinned_sdk_jar_and_jdk_fail_without_fallback(self):
        sdk = self.root / "sdk"
        version = sdk / "build-tools" / p.BUILD_TOOLS_VERSION
        version.mkdir(parents=True)
        for name in ("aapt", "zipalign", "apksigner"):
            (version / name).write_text("synthetic tool")
            (version / name).chmod(0o700)
        jar = version / "lib/apksigner.jar"
        jar.parent.mkdir()
        jar.write_bytes(b"synthetic jar")
        jdk = self.root / "jdk"
        (jdk / "bin").mkdir(parents=True)
        for name in ("java", "javac"):
            (jdk / "bin" / name).write_text("synthetic executable")
            (jdk / "bin" / name).chmod(0o700)
        (jdk / "release").write_text('JAVA_VERSION="17.0.20.1"\n')
        digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        with patch.dict(os.environ, {"JAVA_HOME": str(jdk)}), patch.object(p, "APKSIG_JAR_SHA256", digest):
            self.assertEqual(p.find_tools(sdk)["apksig"], jar)
            (jdk / "release").write_text('JAVA_VERSION="21.0.1"\n')
            with self.assertRaisesRegex(p.PackagingError, "MISSING_JDK_17"):
                p.find_tools(sdk)
            (jdk / "release").write_text('JAVA_VERSION="17.0.20.1"\n')
            jar.write_bytes(b"changed jar")
            with self.assertRaisesRegex(p.PackagingError, "VERIFIER_VERSION_MISMATCH"):
                p.find_tools(sdk)
            jar.unlink()
            with self.assertRaisesRegex(p.PackagingError, "MISSING_CERTIFICATE_VERIFIER"):
                p.find_tools(sdk)
        (version / "aapt").unlink()
        with self.assertRaisesRegex(p.PackagingError, "MISSING_ANDROID_TOOLS"):
            p.find_tools(sdk)

    def test_java_startup_options_are_removed_from_child_environment(self):
        env = {**self.env, "JAVA_TOOL_OPTIONS": "synthetic private argument", "JDK_JAVA_OPTIONS": "synthetic",
               "_JAVA_OPTIONS": "synthetic", "CLASSPATH": "synthetic"}
        cleaned = p.clean_environment(env)
        self.assertFalse(any(k in cleaned for k in (*p.SECRET_NAMES, "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH")))

    def test_normalized_expected_der_certificate_is_accepted(self):
        # Normalization stays in validate_signing; uppercase tool hex is accepted.
        self.env["SSTC_SIGNING_CERT_SHA256"] = ":".join(["AB"] * 32)
        self.certs = self.certs.replace(CERT, CERT.upper())
        self.package()
        self.assertTrue((self.output / p.ASSET_NAME).is_file())
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
