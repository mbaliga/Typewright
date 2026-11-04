# project

The open project as plain files: the ProjectSession that owns a project directory (one UFO 3 per master, typewright.json, locks/, scrapbook/, lessons/, comparisons/, build/), routes every font edit through one undo/redo history, enforces law 1's locks with stored diffs, and autosaves atomically. The design is docs/PROJECT_MODEL.md; project/storage holds the platform storage actuals.

It also holds the importer (`project/imports`): a TrueType font or a UFO folder is read and given a licence audit, then becomes a new project or is adopted where it lies, with every glyph approved and locked on arrival (docs/PROJECT_MODEL.md section 15).
