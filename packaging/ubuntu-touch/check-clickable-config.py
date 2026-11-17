# SPDX-License-Identifier: Apache-2.0
"""Loads both clickable.yaml files with Clickable's own config loader and checks what it parsed.

Needs `pip install clickable-ut==8.10.0`. Validates against Clickable's JSON schema, which is the
same validation `clickable build` runs first, without needing Docker. Run from the repository root.
"""
import os
import sys

from clickable.config.project import ProjectConfig

EXPECT = {
    "packaging/ubuntu-touch": {"builder": "precompiled", "restrict_arch": "arm64"},
    "packaging/ubuntu-touch/preview": {"builder": "pure"},
}

root = os.getcwd()
failed = False
for directory, expected in EXPECT.items():
    os.chdir(root)
    os.chdir(directory)  # the loader looks for clickable.yaml from the process cwd
    config = ProjectConfig(cwd=os.getcwd()).config
    for key, value in expected.items():
        if config.get(key) != value:
            print(f"{directory}: {key} is {config.get(key)!r}, expected {value!r}", file=sys.stderr)
            failed = True
    print(f"{directory}: loaded; builder={config['builder']} framework={config['framework']}")
sys.exit(1 if failed else 0)
