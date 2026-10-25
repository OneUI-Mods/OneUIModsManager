#!/usr/bin/env python3
"""Attach APK-derived metadata to an existing release; never modifies APKs or Git tags."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
from urllib.parse import quote

from release_metadata import ROOT, write_metadata


def gh(*args):
    return subprocess.run(["gh", *args], check=True, capture_output=True, text=True).stdout


def publish(repo, tag, tools, dry_run=False):
    catalog = json.loads((ROOT / "catalog/apps.json").read_text(encoding="utf-8"))
    app = next((a for a in catalog["apps"] if repo == "OneUI-Mods/" + a["repo"]), None)
    if app is None:
        raise ValueError("Repository is not registered in the public catalog")
    release = json.loads(gh("api", f"repos/{repo}/releases/tags/{quote(tag, safe='')}"))
    if release["draft"]:
        raise ValueError("Only published releases are supported")
    apks = [a for a in release["assets"] if a["name"].lower().endswith(".apk")
            and not any(word in a["name"].lower() for word in ["debug", "unsigned"])]
    if len(apks) != 1:
        raise ValueError("The release must contain exactly one release APK")
    asset = apks[0]
    if Path(asset["name"]).name != asset["name"] or any(c in asset["name"] for c in "*?[]\\"):
        raise ValueError("Unexpected APK filename")
    with tempfile.TemporaryDirectory(prefix="oneui-release-metadata-") as folder:
        directory = Path(folder)
        gh("release", "download", tag, "--repo", repo, "--pattern", asset["name"], "--dir", folder)
        apk = directory / asset["name"]
        output = directory / "release-metadata.json"
        metadata = write_metadata(apk, output, tools)
        if metadata["packageName"] != app["packageName"]:
            raise ValueError("Release APK belongs to another registered app")
        digest = asset.get("digest") or ""
        if digest.startswith("sha256:") and digest[7:] != metadata["sha256"]:
            raise ValueError("Downloaded APK differs from the GitHub asset digest")
        existing = next((a for a in release["assets"] if a["name"] == output.name), None)
        if existing is not None:
            previous = directory / "previous"
            gh("release", "download", tag, "--repo", repo, "--pattern", output.name,
               "--dir", str(previous))
            value = json.loads((previous / output.name).read_text(encoding="utf-8"))
            if not all(value.get(key) == item for key, item in metadata.items()):
                raise ValueError("Existing metadata differs; it was not overwritten")
            print(f"{repo} {tag}: existing metadata verified")
            return
        if not dry_run:
            gh("release", "upload", tag, "--repo", repo, str(output))
        print(f'{repo} {tag}: verified build {metadata["versionCode"]}'
              + (" (dry run)" if dry_run else "; metadata published"))


def main():
    import os
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--tools", type=Path, default=Path(
        os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk")) / "build-tools/37.0.0")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    publish(args.repo, args.tag, args.tools, args.dry_run)


if __name__ == "__main__":
    main()
