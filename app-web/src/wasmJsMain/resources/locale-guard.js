// SPDX-License-Identifier: FSL-1.1-ALv2
//
// Locale guard (docs/OPEN_QUESTIONS.md item 13). Chromium on Linux with no LANG set reports
// navigator.language as "en-US@posix". Compose Multiplatform passes it to Intl.Locale, which throws
// "RangeError: Incorrect locale information provided", and the first composition fails: only the page
// background shows. This file runs before typewright.js and makes navigator.language and
// navigator.languages hold tags that Intl.Locale accepts. It changes nothing when they already do.
(function (global) {
    "use strict";

    var FALLBACK = "en-US";

    function accepted(tag) {
        try {
            new Intl.Locale(tag);
            return true;
        } catch (e) {
            return false;
        }
    }

    // "en_US.UTF-8@posix" -> "en-US"; "C" and "POSIX" -> the fallback; anything still invalid -> the fallback.
    function normaliseLocale(tag) {
        if (typeof tag !== "string") return FALLBACK;
        var cleaned = tag.split("@")[0].split(".")[0].replace(/_/g, "-").trim();
        if (/^(c|posix)$/i.test(cleaned) || cleaned === "") return FALLBACK;
        return accepted(cleaned) ? cleaned : FALLBACK;
    }

    function normaliseLanguages(tags) {
        var out = [];
        for (var i = 0; i < tags.length; i++) {
            var tag = normaliseLocale(tags[i]);
            if (out.indexOf(tag) < 0) out.push(tag);
        }
        return out.length ? out : [FALLBACK];
    }

    function install(nav) {
        var language = nav.language;
        var languages = Array.prototype.slice.call(nav.languages || []);
        var needed = !accepted(language) || languages.some(function (tag) { return !accepted(tag); });
        if (!needed) return false;
        var fixedLanguages = Object.freeze(normaliseLanguages(languages.length ? languages : [language]));
        var fixedLanguage = normaliseLocale(language);
        Object.defineProperty(nav, "language", { get: function () { return fixedLanguage; }, configurable: true });
        Object.defineProperty(nav, "languages", { get: function () { return fixedLanguages; }, configurable: true });
        return true;
    }

    var api = { normaliseLocale: normaliseLocale, normaliseLanguages: normaliseLanguages, install: install };
    if (typeof module !== "undefined" && module.exports) module.exports = api;
    if (global && global.navigator) install(global.navigator);
})(typeof window !== "undefined" ? window : undefined);
