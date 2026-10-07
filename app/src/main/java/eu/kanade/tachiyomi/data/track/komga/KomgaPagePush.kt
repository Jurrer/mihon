package eu.kanade.tachiyomi.data.track.komga

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** A pending Komga page-level push: which book, what page, and which chapter session it belongs to. */
data class KomgaPushRequest(val bookUrl: String, val page: Int, val session: KomgaChapterSession)

/**
 * Schedules Komga page-level pushes: [request] debounces rapid page changes during active
 * reading into a single push, while [flush] bypasses the debounce for an immediate push (chapter
 * exit, app backgrounding). Both paths push the same [KomgaPushRequest] shape.
 */
class KomgaPagePushCoordinator(
    scope: CoroutineScope,
    debounceMillis: Long,
    private val push: suspend (KomgaPushRequest) -> Unit,
) {
    private val requests = MutableSharedFlow<KomgaPushRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        requests
            .debounce(debounceMillis)
            .onEach { push(it) }
            .launchIn(scope)
    }

    fun request(request: KomgaPushRequest) {
        requests.tryEmit(request)
    }

    suspend fun flush(request: KomgaPushRequest) {
        push(request)
    }
}
