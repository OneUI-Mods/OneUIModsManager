#!/usr/bin/env python3
"""Build and verify a signed release; private signing material never enters Git or output."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent.parent
CERTIFICATE = "f37313a1ddbe006257653b4772e9a2f43f0ddd5c0bcbf6dfe0f71c2fbd9aad73"


def run(*args, capture=False):
    return subprocess.run(
        [str(a) for a in args], cwd=ROOT, check=True, text=True, capture_output=capture
    )


def validate(apk, tools):
    badging = run(tools / "aapt2", "dump", "badging", apk, capture=True).stdout
    if (
        "name='dev.ryuya.oneuimods'" not in badging
        or "targetSdkVersion:'37'" not in badging
    ):
        raise RuntimeError("Unexpected package or SDK")
    if "application-debuggable" in badging:
        raise RuntimeError("Debuggable APK")
    manifest = run(
        tools / "aapt2", "dump", "xmltree", apk, "--file", "AndroidManifest.xml", capture=True
    ).stdout
    if any(
        word in manifest.lower()
        for word in ["testactivity", "probeactivity", "fixtureactivity", "instrumentation"]
    ):
        raise RuntimeError("Test component in release")
    with zipfile.ZipFile(apk) as archive:
        names = set(archive.namelist())
        if any(name.startswith("META-INF/xposed/") for name in names):
            raise RuntimeError("Manager must not contain Xposed entries")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-ref", default="HEAD")
    parser.add_argument("--check-release-apk", type=Path)
    args = parser.parse_args()
    tools = (
        Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
        / "build-tools/37.0.0"
    )
    if args.check_release_apk:
        apk = args.check_release_apk.resolve()
        validate(apk, tools)
        run(tools / "zipalign", "-c", "-P", "16", "4", apk)
        print("Release APK structure verified; distribution still requires signing verification.")
        return
    run("git", "diff", "--exit-code", args.source_ref, "--", ".")
    if run("git", "ls-files", "--others", "--exclude-standard", capture=True).stdout:
        raise RuntimeError("Commit all release sources first")
    signing = ROOT / ".signing"
    if not all((signing / f).is_file() for f in ["release.keystore", "password"]):
        raise RuntimeError("Missing private distribution key; see docs/releasing.md")
    run(
        ROOT / "gradlew",
        ":app:assembleRelease",
        ":app:lintRelease",
        ":app:testDebugUnitTest",
        ":app:writeRuntimeDependencyList",
    )
    run(sys.executable, ROOT / "tools/check_license_catalog.py")
    run(sys.executable, ROOT / "tools/check_catalog.py")
    output = ROOT / "app/build/outputs/apk/release"
    metadata = json.loads((output / "output-metadata.json").read_text())
    if metadata["variantName"] != "release" or len(metadata["elements"]) != 1:
        raise RuntimeError("Expected a single release variant")
    item = metadata["elements"][0]
    version = item["versionName"]
    unsigned = output / item["outputFile"]
    validate(unsigned, tools)
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    target = dist / f"OneUIModsManager-{version}-release.apk"
    with tempfile.TemporaryDirectory(prefix="oneui-mods-manager-") as folder:
        aligned = Path(folder) / "aligned.apk"
        run(tools / "zipalign", "-P", "16", "-f", "4", unsigned, aligned)
        run(
            tools / "apksigner",
            "sign",
            "--ks",
            signing / "release.keystore",
            "--ks-key-alias",
            "oneuimods-release",
            "--ks-pass",
            "file:" + str(signing / "password"),
            "--debuggable-apk-permitted",
            "false",
            "--out",
            target,
            aligned,
        )
    run(tools / "zipalign", "-c", "-P", "16", "4", target)
    signature = run(
        tools / "apksigner", "verify", "--verbose", "--print-certs", target, capture=True
    ).stdout
    if CERTIFICATE not in signature:
        raise RuntimeError("Distribution certificate mismatch")
    validate(target, tools)
    (dist / f"OneUIModsManager-{version}-signature.txt").write_text(signature)
    source = dist / f"OneUIModsManager-{version}-source.zip"
    run(
        "git",
        "archive",
        "--format=zip",
        "--prefix=OneUIModsManager-" + version + "/",
        "--output=" + str(source),
        args.source_ref,
    )
    (dist / f"SHA256SUMS-{version}.txt").write_text(
        "".join(
            hashlib.sha256(f.read_bytes()).hexdigest() + "  " + f.name + "\n"
            for f in [target, source]
        )
    )
    print("Signed and verified release:", target.name)


if __name__ == "__main__":
    main()
