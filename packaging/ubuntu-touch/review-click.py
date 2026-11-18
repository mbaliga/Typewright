#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Runs `clickable review` on the built click and gates on an exact allowlist of findings.

Why this exists: click-review flags the native click's `unconfined` AppArmor template as
"(NEEDS REVIEW) 'unconfined' not allowed". The JVM app-image needs that template, and only
OpenStore's human reviewers can clear it (docs/UBUNTU_TOUCH_PORT.md, OQ-35). The repo owner decided
on 2026-11-18 that CI may tolerate exactly that finding and nothing else. Clickable's own
--accept-review-warnings is all-or-nothing, so the build runs with --skip-review and this script is
the review gate instead.

Contract:
  * the whole click-review output is printed, tolerated findings included;
  * a finding is tolerated only if its full rule id is in ALLOWLIST and its text matches the
    expected "(NEEDS REVIEW)" text; any other error or warning, any unparsable output, or a
    non-zero exit with no parsable findings fails the script (exit 1).

Usage (from the stage directory, same arguments as the build):
  review-click.py --arch arm64 --docker-image IMAGE --output DIR
  review-click.py --self-test

Keep ALLOWLIST minimal. OpenStore review still has to clear these before any store submission.
"""
import argparse
import glob
import os
import re
import subprocess
import sys

# Full rule id -> regex the finding text must match. Owner decision 2026-11-18 (OQ-35).
ALLOWLIST = {
    "security:template_valid:typewright.apparmor.json":
        r"\(NEEDS REVIEW\)\s*'unconfined' not allowed",
}

ANSI = re.compile(r"\x1b\[[0-9;?]*[A-Za-z]")
HEADER = re.compile(r"^=+\s*([A-Za-z][A-Za-z ]*?)\s*=+\s*$")
ENTRY = re.compile(r"^\s*-+\s*([A-Za-z0-9_.:\-/]+)\s*(.*)$")
PASS_LINE = re.compile(r"^\S.*\.click:\s*pass\s*$", re.IGNORECASE)
FAILING = {"errors": "error", "warnings": "warning"}
IGNORED = {"info", "informational"}


def parse(text):
    """Returns (findings, recognised). findings: list of (kind, rule_id, text)."""
    findings, recognised = [], False
    section, entry = None, None
    for raw in ANSI.sub("", text).splitlines():
        line = raw.rstrip()
        if PASS_LINE.match(line):
            recognised = True
        m = HEADER.match(line)
        if m:
            recognised, section, entry = True, m.group(1).strip().lower(), None
            continue
        if section is None or not line.strip():
            continue
        m = ENTRY.match(line)
        if m and re.match(r"^ {0,3}-", raw):
            entry = [section, m.group(1), m.group(2).strip()]
            findings.append(entry)
        elif raw[:1].isspace() and entry is not None:
            entry[2] = (entry[2] + " " + line.strip()).strip()
        else:
            section, entry = None, None  # unindented noise (e.g. Clickable's own log) ends it
    return [tuple(f) for f in findings], recognised


def evaluate(text, returncode):
    """Returns (ok, tolerated rule ids, problems)."""
    findings, recognised = parse(text)
    tolerated, problems = [], []
    for section, rule, body in findings:
        if section in IGNORED:
            continue
        if section not in FAILING:
            problems.append(f"finding in unknown section '{section}': {rule} {body}")
        elif rule in ALLOWLIST and re.search(ALLOWLIST[rule], body):
            if rule not in tolerated:
                tolerated.append(rule)
        else:
            problems.append(f"{FAILING[section]} not on the allowlist: {rule} {body}".strip())
    if not recognised:
        problems.append("click-review output not recognised (no findings sections)")
    elif returncode != 0 and not findings:
        problems.append(f"review exited {returncode} but no findings could be parsed")
    return not problems, tolerated, problems


def report(ok, tolerated, problems):
    lines = []
    if ok:
        if tolerated:
            lines.append("TOLERATED BY OWNER DECISION (2026-11-18, OQ-35): " + ", ".join(tolerated)
                         + ". Any other error or warning would have failed this step."
                         " OpenStore review must still clear these before store submission.")
        else:
            lines.append("click-review: no findings. (Allowlisted by owner decision 2026-11-18, "
                         "not needed this run: " + ", ".join(ALLOWLIST) + ".)")
    else:
        lines.append("REVIEW FAILED:")
        lines += ["  - " + p for p in problems]
        lines.append("Only these findings are tolerated (owner decision 2026-11-18): "
                     + ", ".join(ALLOWLIST))
    return "\n".join(lines)


def run(args):
    clicks = sorted(glob.glob(os.path.join(args.output, "*.click")))
    if len(clicks) != 1:
        print(f"REVIEW FAILED: expected exactly one .click in {args.output}, found {clicks}")
        return 1
    cmd = ["clickable", "review", clicks[0], "--non-interactive", "--arch", args.arch]
    if args.docker_image:
        cmd += ["--docker-image", args.docker_image]
    try:
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                              errors="replace", check=False)
    except FileNotFoundError:
        print("REVIEW FAILED: clickable not found")
        return 1
    print(proc.stdout)
    ok, tolerated, problems = evaluate(proc.stdout, proc.returncode)
    summary = report(ok, tolerated, problems)
    print(summary)
    target = os.environ.get("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a", encoding="utf-8") as fh:
            fh.write("### click-review\n```\n" + ANSI.sub("", proc.stdout) + "\n" + summary + "\n```\n")
    return 0 if ok else 1


UNCONF = "security:template_valid:typewright.apparmor.json"
OK_BLOCK = f"= Errors =\n- {UNCONF}\n\t(NEEDS REVIEW) 'unconfined' not allowed\n= Warnings =\n= Info =\n"


def self_test():
    cases = [
        ("allowed only", OK_BLOCK, 2, True),
        ("allowed only, as warning", OK_BLOCK.replace("Errors", "Warnings", 1), 3, True),
        ("allowed + other error", OK_BLOCK.replace("= Warnings =\n", "- lint:x:y\n\tbad thing\n= Warnings =\n", 1), 2, False),
        ("allowed + warning", OK_BLOCK.replace("= Info =", "- lint:z\n\tsomething odd\n= Info =", 1), 3, False),
        ("no findings", "./t.click\n= Errors =\n= Warnings =\n= Info =\n", 0, True),
        ("no findings pass line", "./t.click: pass\n", 0, True),
        ("different rule id, same file", OK_BLOCK.replace("template_valid", "policy_groups_safe"), 2, False),
        ("right id, different text", OK_BLOCK.replace("'unconfined' not allowed", "other"), 2, False),
        ("right id, not NEEDS REVIEW", OK_BLOCK.replace("(NEEDS REVIEW) ", ""), 2, False),
        ("garbage, exit 0", "hello\nworld\n", 0, False),
        ("garbage, exit 2", "Traceback boom\n", 2, False),
        ("empty", "", 0, False),
        ("non-zero with empty sections", "= Errors =\n= Warnings =\n", 2, False),
        ("clickable noise after findings", OK_BLOCK + "CRITICAL: Command exited with non-zero 2\n", 2, True),
        ("ansi colours", "\x1b[1m" + OK_BLOCK + "\x1b[0m", 2, True),
    ]
    bad = 0
    for name, text, rc, want in cases:
        got = evaluate(text, rc)[0]
        print(("ok   " if got == want else "FAIL ") + name)
        bad += got != want
    print("self-test: " + ("FAILED" if bad else "all passed"))
    return 1 if bad else 0


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--self-test", action="store_true")
    p.add_argument("--arch")
    p.add_argument("--docker-image")
    p.add_argument("--output")
    a = p.parse_args()
    if a.self_test:
        return self_test()
    if not a.arch or not a.output:
        p.error("--arch and --output are required")
    return run(a)


if __name__ == "__main__":
    sys.exit(main())
