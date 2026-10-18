# The Brief

A person who sits down to make a font usually knows more than they can say. "Art deco, for a
watch face" is a start, but it is not a set of numbers a drawing can be held to. The Brief is the
section that turns what they know (or half-know, or don't know) into measured targets, then
keeps those targets beside the drawing as it grows. It is the missing upstream half of the
quality gate: the gate asks "is this font well made?", the Brief asks "is it the font you meant?".

## What it is for

1. **Find the ethos.** A short set of questions, each answered by looking at a real typeface,
   not by reading a term.
2. **Make it measurable.** Every answer becomes a range on a feature the style detector already
   measures (`qa/corpus`), taken from the style atlas, never invented (CLAUDE.md law 5).
3. **Say where the answers disagree.** "Squarish rounds" and "art deco" is fine; "calm" and "loud"
   on the same aperture is a tension worth naming before a letter is drawn.
4. **Guide the drawing.** What to draw first and why, requirements the chosen use imposes, and,
   once letters exist, which of them have drifted from the brief and how to bring them back.

## The flow, end to end

1. **A door.** The person starts from what they know (below). Every door asks one question at a
   time, each answered by looking at real typefaces; "I'm not sure" and Skip always move on.
2. **The wrap-up.** When a door's questions are through, the Brief never just stops: it sets the
   brief in one sentence, names the letters to draw first and why, and leads to the guide. The
   tensions it found sit under that, to settle now or later, each with "Keep as signature" or
   "Change answer". "Ask N more" adds the dimensions the door left out; "Try another door" keeps
   every answer and asks what that door asks first.
3. **The Brief and the Guide.** The Brief is the record (targets, tensions, faces that fit) and ends
   by pointing at the guide. The guide is what to do, most pressing first, and ends in "Start
   drawing".
4. **Drawing, and back.** Start drawing closes the Brief onto Draw. The Brief keeps its answers,
   door and tab while closed (with no project open they live only in this session, and the screen
   says so), so coming back lands on the guide, now measuring the letters drawn: drift items say
   what to move, or offer to change the brief instead.

A tension or a drift item can reopen one question on its own ("Changing one answer"), whatever the
door; answering it, or keeping the answer, returns to where the person was.

## Doors: six ways in

The person chooses where to start; every door ends in the same record.

| Door | For someone who knows… | Starts from |
|---|---|---|
| Style | the name of it ("a grotesque", "art deco") | the genre, then the questions that genre leaves open |
| Use | where it will live (watch face, app, signage, headline…) | the use's requirements, then genres usual for it |
| References | fonts they like | the faces' measured agreement, then where they differ |
| Feeling | how it should feel (calm, loud, warm…) | measured profiles of the feeling, up to three |
| Mirror | nothing, but has drawn letters | the drawing is measured and the app reads it back |
| Blank | nothing at all | the most separating question first, answered by pictures |

## The record

The answers are an `Ethos` in the project's brief (`project`): genre, uses and their options,
feelings, references, a wordmark, answers by dimension, signatures (departures the person has
chosen to keep) and the ids of steps answered or skipped. It stores choices only. The numbers
are derived from the atlas each time they are shown, so a regenerated atlas never leaves a stale
number in a project.

## Dimensions and answers

Each dimension (contrast, stress, serifs, bracketing, apertures, a- and g-storeys, terminals,
roundness, x-height, width, stem weight, crossbar position, waist) has three to five answers. An
answer is a `Level` with anchors on atlas features: a pooled quantile band, an explicit band, or
a categorical value. Every answer is shown in a cue face that the tests prove measures as that
answer (`AtlasConsistencyTest`): the picture and the number cannot drift apart.

## Targets and their origin

A target takes the first of: the person's answer, what their references agree on, what their
feelings measure as, what the genre's faces do. Each carries an origin (how many faces stand
behind it) and the table shows it. Anything without measurement behind it is labelled
"our heuristic". Genres with fewer than four measured faces are never described by counts.

## Tensions

Found from the targets alone, each with a source kind (rule, guideline, evidence, measured,
derived, convention, our heuristic):

- a use's requirement against an answer (a watch face's always-on state, small sizes, tabular figures);
- an answer against its genre ("None of the 8 art deco faces in the atlas have squarish rounds");
- feelings against feelings; references against each other.

A tension is information, not an error. The person can keep the departure as a *signature*, and
the Guide then stops flagging it.

## Reading a drawing

`readAs` takes the nearest atlas faces by weighted distance (k = 9; contrast on a log scale,
scales taken from the corpus interquartile ranges) and votes for a genre. Left-one-out accuracy
on the atlas is printed by `ReadingAccuracyTest`: 52% top-1 and 83% top-3 among the ten corpus
classes, 40% and 69% across all 23. The app shows the neighbours, not just the verdict, because
that is what the number is really made of.

`readAs` is the Brief's only reader (docs/HANDOFF_BRIEF_FOLD.md "one reader"). `qa:corpus`'s
older `StyleScorer.rankStyleClasses` (TYPEWRIGHT_BUILD_BRIEF.md 8.4's "small hand-written scorer")
reads left one out on the same 242 corpus faces at 36% top-1 / 76% top-3 -- markedly behind
`readAs`'s 52% / 83% on the same faces -- and no V1 screen calls it (`docs/V1_TRIAGE.md` item 19).
It is `@Deprecated` with a pointer to `readAs`, not removed: it needs no style atlas to vote
against, only its own ten-class heuristic, so a caller outside the Brief with no atlas available
could still reach for it.

## Drift

For each target feature the drawing's own measurement is compared: ON, NEAR, OFF or UNMEASURED
(too few letters to measure). Every OFF item carries advice written for that feature ("tilt the
stress: move the thickest parts of o toward eight and two o'clock"). The drawing is never
changed by the Brief (law 1): drift is advice.

## The plan

A drawing order built from the brief: figures first for a watch face, capitals first for signage,
the letters that carry the answers (n, o, H, O for contrast and stress) before the rest, the day
and month capitals if a date is wanted, a wordmark's own letters first. Letters are never listed twice.

## What it does not do

- It does not choose for the person. Skip and "I'm not sure" are always there.
- It does not draw, regenerate or alter outlines.
- It never phones home. The atlas is a file in the app; nothing about a brief leaves the device.
- Browser builds cannot read the atlas yet (see docs/OPEN_QUESTIONS.md); there the questions
  work and the measured ranges don't, and the screen says so.

## The prototype

`ui/v1-screens/brief/` is the design source for the Brief's screens (CLAUDE.md law 6): one page that
runs this engine, compiled to JavaScript with `kotlinc-js`, over the real style atlas and the real
cue faces. `build.py` builds it into `build/brief-prototype.html`; the page holds no logic of its
own beyond drawing the engine's JSON view and sending actions back. Hyle Deco, this repository's
test font, can stand in for drawn letters, so the whole loop (ask, brief, guide, draw, measure)
can be tried end to end. The prototype keeps answers in the page only.

## Where the code is

`brief/` (engine, pure Kotlin, JVM + Wasm), `ui/.../ui/brief/` (screen, state, cue fonts),
`qa/corpus` (`StyleAtlas`, `StyleMeasurement`), `data/style-atlas-latin.json` with its generator
(`data/scripts/fetch_style_atlas_fonts.py`, `StyleAtlasGeneratorTest`), `data/brief-cues/` with
`data/scripts/build_brief_cues.py`.
