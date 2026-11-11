# Typewright privacy policy

> **Draft for Madhav's edit.** Written from the code as it stands at 1.0.0-rc1 (`CLAUDE.md` law 3).
> Have the lawyer who reads `docs/LICENSING.md` read this too before 1.0.0, and check the contact line.

**Typewright collects nothing.** It has no accounts, no analytics, no advertising, no telemetry and no
crash-reporting service. Nothing about you, your drawings or your fonts is sent to A System of Cells or to
anyone else.

Last updated: for version 1.0.0-rc1. Publisher: Madhav Baliga, as A System of Cells.

## What stays on your device

Your projects (UFO and JSON files), the photographs you take of a sheet, the fonts you open or make, your
settings and your themes are files on your own device, in the places you chose. On the web preview they live in
the browser tab, and a project you do not download is gone when the tab closes. We never see any of it.

## Network

- **This version makes no network connections of its own.** The Android app does not even ask the system for
  the internet permission (`INTERNET`). Fonts the app teaches with are packed inside it.
- The app is allowed to go online only for something you ask for, and the interface says so each time:
  fetching a Google Fonts family so you can compare against it, and, on the web only, an opt-in hosted build.
  Neither is in this version. If a later version adds either, this page changes first, the request is sent
  only when you press the button, it carries no identifier, and nothing is kept.
- There is no GitHub push in this version. If a later version adds one, it uses your own GitHub account and
  your own repository, and only when you ask.
- Tapping a link in About (this policy, the source, a support page) opens it in your browser. That visit is
  between you and that website.
- The web preview is a set of static files. The host that serves them sees an ordinary request for those
  files, the way any web server does; the app adds nothing to it.

## Camera and photos

Android asks for the **camera** permission only when you open Capture's camera to photograph a printed sheet.
The picture is read on the device to find the cells and trace the letters, and is stored only inside your
project if you keep it. It is not uploaded. On desktop and web you give the app a picture file you chose;
nothing else is read. Typewright asks for no location, contacts, microphone, storage-wide or account access.

## Crashes

If the app crashes, the Android app keeps a short text report on the device (the version, the platform, the
error and where it happened; no glyphs, file names, paths or project contents) and shows it the next time you
open the app. **You** decide whether to copy it and send it to us, for instance in a bug report. Nothing is
sent automatically.

## Children, sale of data, advertising

Typewright is not directed at children, sells nothing, and shows no ads. There is no data to sell, share or
delete, because we hold none.

## Fonts you make

The app claims nothing over what you make with it. See `docs/LICENSING.md`.

## Changes and contact

Changes to this policy are made in this repository (`docs/release/PRIVACY.md`), in the open, and the
changelog says when. Contact: _to be filled in by Madhav (an address on the studio's site)_.
