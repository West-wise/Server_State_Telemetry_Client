"""Fail-closed release packaging. Only the sign command receives signing inputs."""
import argparse
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

APPLICATION_ID = "com.SST.server_state_telemetry_client"
SECRET_NAMES = (
    "SSTC_KEYSTORE_BASE64", "SSTC_KEYSTORE_PASSWORD", "SSTC_KEY_ALIAS",
    "SSTC_KEY_PASSWORD", "SSTC_SIGNING_CERT_SHA256",
)
ASSET_NAME = "sstc-release.apk"
BUILD_TOOLS_VERSION = "37.0.0"
APKSIG_JAR_SHA256 = "2defad215d7ff52968a409cde528cdaef7918b115e276b8e3378ca7a178e4180"


class PackagingError(Exception):
    """Only fixed, non-sensitive labels may be exposed to operators."""


class CertificateVerificationError(PackagingError):
    """Diagnostic fields are derived only from fixed classifications and counts."""

    def __init__(self, code, count, matches, output_format):
        super().__init__(code)
        self.diagnostic = {
            "extracted": count > 0,
            "recognized_count": count,
            "matches": matches,
            "format": output_format,
        }


def validate_versions(code, name):
    if not isinstance(code, str) or not re.fullmatch(r"[1-9][0-9]{0,9}", code):
        raise PackagingError("INVALID_VERSION_CODE")
    if not 1 <= int(code) <= 2100000000:
        raise PackagingError("INVALID_VERSION_CODE")
    if (not isinstance(name, str) or not 1 <= len(name) <= 64 or
            not name.strip() or any(not 32 <= ord(c) <= 126 or c in "\"'\\" for c in name)):
        raise PackagingError("INVALID_VERSION_NAME")
    return int(code), name


def validate_signing(env):
    if any(not env.get(key) for key in SECRET_NAMES):
        raise PackagingError("MISSING_SIGNING_INPUT")
    try:
        key = base64.b64decode(env[SECRET_NAMES[0]], validate=True)
    except (ValueError, binascii.Error):
        raise PackagingError("INVALID_KEYSTORE_BASE64") from None
    # JKS magic or DER SEQUENCE (PKCS12). The signer validates the full container.
    if not key or not (key.startswith(bytes.fromhex("feedfeed")) or key[0] == 0x30):
        raise PackagingError("INVALID_KEYSTORE_FORMAT")
    for field in SECRET_NAMES[1:4]:
        if any(c in env[field] for c in "\r\n\x00"):
            raise PackagingError("INVALID_SIGNING_INPUT")
    cert = env[SECRET_NAMES[4]]
    if re.fullmatch(r"(?:[0-9a-fA-F]{2}:){31}[0-9a-fA-F]{2}", cert):
        cert = cert.replace(":", "")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", cert):
        raise PackagingError("INVALID_CERTIFICATE_SHA256")
    return key, cert.lower()


def clean_environment(env):
    return {key: value for key, value in env.items()
            if key not in (*SECRET_NAMES, "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS",
                           "_JAVA_OPTIONS", "CLASSPATH")}


def run_tool(args, label, runner=subprocess.run, env=None):
    try:
        result = runner([str(arg) for arg in args], check=False, capture_output=True,
                        text=True, env=clean_environment(os.environ if env is None else env),
                        timeout=600)
        if result.returncode != 0:
            raise PackagingError(label)
        return result.stdout
    except (OSError, subprocess.SubprocessError, UnicodeError):
        raise PackagingError(label) from None


def find_tools(sdk):
    directory = Path(sdk) / "build-tools" / BUILD_TOOLS_VERSION
    tools = {name: directory / name for name in ("aapt", "zipalign", "apksigner")}
    if any(not p.is_file() or p.is_symlink() or not os.access(p, os.X_OK)
           for p in tools.values()):
        raise PackagingError("MISSING_ANDROID_TOOLS")
    jar = directory / "lib" / "apksigner.jar"
    if not jar.is_file() or jar.is_symlink():
        raise PackagingError("MISSING_CERTIFICATE_VERIFIER")
    if hashlib.sha256(jar.read_bytes()).hexdigest() != APKSIG_JAR_SHA256:
        raise PackagingError("CERTIFICATE_VERIFIER_VERSION_MISMATCH")
    home = os.environ.get("JAVA_HOME")
    if not home:
        raise PackagingError("MISSING_JDK_17")
    release = Path(home) / "release"
    if not release.is_file() or not re.search(r'^JAVA_VERSION="17(?:\.|\")', release.read_text(), re.MULTILINE):
        raise PackagingError("MISSING_JDK_17")
    tools.update(apksig=jar, java=Path(home) / "bin/java", javac=Path(home) / "bin/javac")
    if any(not tools[k].is_file() or not os.access(tools[k], os.X_OK) for k in ("java", "javac")):
        raise PackagingError("MISSING_JDK_17")
    return tools


def inspect_apk(badging, code, name):
    if re.search(r"^application-debuggable(?:[:\s]|$)", badging, re.MULTILINE):
        raise PackagingError("DEBUG_APK_REJECTED")
    package = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='(.*?)'(?= [A-Za-z][A-Za-z0-9]*=|$)",
                        badging, re.MULTILINE)
    sdk = re.search(r"^sdkVersion:'([0-9]+)'$", badging, re.MULTILINE)
    if not package or not sdk:
        raise PackagingError("INVALID_APK_BADGING")
    actual_code, actual_name = validate_versions(package[2], package[3])
    if (package[1] != APPLICATION_ID or actual_code != code or actual_name != name or
            int(sdk[1]) != 26):
        raise PackagingError("APK_IDENTITY_MISMATCH")
    return {"applicationId": package[1], "versionCode": actual_code,
            "versionName": actual_name, "minSdkVersion": int(sdk[1])}


def verify_certificate(output, expected):
    def unique_fields(items):
        value = {}
        for key, item in items:
            if key in value:
                raise ValueError("DUPLICATE_FIELD")
            value[key] = item
        return value
    try:
        if not isinstance(output, str) or len(output) > 2048:
            raise ValueError("INVALID_HELPER_OUTPUT")
        value = json.loads(output, object_pairs_hook=unique_fields)
        if not isinstance(value, dict) or set(value) != {"verified", "signerCount", "sha256", "rotation"}:
            raise ValueError("INVALID_HELPER_OUTPUT")
        count, sha = value["signerCount"], value["sha256"]
        if (type(value["verified"]) is not bool or type(value["rotation"]) is not bool or
                type(count) is not int or not 0 <= count <= 2147483647 or
                (count == 1 and (not isinstance(sha, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", sha))) or
                (count != 1 and sha is not None)):
            raise ValueError("INVALID_HELPER_OUTPUT")
    except (ValueError, TypeError):
        raise CertificateVerificationError("SIGNING_CERTIFICATE_EXTRACTION_FAILED", 0, None,
                                           "UNRECOGNIZED") from None
    if not value["verified"]:
        raise PackagingError("SIGNATURE_VERIFICATION_FAILED")
    if count == 0:
        raise CertificateVerificationError("SIGNING_CERTIFICATE_EXTRACTION_FAILED", count, None,
                                           "SDK_CERTIFICATE")
    if count != 1:
        raise CertificateVerificationError("SIGNING_CERTIFICATE_MULTIPLE_DIGESTS", count, None,
                                           "SDK_CERTIFICATE")
    if value["rotation"]:
        raise CertificateVerificationError("SIGNING_CERTIFICATE_ROTATION_UNSUPPORTED", count, None,
                                           "SDK_CERTIFICATE")
    if sha.lower() != expected:
        raise CertificateVerificationError("SIGNING_CERTIFICATE_MISMATCH", count, False,
                                           "SDK_CERTIFICATE")


def certificate_output(signed, tools, private, runner, env):
    helper = Path(__file__).resolve().with_name("VerifyApkCertificate.java")
    if not helper.is_file() or helper.is_symlink():
        raise PackagingError("MISSING_CERTIFICATE_HELPER")
    classes = private / "certificate-helper"
    classes.mkdir(mode=0o700)
    run_tool([tools["javac"], "-encoding", "UTF-8", "--release", "17", "-cp", tools["apksig"],
              "-d", classes, helper], "CERTIFICATE_HELPER_COMPILATION_FAILED", runner, env)
    if not (classes / "VerifyApkCertificate.class").is_file():
        raise PackagingError("CERTIFICATE_HELPER_COMPILATION_FAILED")
    return run_tool([tools["java"], "-cp", str(classes) + os.pathsep + str(tools["apksig"]),
                     "VerifyApkCertificate", signed], "CERTIFICATE_HELPER_FAILED", runner, env)


def make_metadata(identity, apk):
    return {"schemaVersion": 1, "versionCode": identity["versionCode"],
            "versionName": identity["versionName"], "minSdkVersion": identity["minSdkVersion"],
            "apkAssetName": apk.name, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest()}


def validate_metadata(metadata, identity, apk):
    if metadata != make_metadata(identity, apk):
        raise PackagingError("METADATA_MISMATCH")
    if any(type(metadata[k]) is not int for k in ("schemaVersion", "versionCode", "minSdkVersion")):
        raise PackagingError("METADATA_MISMATCH")


def build_unsigned(code, name, runner=subprocess.run):
    validate_versions(code, name)
    run_tool(["bash", "./gradlew", "testDebugUnitTest", "assembleDebug"],
             "DEBUG_TEST_BUILD_FAILED", runner)
    run_tool(["bash", "./gradlew", "--no-daemon", "assembleRelease",
              "-PsstcVersionCode=" + code, "-PsstcVersionName=" + name],
             "UNSIGNED_RELEASE_BUILD_FAILED", runner)


def package_release(unsigned, output, code, name, tools, env, runner=subprocess.run):
    version_code, version_name = validate_versions(code, name)
    key, cert = validate_signing(env)
    unsigned, output = Path(unsigned), Path(output)
    if not unsigned.is_file() or unsigned.is_symlink():
        raise PackagingError("MISSING_UNSIGNED_APK")
    if output.exists():
        raise PackagingError("OUTPUT_ALREADY_EXISTS")
    if any(k not in tools or not Path(tools[k]).is_file()
           for k in ("aapt", "zipalign", "apksigner", "java", "javac", "apksig")):
        raise PackagingError("MISSING_ANDROID_TOOLS")
    # Nothing is published until every check passes. TemporaryDirectory cleans all
    # private material and partial outputs even when a tool or validation fails.
    with tempfile.TemporaryDirectory(prefix="sstc-sign-") as private:
        private = Path(private)
        keystore = private / "signing.keystore"
        keystore.write_bytes(key)
        keystore.chmod(0o600)
        passwords = []
        for index, field in enumerate(("SSTC_KEYSTORE_PASSWORD", "SSTC_KEY_PASSWORD")):
            path = private / ("password" + str(index))
            path.write_text(env[field], encoding="utf-8")
            path.chmod(0o600)
            passwords.append(path)
        aligned = private / "aligned.apk"
        signed = private / ASSET_NAME
        run_tool([tools["zipalign"], "-P", "16", "-f", "4", unsigned, aligned],
                 "ZIPALIGN_FAILED", runner, env)
        run_tool([tools["apksigner"], "sign", "--ks", keystore,
                  "--ks-key-alias", env["SSTC_KEY_ALIAS"], "--ks-pass", "file:" + str(passwords[0]),
                  "--key-pass", "file:" + str(passwords[1]), "--out", signed, aligned],
                 "APK_SIGNING_FAILED", runner, env)
        run_tool([tools["zipalign"], "-c", "-P", "16", "4", signed],
                 "ALIGNMENT_VERIFICATION_FAILED", runner, env)
        run_tool([tools["apksigner"], "verify", "--verbose", signed],
                 "SIGNATURE_VERIFICATION_FAILED", runner, env)
        verify_certificate(certificate_output(signed, tools, private, runner, env), cert)
        badging = run_tool([tools["aapt"], "dump", "badging", signed],
                           "APK_INSPECTION_FAILED", runner, env)
        identity = inspect_apk(badging, version_code, version_name)
        metadata = make_metadata(identity, signed)
        validate_metadata(metadata, identity, signed)
        staged = private / "artifact"
        staged.mkdir()
        (staged / ASSET_NAME).write_bytes(signed.read_bytes())
        (staged / "sstc-update.json").write_text(json.dumps(metadata, ensure_ascii=True, indent=2) + "\n")
        validate_metadata(json.loads((staged / "sstc-update.json").read_text()), identity, staged / ASSET_NAME)
        (staged / "SHA256SUMS").write_text(metadata["sha256"] + "  " + ASSET_NAME + "\n")
        receipt = {"schemaVersion": 1, **identity, "apkAssetName": ASSET_NAME,
                   "sha256": metadata["sha256"], "signingCertificateSha256": cert,
                   "signatureVerified": True, "alignmentVerified": True,
                   "debuggable": False, "metadataVerified": True,
                   "releasePublished": False, "deviceUpdateVerified": False}
        (staged / "packaging-verification.json").write_text(json.dumps(receipt, indent=2) + "\n")
        # Exclusive destination creation prevents overwriting operator files.
        output.mkdir(parents=True)
        try:
            for path in staged.iterdir():
                (output / path.name).write_bytes(path.read_bytes())
        except OSError:
            for path in output.iterdir():
                path.unlink()
            output.rmdir()
            raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("validate", "build", "sign"))
    parser.add_argument("--unsigned", default="app/build/outputs/apk/release/app-release-unsigned.apk")
    parser.add_argument("--output", default="release-package")
    args = parser.parse_args()
    try:
        code = os.environ.get("SSTC_VERSION_CODE", "")
        name = os.environ.get("SSTC_VERSION_NAME", "")
        validate_versions(code, name)
        if args.command == "build":
            build_unsigned(code, name)
        elif args.command == "sign":
            sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
            if not sdk:
                raise PackagingError("MISSING_ANDROID_SDK")
            package_release(args.unsigned, args.output, code, name, find_tools(sdk), os.environ)
    except PackagingError as error:
        print(str(error), file=sys.stderr)
        if isinstance(error, CertificateVerificationError):
            print("SIGNING_CERTIFICATE_DIAGNOSTIC " + json.dumps(error.diagnostic),
                  file=sys.stderr)
        return 1
    except Exception:
        print("PACKAGING_FAILED", file=sys.stderr)
        return 1
    print("PACKAGING_STEP_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
