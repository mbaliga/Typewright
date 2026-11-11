# Web preview: build and deploy

> Draft for Madhav's edit. The web build is a **Preview**: it cannot compile a font and cannot run Google Fonts'
> own checks (layer one); it exports the project as UFO and a zip, and the desktop app builds it (`docs/V1_SCOPE.md`,
> D4). The page says so in a banner above the app and again in About. Nothing here was run against a real static
> host, and browser behaviour is owner-verified only.

## Build

```
./gradlew :app-web:wasmJsBrowserDistribution
```

The static site is written to `app-web/build/dist/wasmJs/productionExecutable/`: `index.html`,
`locale-guard.js`, `typewright.js` (and its map), the hashed `.wasm` files, `composeResources/`, and `legal/` (the licence, `THIRD_PARTY.md`, `TRADEMARKS.md`,
the privacy policy). Every file in the folder goes on the host, in one folder, unchanged. The first production build
downloads Binaryen (`wasm-opt`) from its GitHub releases; where github.com is unreachable, pass
`-Ptypewright.wasmOpt=/path/to/wasm-opt` (the README's Toolchain section).

## The locale guard (OQ 13)

Chromium with no `LANG` reports `navigator.language` as `en-US@posix`, which makes `Intl.Locale` throw and leaves a
blank page. `index.html` loads `locale-guard.js` before the app; it rewrites `navigator.language(s)` to tags
`Intl.Locale` accepts, and does nothing when they already are. `node tools/locale_guard_test.mjs` tests it (CI's web
job runs it). Do not remove the `<script src="locale-guard.js">` line when you edit `index.html`.

## Hosts: what any static host needs

- Serve the folder as it is. No server code, no database, no environment variables, no cookies.
- `.wasm` must be sent as `application/wasm`; `.js` as a JavaScript type. GitHub Pages, Netlify, Cloudflare Pages
  and Caddy/nginx defaults do this; check with the browser's network panel if the app does not start.
- HTTPS. A browser needs a secure context for several of the APIs the app uses (clipboard, file access).
- The app is not built to need special headers (cross-origin isolation); if a browser console says otherwise, that is a defect to report.
- Caching: the `.wasm` files have content-hashed names and may be cached for a long time; `index.html`,
  `typewright.js` and `locale-guard.js` do not, so serve those with `Cache-Control: no-cache` (revalidate).
- Compression: let the host compress `.wasm` and `.js` (gzip or brotli).
- Browsers: one with WebAssembly GC support (current Chrome, Firefox and Safari). The page shows the `<noscript>`
  line only when scripting is off; a browser that is too old shows a blank page. Test the ones you intend to support.

## Deploy, by host

**GitHub Pages (a `gh-pages` branch or Pages artifact):** copy the folder's contents to the Pages root; if the site is
served from a path such as `/Typewright/`, nothing should need changing, because `index.html` loads its files by relative
name.

**Netlify / Cloudflare Pages / any bucket:** publish directory = `app-web/build/dist/wasmJs/productionExecutable`.
The build command is the Gradle line above; it needs JDK 21 and Node is downloaded by the build.

**Your own server:** `rsync -a --delete app-web/build/dist/wasmJs/productionExecutable/ host:/var/www/typewright/`.

## What the host learns

An ordinary request log for those files (address, time, user agent), as for any web page. The app sends nothing else
anywhere (`PRIVACY.md`). If the host adds analytics of its own, the Preview banner and the privacy policy would be
wrong: turn it off or change the policy first.

## Not done

CI uploads the production bundle as an artifact on `main` (7 days) and nothing deploys it. There is no domain, no
host account and no staging site; those are the owner's.
