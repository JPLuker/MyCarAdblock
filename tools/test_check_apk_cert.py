#!/usr/bin/env python3
"""Regression tests for optional GeckoView APK signer pinning."""
import os
import unittest
from unittest.mock import patch

import check_apk_cert


class CertificatePinningTests(unittest.TestCase):
    BASE = "base.apk"
    SPLIT = "cinema_gecko-master.apk"
    A = "a" * 64
    B = "b" * 64

    def run_check(self, certificates, expected=""):
        with patch.object(check_apk_cert, "actual_certificate", side_effect=certificates), \
             patch.dict(os.environ, {"MYCAR_EXPECT_CERT_SHA256": expected}):
            check_apk_cert.check([self.BASE, self.SPLIT])

    def test_matching_certificates_with_pinned_signer_pass(self):
        self.run_check([self.A, self.A], self.A)

    def test_matching_temporary_debug_certificates_without_pin_pass(self):
        self.run_check([self.B, self.B])

    def test_split_signed_by_different_key_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "DIFFERENT"):
            self.run_check([self.A, self.B], self.A)

    def test_both_apks_signed_by_wrong_key_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "differs"):
            self.run_check([self.B, self.B], self.A)

    def test_invalid_certificate_pin_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "malformed"):
            self.run_check([self.A, self.A], "not-a-valid-fingerprint")


if __name__ == "__main__":
    unittest.main()
