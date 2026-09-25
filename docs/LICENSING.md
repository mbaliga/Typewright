# Typewright: licensing and money

Decided 25 Sep 2026. Licensor: **Madhav Baliga**, as an individual. "A System of Cells" is
the studio brand, not a registered entity.

I'm not a lawyer. This is a plan built from the licences' own texts. Have the lawyer you use
for the trademark filings read §1 and §5 before v1.0.0 ships.

---

## 1. The licence: fair source for the app, open source for the engine

| Part | Licence | Why |
|---|---|---|
| **The app**: `ui`, `learn`, `learn/scenes`, `campaign`, `app-android`, `app-desktop`, `app-web`, and the Workbook and lesson text written for Typewright | **FSL-1.1-ALv2** (Functional Source License 1.1, Apache 2.0 future licence) | Fair source. Anyone may read, use, modify and redistribute the app for any purpose except selling a competing product. Each release automatically becomes Apache-2.0 two years after it's published. |
| **The engine**: `core-geometry`, `core-font`, `engine-trace`, `engine-construct`, `qa`, `qa/corpus`, `scripts`, `shape-preview`, `compile`, `build-logic`, `tools`, `data/scripts` | **Apache-2.0** | Already published under Apache-2.0: the repository has had that LICENSE since its first commit, so this code is Apache-2.0 for anyone who has a copy. Keeping it Apache makes it eligible for open-source grants and reusable across the constellation, and it stays consistent with the history. |
| Template sheets (`scripts/templates`) and the node-economy data packs (`data/*.json`) | **CC0-1.0** | Whatever you draw on a template, and any font you make with the app, carries no conditions from us. The data packs are measurements (point counts). |
| Bundled fonts | their own licences (OFL-1.1, and Apache-2.0 for Roboto Slab) | unchanged; listed in About (S23) |
| **Fonts people make with Typewright** | **theirs** | The app claims nothing over its output. Ship offers OFL for Google Fonts, or the user's own licence. |

### What FSL does and doesn't stop

- **Free for:** every individual, student, school, researcher, non-profit, freelancer,
  foundry and company **making fonts with it**. Internal use at any company. Teaching with
  it. Forking it for yourself. Contributing.
- **Not allowed until a release's two-year date:** taking the app and offering it, or
  something substantially like it, as a **commercial product or service**. That covers a
  paid rebrand on the Play Store, and a hosted "Typewright-in-the-cloud" that competes with
  our hosted builds.
- **Never granted:** the name. FSL has no trademark grant, and TRADEMARKS.md asks any fork
  to rename.

### Why this keeps our movement free

1. **Every door stays open to us.** As the only copyright holder, we can relicense the app
   to Apache-2.0 at any moment with one commit. Going the other way (making published
   Apache code restrictive again) is impossible. So starting with FSL is the reversible
   choice.
2. **No contributor agreement is needed.** Contributions come in under Apache-2.0 with a DCO
   sign-off (CONTRIBUTING.md). Apache is permissive, so we can ship contributed code inside
   the FSL app, a commercial exception, or a future relicense without asking anyone.
3. **No copyleft on us or our dependencies.** Nothing GPL or AGPL is linked (that rule is
   already in CLAUDE.md). AGPL, the handoff's earlier idea, would have bound any hosted
   service and scared off commercial exceptions. FSL does neither.
4. **Nothing in the app is sold, so no store billing is needed** (§3).
5. **No accounts, no licence keys, no server we must keep alive** for the app to work.

### The one real cost, stated plainly

**F-Droid and IzzyOnDroid accept only OSI/FSF-licensed apps, so the FSL app can't be listed
there.** Distribution for V1 is Google Play, Huawei AppGallery, GitHub Releases (which works
with Obtainium) and the website. If those two stores matter more than the fair-source
protection, relicense the app to Apache-2.0: one commit, and nothing else changes. Grants
that require an open-source licence apply to the engine, which qualifies.

---

## 2. Can it make money and still be free for most people? Yes, modestly.

The honest scale: a font-making app is a niche. Expect revenue that covers hosting and some
of your time, not a salary. The bigger return is reputation as a well-made, principled tool.
The model below never gates a feature.

| Stream | Who pays | When | Notes |
|---|---|---|---|
| **Supporters** ("Friends of Typewright") | people who want it to exist | V1 | Pay what you want, one-time or monthly, on the website. **Unlocks nothing.** Supporters are thanked in About if they opt in. The same model as Fonebrew's patronage. |
| **Hosted builds** on the web | web users who want a TTF without installing anything | v1.x | The only thing that costs us money to run: fontmake plus fontbakery in a zero-retention container. A small free quota, then credits, or included for supporters. Desktop and Android always build locally for free. This needs law 3's amendment in §4. |
| **Commercial exception licences** | companies that want to ship the app's code inside their own commercial tool before the two-year conversion | on request | Priced per deal. The engine is already Apache, so this covers the product layer only. |
| **Grants and commissions** | Google Fonts (script expansion and tooling), FOSS United (India), NLnet/NGI (engine only) | now | Indic and South-East Asian script tooling from Hyderabad makes a strong grant case. |
| **Education** | design schools, workshops | later | The Workbook as a taught course; paid workshops; classroom packs. |

**Never:** ads, data, feature paywalls, subscriptions for features, accounts, or telemetry.

---

## 3. Where money changes hands (so no store rules bind us)

- **Nothing is sold inside the Android app, so Google Play Billing isn't needed** and no
  store takes a cut. The Play build's About screen shows no payment link. Supporting happens
  on the website, found through the Play listing's website field.
- Desktop, web and GitHub-release builds show a "Support Typewright" link in About (S23).
- Payments go through a **merchant of record** (Lemon Squeezy, the constellation's default,
  or Paddle), which handles tax for an individual seller in India. It's swappable: no SDK in
  the app, only a link.
- If in-app support on Play is ever wanted, a Play Billing purchase that unlocks nothing is
  allowed. It costs the Play fee and isn't needed for V1.

---

## 4. Changes to CLAUDE.md

- **Law 3, amended:** "No telemetry, no analytics, no phoning home, ever. The only network
  calls are the ones the user asks for: fetching a Google Fonts family for comparison,
  pushing to their GitHub, and, on the web only, an opt-in hosted build that the UI names
  every time it's used and that keeps nothing."
- Replace "No LICENSE file and no SPDX headers until the licence is decided" with: "Every
  source file carries `SPDX-License-Identifier: FSL-1.1-ALv2` (app modules) or
  `Apache-2.0` (engine modules). An Apache module may never depend on an FSL module; CI
  checks this."

---

## 5. Files to add (drafts in this pack)

- `LICENSE`: FSL-1.1-ALv2 with the copyright line, plus a short header explaining the split.
- `LICENSES/Apache-2.0.txt`: the Apache text already at the root, moved.
- `LICENSES/CC0-1.0.txt`: fetch the official text (not in this pack).
- a one-line `LICENSE` in each engine module pointing to `LICENSES/Apache-2.0.txt`.
- `CONTRIBUTING.md`: DCO plus inbound Apache-2.0.
- `TRADEMARKS.md`: how the name may and may not be used.
- `THIRD_PARTY.md`: unchanged, and it now feeds S23.

**Before tagging v1.0.0:** name clearance for TYPEWRIGHT (Classes 9 and 42 through Shiva,
as for the other marks). The FSL's protection and TRADEMARKS.md both rest on owning the
name.
