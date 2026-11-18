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
    expected "(NEEDS REVIEW)" text; any other error or warning, a finding in an unknown section,
    an unparsable line inside a section, a missing or repeated verdict line, or a FAIL verdict
    with no parsable findings fails the script (exit 1).

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
# The layout click-review really prints (first seen in CI run 44233319924, kept verbatim as
# REAL_RUN below): a bare section title underlined with dashes, " - <rule id>" entries whose text
# lines are tab-indented, then one verdict line "<path>.click: pass|FAIL".
TITLE = re.compile(r"^[A-Za-z][A-Za-z ]*$")
UNDERLINE = re.compile(r"^-{3,}$")
ENTRY = re.compile(r"^ {0,3}- ?([A-Za-z0-9_.:\-/]+)\s*(.*)$")
VERDICT = re.compile(r"^\S.*\.click: (pass|FAIL)$")
FAILING = {"errors": "error", "warnings": "warning"}
IGNORED = {"info", "informational"}


def parse(text):
    """Returns (findings, verdicts, stray). findings: list of (kind, rule_id, text).

    Lines outside a section (Clickable's and Podman's own chatter) are ignored; a line inside a
    section that is neither an entry nor an indented continuation is returned as stray, so the
    caller can fail closed on it.
    """
    findings, verdicts, stray = [], [], []
    section, entry = None, None
    lines = [raw.rstrip() for raw in ANSI.sub("", text).splitlines()]
    skip = False
    for i, line in enumerate(lines):
        if skip:
            skip = False
            continue
        m = VERDICT.match(line)
        if m:
            verdicts.append(m.group(1))
            section, entry = None, None
            continue
        nxt = lines[i + 1] if i + 1 < len(lines) else ""
        if TITLE.match(line) and UNDERLINE.match(nxt) and len(nxt) == len(line):
            section, entry, skip = line.strip().lower(), None, True
            continue
        if section is None or not line.strip():
            continue
        m = ENTRY.match(line)
        if m:
            entry = [section, m.group(1), m.group(2).strip()]
            findings.append(entry)
        elif line[:1].isspace() and entry is not None:
            entry[2] = (entry[2] + " " + line.strip()).strip()
        else:
            stray.append(line)
    return [tuple(f) for f in findings], verdicts, stray


def evaluate(text, returncode):
    """Returns (ok, tolerated rule ids, problems)."""
    findings, verdicts, stray = parse(text)
    tolerated, problems, failing = [], [], 0
    for section, rule, body in findings:
        if section in IGNORED:
            continue
        failing += 1
        if section not in FAILING:
            problems.append(f"finding in unknown section '{section}': {rule} {body}")
        elif rule in ALLOWLIST and re.search(ALLOWLIST[rule], body):
            if rule not in tolerated:
                tolerated.append(rule)
        else:
            problems.append(f"{FAILING[section]} not on the allowlist: {rule} {body}".strip())
    problems += [f"unparsable line inside a findings section: {s!r}" for s in stray]
    if len(verdicts) != 1:
        problems.append("click-review output not recognised (expected exactly one "
                        f"'<click>: pass|FAIL' verdict line, found {len(verdicts)})")
    elif verdicts[0] == "FAIL" and not failing:
        problems.append("review verdict is FAIL but no findings could be parsed")
    elif verdicts[0] == "pass" and returncode != 0:
        problems.append(f"review verdict is pass but the command exited {returncode}")
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
CLICK = "/home/runner/work/Typewright/Typewright/build/ubuntu-touch/out/typewright.asoc_0.0.1_arm64.click"
# Verbatim output of the first real run (CI run 44233319924, job 134563465954, clickable 8.10.0).
REAL_RUN = (
    "\x1b[92mRunning review on " + CLICK + "\x1b[0m\n"
    "WARNING: image platform (linux/amd64) does not match the expected platform (linux/arm64)\n"
    "Errors\n"
    "------\n"
    " - " + UNCONF + "\n"
    "\t(NEEDS REVIEW) 'unconfined' not allowed\n"
    "\thttp://askubuntu.com/q/671403\n"
    + CLICK + ": FAIL\n"
    "\x1b[91mCommand exited with non-zero exit status 2, see above for details. "
    "This is most likely not a problem with Clickable.\x1b[0m\n"
)
FINDING = " - " + UNCONF + "\n\t(NEEDS REVIEW) 'unconfined' not allowed\n\thttp://askubuntu.com/q/671403\n"
VERDICT_FAIL = CLICK + ": FAIL\n"
OTHER = " - lint:x:y\n\tbad thing\n"
WARN = "Warnings\n--------\n - lint:z\n\tsomething odd\n"


def self_test():
    cases = [
        ("allowed only (real run, verbatim)", REAL_RUN, 1, True),
        ("allowed + other error", REAL_RUN.replace(VERDICT_FAIL, OTHER + VERDICT_FAIL), 1, False),
        ("allowed + warning", REAL_RUN.replace(VERDICT_FAIL, WARN + VERDICT_FAIL), 1, False),
        ("no findings", "Running review on " + CLICK + "\n" + CLICK + ": pass\n", 0, True),
        ("different rule id, same file", REAL_RUN.replace("template_valid", "policy_groups_safe"), 1, False),
        ("garbage, exit 0", "hello\nworld\n", 0, False),
        ("garbage, exit 2", "Traceback boom\n", 2, False),
        ("empty", "", 0, False),
        ("allowed only, as warning", REAL_RUN.replace("Errors\n------", "Warnings\n--------"), 1, True),
        ("right id, different text", REAL_RUN.replace("'unconfined' not allowed", "other"), 1, False),
        ("right id, not NEEDS REVIEW", REAL_RUN.replace("(NEEDS REVIEW) ", ""), 1, False),
        ("other error only", REAL_RUN.replace(FINDING, OTHER), 1, False),
        ("warning only", "Warnings\n--------\n - lint:z\n\tsomething odd\n" + VERDICT_FAIL, 1, False),
        ("finding in an unknown section", REAL_RUN.replace("Errors\n------", "Fatal\n-----"), 1, False),
        ("FAIL verdict, nothing parsed", VERDICT_FAIL, 1, False),
        ("FAIL verdict, old '= Errors =' layout", "= Errors =\n- " + UNCONF + "\n\t(NEEDS REVIEW) 'unconfined' not allowed\n" + VERDICT_FAIL, 1, False),
        ("findings but no verdict line", REAL_RUN.replace(VERDICT_FAIL, ""), 1, False),
        ("two verdict lines", REAL_RUN + CLICK + ": pass\n", 1, False),
        ("pass verdict, non-zero exit", CLICK + ": pass\n", 1, False),
        ("pass verdict with an error listed", REAL_RUN.replace(": FAIL", ": pass").replace(FINDING, OTHER), 0, False),
        ("stray line inside a section", REAL_RUN.replace(VERDICT_FAIL, "something unexpected\n" + VERDICT_FAIL), 1, False),
        ("info section is not a finding", REAL_RUN.replace(VERDICT_FAIL, "Info\n----\n - lint:ok\n\tOK\n" + VERDICT_FAIL), 1, True),
        ("no ansi colours", ANSI.sub("", REAL_RUN), 1, True),
    ]
    bad = 0
    for name, text, rc, want in cases:
        got = evaluate(text, rc)[0]
        print(("ok   " if got == want else "FAIL ") + name)
        bad += got != want
    if evaluate(REAL_RUN, 1)[1] != [UNCONF]:
        print("FAIL real run must tolerate exactly " + UNCONF)
        bad += 1
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
