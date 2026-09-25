# project

The open project as plain files: the ProjectSession that owns a project directory (one UFO 3 per master, typewright.json, locks/, scrapbook/, lessons/, comparisons/, build/), routes every font edit through one undo/redo history, enforces law 1's locks with stored diffs, and autosaves atomically. The design is docs/PROJECT_MODEL.md; project/storage holds the platform storage actuals.
