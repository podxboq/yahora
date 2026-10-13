#!/usr/bin/env python3
# Copyright (C) 2026 podxboq
#
# This file is part of Yahora.
#
# Yahora is free software: you can redistribute it and/or modify it under the
# terms of the GNU General Public License as published by the Free Software
# Foundation, either version 3 of the License, or (at your option) any later
# version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
"""Builds the signed APK and AAB of a release tag the way F-Droid will.

F-Droid rebuilds the tagged commit and ships the APK attached to the GitHub
release only if its own build matches it byte for byte. A build from the
everyday working copy does not reliably match: 0.2.0's first APK carried
leftovers of earlier builds in its DEX and no git revision at all, and F-Droid
refused it (issue #22). So this never builds in place. It clones the repository
afresh, checks out the tag, and builds there with no daemon and no build cache,
which is as close to F-Droid's buildserver as a laptop gets.

The clone is taken from GitHub by default, since that is what F-Droid reads: a
tag that was never pushed, or was moved without pushing, fails here first.

Signing comes from YAHORA_KEYSTORE and its passwords in ~/.gradle/gradle.properties,
as for any release build. The script checks that the APK is signed with the
key the F-Droid recipe allows, and prints the SHA-256 of what it wrote.

  tools/build-release.py v0.2.0
  tools/build-release.py v0.2.0 --out ~/releases --twice
"""
import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RECIPE = ROOT / "fdroid" / "com.podxboq.yahora.yml"
DEFAULT_REPO = "https://github.com/podxboq/yahora.git"


def run(*args, cwd=None, capture=False):
    result = subprocess.run(args, cwd=cwd, check=True, text=True,
                            capture_output=capture)
    return result.stdout if capture else None


def android_home():
    """The SDK from the environment, else from this checkout's local.properties."""
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if home:
        return home
    properties = ROOT / "local.properties"
    if properties.exists():
        match = re.search(r"^sdk\.dir=(.+)$", properties.read_text(), re.M)
        if match:
            return match.group(1).strip()
    sys.exit("No Android SDK: set ANDROID_HOME.")


def apksigner(sdk):
    tools = sorted(Path(sdk, "build-tools").iterdir(),
                   key=lambda p: [int(n) for n in re.findall(r"\d+", p.name)])
    return str(tools[-1] / "apksigner")


def allowed_signer():
    match = re.search(r"^AllowedAPKSigningKeys:\s*(\w+)", RECIPE.read_text(), re.M)
    return match.group(1)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build(tag, repo, sdk, workdir):
    """Clones [repo] into [workdir], builds [tag] there and returns (apk, aab)."""
    run("git", "clone", "--quiet", repo, str(workdir))
    run("git", "-c", "advice.detachedHead=false", "checkout", "--quiet", tag,
        cwd=workdir)
    # A tag that is not signed is not a release; see the README. The clone has
    # no config of its own, so it borrows the allowed signers of this checkout.
    signers = subprocess.run(
        ["git", "config", "gpg.ssh.allowedSignersFile"],
        cwd=ROOT, text=True, capture_output=True,
    ).stdout.strip()
    config = ["-c", f"gpg.ssh.allowedSignersFile={signers}"] if signers else []
    run("git", *config, "verify-tag", tag, cwd=workdir)
    env_sdk = {**os.environ, "ANDROID_HOME": sdk}
    subprocess.run(
        ["./gradlew", "--no-daemon", "--no-build-cache", "--quiet",
         "assembleRelease", "bundleRelease"],
        cwd=workdir, env=env_sdk, check=True,
    )
    outputs = workdir / "app" / "build" / "outputs"
    return (outputs / "apk" / "release" / "app-release.apk",
            outputs / "bundle" / "release" / "app-release.aab")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("tag", help="release tag, such as v0.2.0")
    parser.add_argument("--out", type=Path, default=Path.cwd(),
                        help="where to write the APK and AAB (default: here)")
    parser.add_argument("--repo", default=DEFAULT_REPO,
                        help="repository to clone (default: GitHub)")
    parser.add_argument("--twice", action="store_true",
                        help="build a second time from another clone and "
                             "require the same APK")
    args = parser.parse_args()

    if not re.fullmatch(r"v\d+\.\d+\.\d+", args.tag):
        sys.exit(f"Not a release tag: {args.tag}")
    version = args.tag[1:]
    sdk = android_home()

    with tempfile.TemporaryDirectory(prefix="yahora-release-") as tmp:
        apk, aab = build(args.tag, args.repo, sdk, Path(tmp, "first"))

        signers = run(apksigner(sdk), "verify", "--print-certs", str(apk),
                      capture=True)
        if allowed_signer() not in signers:
            sys.exit("The APK is not signed with the key the F-Droid recipe "
                     "allows. Is YAHORA_KEYSTORE set in ~/.gradle/gradle.properties?")

        if args.twice:
            again, _ = build(args.tag, args.repo, sdk, Path(tmp, "second"))
            if sha256(again) != sha256(apk):
                sys.exit("Two clean builds of the same tag differ: F-Droid "
                         "will not be able to match this APK.")

        args.out.mkdir(parents=True, exist_ok=True)
        for source, suffix in ((apk, "apk"), (aab, "aab")):
            target = args.out / f"yahora-{version}.{suffix}"
            shutil.copyfile(source, target)
            print(f"{sha256(target)}  {target}")


main()
