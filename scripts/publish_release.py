"""Tag-driven release publication; never called during ordinary local builds."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

from package_release import APPLICATION_ID, ASSET_NAME, PackagingError, validate_versions

REPOSITORY = "West-wise/Server_State_Telemetry_Client"
FILES = (ASSET_NAME, "sstc-update.json", "SHA256SUMS", "packaging-verification.json")


def tag_versions(tag):
    match = re.fullmatch(r"v([0-9]+\.[0-9]+\.[0-9]+)-([1-9][0-9]{0,9})", tag)
    if not match:
        raise PackagingError("INVALID_RELEASE_TAG")
    return validate_versions(match[2], match[1])


def write_versions(env):
    if env.get("GITHUB_EVENT_NAME") == "push" and env.get("GITHUB_REF_TYPE") == "tag":
        code, name = tag_versions(env.get("GITHUB_REF_NAME", ""))
    elif env.get("GITHUB_EVENT_NAME") == "workflow_dispatch" and env.get("GITHUB_REF") == "refs/heads/main":
        code, name = validate_versions(env.get("SSTC_VERSION_CODE", ""), env.get("SSTC_VERSION_NAME", ""))
    else:
        raise PackagingError("INVALID_PACKAGING_TRIGGER")
    with open(env["GITHUB_ENV"], "a", encoding="utf-8") as output:
        output.write(f"SSTC_VERSION_CODE={code}\nSSTC_VERSION_NAME={name}\n")


def validate_package(directory, tag):
    code, name = tag_versions(tag)
    directory = Path(directory)
    if set(path.name for path in directory.iterdir()) != set(FILES):
        raise PackagingError("INVALID_RELEASE_FILES")
    if any(not (directory / name).is_file() or (directory / name).is_symlink() for name in FILES):
        raise PackagingError("INVALID_RELEASE_FILES")
    if not (directory / ASSET_NAME).stat().st_size:
        raise PackagingError("INVALID_RELEASE_FILES")
    sha = hashlib.sha256((directory / ASSET_NAME).read_bytes()).hexdigest()
    metadata = json.loads((directory / "sstc-update.json").read_text(encoding="utf-8"))
    expected = {"schemaVersion": 1, "versionCode": code, "versionName": name,
                "minSdkVersion": 26, "apkAssetName": ASSET_NAME, "sha256": sha}
    if metadata != expected or any(type(metadata[key]) is not int for key in ("schemaVersion", "versionCode", "minSdkVersion")):
        raise PackagingError("RELEASE_METADATA_MISMATCH")
    receipt = json.loads((directory / "packaging-verification.json").read_text(encoding="utf-8"))
    if (not isinstance(receipt, dict) or any(receipt.get(key) != value for key, value in expected.items()) or
            any(type(receipt.get(key)) is not int for key in ("schemaVersion", "versionCode", "minSdkVersion")) or
            receipt.get("applicationId") != APPLICATION_ID or receipt.get("debuggable") is not False or
            any(receipt.get(key) is not True for key in ("signatureVerified", "alignmentVerified", "metadataVerified")) or
            not re.fullmatch(r"[0-9a-f]{64}", receipt.get("signingCertificateSha256", ""))):
        raise PackagingError("RELEASE_VERIFICATION_MISMATCH")
    if (directory / "SHA256SUMS").read_text(encoding="utf-8") != f"{sha}  {ASSET_NAME}\n":
        raise PackagingError("RELEASE_CHECKSUM_MISMATCH")
    return code, name


def publish(directory, env, runner=subprocess.run):
    if (env.get("GITHUB_ACTIONS") != "true" or env.get("GITHUB_EVENT_NAME") != "push" or
            env.get("GITHUB_REF_TYPE") != "tag" or env.get("GITHUB_REPOSITORY") != REPOSITORY):
        raise PackagingError("RELEASE_TRIGGER_REQUIRED")
    tag = env.get("GITHUB_REF_NAME", "")
    directory = Path(directory).resolve()
    code, name = validate_package(directory, tag)

    def gh(*args):
        try:
            return runner(["gh", *map(str, args)], check=True, capture_output=True,
                          text=True, timeout=600).stdout
        except (OSError, subprocess.SubprocessError):
            raise PackagingError("RELEASE_COMMAND_FAILED") from None

    with tempfile.TemporaryDirectory(prefix="sstc-publish-") as temporary:
        temporary = Path(temporary)
        pages = json.loads(gh("api", "--paginate", "--slurp", f"repos/{REPOSITORY}/releases"))
        # Compare metadata as well as tags: older manually created releases may use other tag formats.
        for index, release in enumerate(release for page in pages for release in page):
            if release["draft"] or release["prerelease"]:
                continue
            if not any(asset["name"] == "sstc-update.json" for asset in release["assets"]):
                continue
            previous = temporary / f"previous-{index}"
            previous.mkdir()
            gh("release", "download", release["tag_name"], "--repo", REPOSITORY,
               "--pattern", "sstc-update.json", "--dir", previous)
            metadata = json.loads((previous / "sstc-update.json").read_text(encoding="utf-8"))
            previous_code = metadata.get("versionCode")
            if type(previous_code) is not int or previous_code < 1:
                raise PackagingError("INVALID_PREVIOUS_RELEASE")
            if previous_code >= code:
                raise PackagingError("RELEASE_VERSION_NOT_INCREASING")
        notes = temporary / "notes.md"
        notes.write_text(f"SSTC {name}\n\nAndroid versionCode: {code}\n\n"
                         "Install sstc-release.apk, or use the in-app update prompt.\n", encoding="utf-8")
        gh("release", "create", tag, "--repo", REPOSITORY, "--verify-tag", "--draft",
           "--title", f"SSTC {name}", "--notes-file", notes, *(directory / file for file in FILES))
        downloaded = temporary / "downloaded"
        downloaded.mkdir()
        gh("release", "download", tag, "--repo", REPOSITORY, "--dir", downloaded)
        validate_package(downloaded, tag)
        for file in FILES:
            if (directory / file).read_bytes() != (downloaded / file).read_bytes():
                raise PackagingError("UPLOADED_RELEASE_MISMATCH")
        # The updater excludes drafts: all assets must pass the round-trip check before publication.
        gh("release", "edit", tag, "--repo", REPOSITORY, "--draft=false", "--latest")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("versions", "publish"))
    parser.add_argument("--directory", default="release-package")
    args = parser.parse_args()
    try:
        if args.command == "versions":
            write_versions(os.environ)
        else:
            publish(args.directory, os.environ)
    except PackagingError as error:
        print(str(error), file=sys.stderr)
        return 1
    except (OSError, ValueError, TypeError, KeyError):
        print("RELEASE_VALIDATION_FAILED", file=sys.stderr)
        return 1
    print("RELEASE_STEP_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
