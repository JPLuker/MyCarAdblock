#!/usr/bin/env python3
"""Offline tests for signed optional GeckoView split selection (no Android SDK required)."""
import importlib.util
import pathlib
import tempfile
import unittest
import zipfile

MODULE = pathlib.Path(__file__).with_name("package_gecko_split.py")
spec = importlib.util.spec_from_file_location("package_gecko_split", MODULE)
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)


class PackageGeckoSplitTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.source = self.root / "signed.apks"
        self.result = self.root / "optional.zip"

    def make_source(self, entries):
        with zipfile.ZipFile(self.source, "w") as apks:
            for name, content in entries.items():
                apks.writestr(name, content)

    def test_only_two_optional_splits_are_published(self):
        self.make_source({
            "splits/base-master.apk": b"base",
            "splits/cinema_gecko-master.apk": b"master",
            "splits/cinema_gecko-arm64_v8a.apk": b"native",
            "toc.pb": b"bundletool",
        })
        package.extract(self.source, self.result)
        with zipfile.ZipFile(self.result) as ready:
            self.assertEqual(
                ready.namelist(),
                ["cinema_gecko-master.apk", "cinema_gecko-arm64_v8a.apk"],
            )
            self.assertEqual(ready.read("cinema_gecko-master.apk"), b"master")

    def test_missing_abi_split_is_rejected(self):
        self.make_source({"splits/cinema_gecko-master.apk": b"master"})
        with self.assertRaises(ValueError):
            package.extract(self.source, self.result)

    def test_unexpected_feature_splits_are_rejected(self):
        self.make_source({
            "splits/cinema_gecko-master.apk": b"master",
            "splits/cinema_gecko-arm64_v8a.apk": b"native",
            "splits/cinema_gecko-config.x86_64.apk": b"other",
        })
        with self.assertRaises(ValueError):
            package.extract(self.source, self.result)

    def test_duplicate_feature_split_is_rejected(self):
        self.make_source({
            "splits/cinema_gecko-master.apk": b"master",
            "splits/cinema_gecko-arm64_v8a.apk": b"native",
            "splits/another/cinema_gecko-master.apk": b"duplicate",
        })
        with self.assertRaises(ValueError):
            package.extract(self.source, self.result)


if __name__ == "__main__":
    unittest.main()
