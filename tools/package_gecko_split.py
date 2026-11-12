#!/usr/bin/env python3
"""Package the GeckoView on-demand feature from signed bundletool-generated APK splits.

Input is an .apks zip created from the SAME :app:bundleRelease that produced the matching
versionCode. The normal base APK is released separately. Never include GeckoView native
libraries in the base APK; refuse missing or surprising optional split layouts.
"""
import argparse
import pathlib
import zipfile

FEATURE = "cinema_gecko"
MASTER = FEATURE + "-master.apk"
ARM64 = FEATURE + "-arm64_v8a.apk"
OUTPUTS = (MASTER, ARM64)
MAX_APK = 400 * 1024 * 1024


def extract(apks, output):
    with zipfile.ZipFile(apks, "r") as source:
        items = {}
        for entry in source.infolist():
            if not entry.filename.startswith("splits/"):
                continue
            name = pathlib.PurePosixPath(entry.filename).name
            if not name.startswith(FEATURE + "-"):
                continue
            if name in items:
                raise ValueError(f"Duplicate feature APK: {name}")
            items[name] = entry
        if set(items) != set(OUTPUTS):
            raise ValueError(f"Unexpected GeckoView splits: {sorted(items)}; expected {OUTPUTS}")
        with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_STORED) as target:
            for name in OUTPUTS:
                entry = items[name]
                if entry.file_size < 1 or entry.file_size > MAX_APK:
                    raise ValueError(f"Invalid split size: {name} {entry.file_size}")
                # The APK itself is already compressed and signed; avoid recompressing it.
                target.writestr(name, source.read(entry))
    print(f"Optional feature: {output.stat().st_size:,} bytes")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apks", type=pathlib.Path)
    parser.add_argument("output", type=pathlib.Path)
    args = parser.parse_args()
    extract(args.apks, args.output)


if __name__ == "__main__":
    main()
