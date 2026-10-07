# Page-level Komga progress bypasses the generic Tracker interface

Mihon's generic `Tracker` interface (shared by MAL, AniList, Kitsu, Komga, ...) only carries chapter-granular progress (`lastChapterRead: Double`), because none of the other services have a page-level concept. Komga's server does support true per-book page progress (`PATCH /api/v1/books/{id}/read-progress`), but rather than widening the generic `Track` domain model and every tracker's contract to accommodate a field only one tracker uses, page-level push/pull for Komga is implemented as a separate, Komga-specific side channel that the reader talks to directly, alongside (not instead of) the existing chapter-completion flow through `Tracker.update()`.

## Considered Options

- Add a nullable page field to the domain `Track` model and thread it through `Tracker.update()`/`refresh()` for all trackers. Rejected: touches every tracker implementation and the tracks DB schema for a feature only Komga can use.
- Komga-specific side channel (chosen): new interface implemented only by `Komga`, invoked directly from the reader. No DB migration, no change to the generic tracker contract.

## Consequences

Several behaviors here are deliberate, not accidental, and should not be "simplified" without re-reading this ADR:

- **Reconciliation only ever jumps forward.** When a chapter-open pull resolves, the reader compares the remote page against whatever page it's currently showing (which may have moved on since the pull started) and only jumps if remote is further ahead. It never jumps backward, even on a slow pull that resolves after the reader has already advanced past the remote page.
- **Absent remote progress is treated as an explicit reset**, not as "remote is behind" — Komga returns no read-progress after a user marks a book unread via its own UI, and mihon honors that by clearing local progress too, overriding the usual "further-ahead wins" rule for this one case. This only clears the *stored* page (DB `last_page_read`); unlike a forward jump, it never moves the reader's live on-screen page or shows a toast — a mid-session reset silently rewinding what's currently displayed, with no explanation, would be worse than leaving it alone.
- **Pushes are suppressed until pull resolves for that chapter session**, however long that takes — otherwise a push could fire from local data that was never checked against remote, silently overwriting genuine progress made elsewhere. The chapter-open pull is fire-and-forget: the reader never visibly waits/blocks on it, so there is no timeout to race against — a pull that resolves late still lifts suppression and reconciles; only a real failure leaves the session push-suppressed until the next chapter open.
- **The existing chapter-completion flow through `Tracker.update()` (series-level status, `READING`/`COMPLETED`) is untouched.** The side channel only ever calls Komga's per-book endpoint; it does not alter status semantics.
- **Komga's `page` field is 1-indexed** (`page in 1..pageCount`, enforced server-side by `BookLifecycle.markReadProgress`), while every page number elsewhere in Mihon (`Page.index`, `Chapter.last_page_read`) is 0-indexed. `KomgaApi` converts at the wire boundary (`+1` on push, `-1` on pull) so the rest of the side channel — reconciliation, suppression, the reader — works entirely in Mihon's 0-indexed convention. Do not remove this conversion; without it, pushes silently record one page behind, and a push of Mihon's page 0 is rejected outright by Komga's own validation.
