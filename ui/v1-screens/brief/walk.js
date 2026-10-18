// SPDX-License-Identifier: FSL-1.1-ALv2
// Walks every door of the Brief prototype end to end, as a person would, and fails on any state
// with no way forward, a console error or a page that scrolls sideways. Screenshots go to
// build/shots. Run after build.py:
//   npm i playwright@1 && CHROME=/path/to/chromium node ui/v1-screens/brief/walk.js "$PWD/ui/v1-screens/brief/build/brief-prototype.html"
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

const page_url = "file://" + process.argv[2];
const shots = path.join(__dirname, "build", "shots");
fs.mkdirSync(shots, { recursive: true });

// An answer is a row of type (.choice) or, for short mono answers, a canvas chip (.chip).
const ANSWER = ".choice, .chip";
const DOORS = ["I know the style", "I know what it's for", "I know fonts I like", "I know the feeling", "I've drawn some letters", "I don't know yet"];

async function run() {
  const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined });
  const problems = [];
  const report = [];
  for (const scheme of ["light", "dark"]) {
    for (const [di, door] of DOORS.entries()) {
      if (scheme === "dark" && di > 0) break;
      const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 2, colorScheme: scheme, reducedMotion: "reduce" });
      const page = await ctx.newPage();
      const errors = [];
      page.on("pageerror", (e) => errors.push(String(e)));
      page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
      const t0 = Date.now();
      await page.goto(page_url);
      await page.waitForSelector(".choice");
      const loadMs = Date.now() - t0;
      const tag = `${scheme}-${di + 1}`;
      if (di === 0) await page.screenshot({ path: `${shots}/${tag}-0-doors.png` });
      await page.click(`.choice:has-text("${door}")`);
      let steps = 0;
      const prompts = [];
      for (; steps < 60; steps++) {
        const wrap = await page.$('.label:text-is("Your brief")');
        if (wrap) break;
        const prompt = (await page.textContent(".prompt")).trim();
        prompts.push(prompt);
        if (steps === 0) await page.screenshot({ path: `${shots}/${tag}-1-first.png`, fullPage: false });
        const forward = await page.$$('.choice, .chip, button:has-text("Skip"), button:has-text("Done"), button:has-text("Set wordmark"), button:has-text("Use these answers")');
        if (!forward.length) { problems.push(`${tag}: no way forward at "${prompt}"`); break; }
        if (prompt === "What is it for?") {
          if (di === 1) await page.click('.chip:has-text("Watch face")').catch(() => page.click(ANSWER));
          await page.click('button:has-text("Done")');
        } else if (prompt === "What should it feel like?") {
          const c = await page.$$(ANSWER);
          await c[0].click();
          await (await page.$$(ANSWER))[1].click();
          await page.click('button:has-text("Done")');
        } else if (prompt === "Which fonts do you like?") {
          const before = await page.$$eval(".choice", (els) => els.map((e) => e.textContent));
          if (steps === 0 && di === 2) {
            report.push(`${tag} references opens with: ${before.slice(0, 6).map((t) => t.replace(/\s+/g, " ").trim().slice(0, 22)).join(" | ")}`);
            await page.fill("#search", "bodoni");
            await page.waitForTimeout(50);
            const found = await page.$$eval(".choice", (els) => els.map((e) => e.querySelector(".t").textContent));
            report.push(`${tag} search "bodoni": ${found.join(", ")}`);
            await page.screenshot({ path: `${shots}/${tag}-1b-search.png` });
          }
          await page.click(".choice");
          await page.click('button:has-text("Done")');
        } else if (prompt === "What does the wordmark say?") {
          await page.fill("#wordmark", "Typewright");
          await page.click('button:has-text("Set wordmark")');
        } else if (prompt === "What do your letters already say?") {
          const ex = await page.$('button:has-text("Try the example letters")');
          if (!ex) { problems.push(`${tag}: mirror without letters offers no example`); }
          await page.screenshot({ path: `${shots}/${tag}-1a-mirror-empty.png` });
          await ex.click();
          await page.screenshot({ path: `${shots}/${tag}-1b-mirror-read.png`, fullPage: true });
          await page.click('button:has-text("Use these answers")');
        } else {
          const c = await page.$(ANSWER);
          if (c) await c.click(); else await page.click('button:has-text("Skip")');
        }
      }
      if (steps >= 60) problems.push(`${tag}: still asking after 60 steps`);
      const sentence = (await page.textContent(".sentence")).trim();
      await page.screenshot({ path: `${shots}/${tag}-2-wrap.png`, fullPage: true });
      for (const need of ["See your guide", "Read the brief", "Try another door"]) {
        if (!(await page.$(`button:has-text("${need}")`))) problems.push(`${tag}: wrap-up has no "${need}"`);
      }
      const settle = await page.$$eval(".tension .t", (els) => els.map((e) => e.textContent));
      const drawFirst = await page.$('.label:text-matches("^Draw (first|next)$") + .glyphs');
      const first = drawFirst ? (await drawFirst.textContent()).trim() : null;
      if (!first) problems.push(`${tag}: wrap-up names no letters to draw first`);
      const more = await page.$('button:has-text("more")');
      const moreText = more ? (await more.textContent()).trim() : "none";
      // Settle a tension by answering again, when there is one with a Change/Answer button.
      const change = await page.$('.tension button:has-text("Change answer"), .tension button:has-text("Answer it")');
      if (change && di === 1) {
        await change.click();
        const focusedPrompt = (await page.textContent(".prompt")).trim();
        report.push(`${tag} settle → asks "${focusedPrompt}"`);
        await page.screenshot({ path: `${shots}/${tag}-2b-settle.png` });
        await page.click(ANSWER);
      }
      // Ask more, then back to the wrap-up.
      if (more && di === 5) {
        await more.click();
        let extra = 0;
        while (!(await page.$('.label:text-is("Your brief")')) && extra < 20) { await page.click(ANSWER); extra++; }
        report.push(`${tag} ask more → ${extra} more questions, then the wrap-up again`);
      }
      await page.click('button:has-text("Read the brief")');
      await page.screenshot({ path: `${shots}/${tag}-3-brief.png`, fullPage: true });
      const faces = await page.$$eval(".face .t", (els) => els.map((e) => e.textContent));
      if (!(await page.$('button:has-text("See your guide")'))) problems.push(`${tag}: Brief tab does not lead to the guide`);
      await page.click('button:has-text("See your guide")');
      await page.screenshot({ path: `${shots}/${tag}-4-guide.png`, fullPage: true });
      const startDrawing = await page.$$('button:has-text("Start drawing")');
      if (!startDrawing.length) problems.push(`${tag}: guide has no Start drawing`);
      await startDrawing[startDrawing.length - 1].click();
      const handoff = await page.$('.label:text-is("Draw opens with")');
      if (!handoff) problems.push(`${tag}: Start drawing does not hand off`);
      await page.screenshot({ path: `${shots}/${tag}-5-handoff.png` });
      await page.click('button:has-text("Use the example letters")');
      const drift = await page.$$eval(".label", (els) => els.map((e) => e.textContent));
      const driftItems = await page.$$eval(".item .t", (els) => els.map((e) => e.textContent).filter((t) => /off target|close|on target/.test(t)));
      await page.screenshot({ path: `${shots}/${tag}-6-guide-measured.png`, fullPage: true });
      const overflow = await page.evaluate(() => document.scrollingElement.scrollWidth - window.innerWidth);
      if (overflow > 0) problems.push(`${tag}: page scrolls sideways by ${overflow}px`);
      if (errors.length) problems.push(`${tag}: console errors: ${errors.join(" | ")}`);
      report.push(`${tag} ${door}: load ${loadMs}ms, ${steps} questions [${prompts.join(" / ")}]`);
      report.push(`   brief: "${sentence}"`);
      report.push(`   settle: ${settle.join(" | ") || "nothing"}; draw first: ${first}; ${moreText}`);
      report.push(`   faces: ${faces.slice(0, 6).join(", ")}`);
      report.push(`   after drawing: ${driftItems.join(" | ") || "(no drift items)"}; sections: ${drift.join(" · ")}`);
      await ctx.close();
    }
  }
  await browser.close();
  console.log(report.join("\n"));
  console.log(problems.length ? "PROBLEMS:\n" + problems.join("\n") : "NO DEAD ENDS: every door reached the wrap-up, the guide and the hand-off.");
  process.exit(problems.length ? 1 : 0);
}
run().catch((e) => { console.error(e); process.exit(2); });
