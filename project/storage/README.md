# project/storage

The platform storage behind ProjectSession: the desktop filesystem and folder picker, Android's Storage Access Framework with a persisted tree permission, and the web's File System Access API with an in-memory and .zip fallback. Each implements the ProjectStore interface from project, as docs/PROJECT_MODEL.md §5 specifies.
