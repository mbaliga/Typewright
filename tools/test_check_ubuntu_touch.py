# SPDX-License-Identifier: Apache-2.0
"""Negative cases for check_ubuntu_touch.py: each breaks one rule in a scratch copy of the sources."""
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import check_ubuntu_touch as c  # noqa: E402

REPO = Path(__file__).resolve().parent.parent


class CheckUbuntuTouchTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp)
        shutil.copytree(REPO / "packaging/ubuntu-touch", self.tmp / "packaging/ubuntu-touch")
        (self.tmp / "app-desktop").mkdir()
        shutil.copy(REPO / "app-desktop/build.gradle.kts", self.tmp / "app-desktop/build.gradle.kts")
        self.base = self.tmp / "packaging/ubuntu-touch"

    def problems(self):
        version = c.gradle_package_version(self.tmp)
        return c.check_click(self.base, False, version) + c.check_click(self.base / "preview", True, version)

    def edit_json(self, path, fn):
        data = json.loads(path.read_text())
        fn(data)
        path.write_text(json.dumps(data))

    def test_repo_sources_pass(self):
        self.assertEqual(self.problems(), [])

    def test_version_drift(self):
        self.edit_json(self.base / "manifest.json", lambda d: d.update(version="9.9.9"))
        self.assertTrue(any("packageVersion" in p for p in self.problems()))

    def test_preview_must_say_preview(self):
        self.edit_json(self.base / "preview/manifest.json", lambda d: d.update(title="Typewright"))
        self.assertTrue(any("must say Preview" in p for p in self.problems()))

    def test_native_must_not_be_a_preview(self):
        self.edit_json(self.base / "manifest.json", lambda d: d.update(title="Typewright Preview"))
        self.assertTrue(any("must not call itself a preview" in p for p in self.problems()))

    def test_networking_group_refused(self):
        self.edit_json(self.base / "preview/typewright-preview.apparmor.json",
                       lambda d: d.update(policy_groups=["webview", "networking"]))
        self.assertTrue(any("networking" in p for p in self.problems()))

    def test_preview_not_unconfined(self):
        self.edit_json(self.base / "preview/typewright-preview.apparmor.json",
                       lambda d: d.update(template="unconfined"))
        self.assertTrue(any("must not be unconfined" in p for p in self.problems()))

    def test_native_needs_unconfined(self):
        self.edit_json(self.base / "typewright.apparmor.json", lambda d: d.pop("template"))
        self.assertTrue(any("unconfined template" in p for p in self.problems()))

    def test_missing_launcher(self):
        (self.base / "typewright-launch.sh").unlink()
        self.assertTrue(any("Exec" in p for p in self.problems()))

    def test_hardcoded_arch_refused(self):
        self.edit_json(self.base / "manifest.json", lambda d: d.update(architecture="arm64"))
        self.assertTrue(any("@CLICK_ARCH@" in p for p in self.problems()))

    def test_bad_name(self):
        self.edit_json(self.base / "manifest.json", lambda d: d.update(name="Typewright"))
        self.assertTrue(any("appname.developer" in p for p in self.problems()))


if __name__ == "__main__":
    unittest.main()
