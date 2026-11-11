// SPDX-License-Identifier: Apache-2.0
// Tests app-web's locale guard (docs/OPEN_QUESTIONS.md item 13) under Node: `node tools/locale_guard_test.mjs`.
import { createRequire } from "node:module";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const guard = createRequire(import.meta.url)(path.join(here, "../app-web/src/wasmJsMain/resources/locale-guard.js"));

// What Chromium reports with no LANG set: Intl.Locale throws on it, which is the bug.
assert.throws(() => new Intl.Locale("en-US@posix"), RangeError);
assert.equal(guard.normaliseLocale("en-US@posix"), "en-US");
assert.equal(guard.normaliseLocale("en_GB.UTF-8"), "en-GB");
assert.equal(guard.normaliseLocale("C"), "en-US");
assert.equal(guard.normaliseLocale("POSIX"), "en-US");
assert.equal(guard.normaliseLocale(""), "en-US");
assert.equal(guard.normaliseLocale(undefined), "en-US");
assert.equal(guard.normaliseLocale("!!"), "en-US");
// A tag that is already fine is left alone.
assert.equal(guard.normaliseLocale("hi-IN"), "hi-IN");
assert.equal(guard.normaliseLocale("en-GB"), "en-GB");
assert.deepEqual(guard.normaliseLanguages(["en-US@posix", "en_US", "fr"]), ["en-US", "fr"]);
assert.deepEqual(guard.normaliseLanguages([]), ["en-US"]);

// install() patches a broken navigator and leaves a good one untouched.
const broken = { language: "en-US@posix", languages: ["en-US@posix"] };
assert.equal(guard.install(broken), true);
assert.equal(broken.language, "en-US");
assert.deepEqual([...broken.languages], ["en-US"]);
assert.doesNotThrow(() => new Intl.Locale(broken.language));
const fine = { language: "en-GB", languages: ["en-GB", "hi"] };
assert.equal(guard.install(fine), false);
assert.equal(fine.language, "en-GB");
assert.deepEqual(fine.languages, ["en-GB", "hi"]);

console.log("locale guard: all checks passed");
