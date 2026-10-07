# Mihon

Android manga/comic reader app (Tachiyomi fork). Reads content from installed extension sources and optionally syncs reading progress to external trackers (MyAnimeList, AniList, Komga, etc).

## Language

**Chapter-level progress**:
The existing tracker sync model: a single number (`lastChapterRead: Double`) representing the highest fully-completed chapter/book. This is what the generic `Tracker` interface carries, shared by every tracker service (MAL, AniList, Kitsu, Komga, ...).
_Avoid_: Whole-chapter progress

**Page-level progress**:
Progress within a single chapter, expressed as a page number. Only meaningful for trackers whose backing service can store it per-book (currently only Komga). Not part of the generic `Tracker` interface — carried over a Komga-specific side channel instead.
_Avoid_: Incremental progress, in-chapter progress

**Push** (page-level progress):
Sending the current page number for the chapter being read to Komga's per-book read-progress endpoint. Debounced/throttled during active reading, with an immediate flush on chapter exit or app backgrounding.
_Avoid_: Sync (ambiguous with pull), upload

**Pull** (page-level progress):
Fetching Komga's remote page number for a chapter at the moment it's opened, to reconcile against the locally stored page.
_Avoid_: Sync (ambiguous with push), download, refresh

**Flush**:
An immediate (non-debounced) push, triggered by leaving the current chapter or the app being backgrounded.

**Reconciliation**:
The decision made once a chapter-open pull resolves: compare the remote page against whatever page the reader is currently showing (which may have already moved past the page open triggered on), and jump forward if remote is ahead. Never jumps backward — if the reader has already advanced past the remote page (by the time a slow pull resolves), reconciliation is a no-op. A visible jump shows a short "Synced from Komga" message; a no-op reconciliation shows nothing.
_Avoid_: Merge, sync

**Explicit reset**:
Komga signals "no read progress" (page/completed absent) for a book after a user marks it unread via Komga's own UI, distinct from a book that simply hasn't been read on any device yet. Reconciliation honors this by resetting local page-level progress too, overriding the normal "remote wins only if further ahead" rule for this one case.

**Push suppression**:
For a given chapter-viewing session, page-level pushes are withheld until pull has resolved (successfully or with a real failure) — so a push can never fire from local data that hasn't yet been checked against remote, which would risk clobbering genuine further progress made elsewhere. Pull is fire-and-forget (the reader never visibly waits/blocks on it), so however long it takes to resolve, suppression lifts for the rest of that session once it does. A hard pull failure (not just a slow one) leaves pushes suppressed for the rest of that session; the next chapter open starts a fresh pull.
