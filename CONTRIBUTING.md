# Contributing to Typewright

> Draft written from `docs/LICENSING.md` §1 and §5. The owner's own draft from the V1 pack
> replaces this file when it lands.

## The licence of your contribution

Everything you contribute, to the app or the engine, comes in under the **Apache License,
Version 2.0** (`LICENSES/Apache-2.0.txt`). You sign each commit with the **Developer
Certificate of Origin 1.1** (below). There is no contributor licence agreement.

Why Apache inbound when the app is FSL-1.1-ALv2: Apache is permissive, so the project can ship
your code inside the FSL app, inside a commercial exception licence, or under a future
relicence without asking you again. You keep your copyright. `docs/LICENSING.md` explains the
split.

## Signing off

Add a sign-off line to every commit, with your real name and an email you can be reached at:

```
git commit -s
```

which appends

```
Signed-off-by: Your Name <you@example.com>
```

By signing off you certify the DCO below for that commit.

## Before you open a pull request

- Read `CLAUDE.md`. Its laws and conventions apply to people and agents alike.
- New source files carry the SPDX line for their directory. `python3 tools/check_licences.py
  --fix` adds it; without `--fix` it checks, as CI does.
- Run `./gradlew spotlessCheck jvmTest` (add `-Ptypewright.android=false` if you have no
  Android SDK).
- Record any new dependency, with its licence, in `THIRD_PARTY.md`. Nothing GPL or AGPL is
  linked.
- The name and icon are covered by `TRADEMARKS.md`, not by any code licence.

## Developer Certificate of Origin

```
Developer Certificate of Origin
Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors.

Everyone is permitted to copy and distribute verbatim copies of this
license document, but changing it is not allowed.


Developer's Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.
```
