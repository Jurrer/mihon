package eu.kanade.tachiyomi.data.track.komga

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private const val DEBOUNCE_MILLIS = 3000L
private const val BOOK_URL = "https://example.com/api/v1/books/abc-123"

class KomgaPagePushCoordinatorTest {

    @Test
    fun `rapid page changes within the debounce window collapse into a single push`() = runTest {
        val pushed = mutableListOf<KomgaPushRequest>()
        val coordinator = KomgaPagePushCoordinator(backgroundScope, DEBOUNCE_MILLIS) { pushed += it }
        val session = KomgaChapterSession()

        coordinator.request(KomgaPushRequest(BOOK_URL, 1, session))
        advanceTimeBy(500)
        coordinator.request(KomgaPushRequest(BOOK_URL, 2, session))
        advanceTimeBy(500)
        coordinator.request(KomgaPushRequest(BOOK_URL, 3, session))
        advanceTimeBy(DEBOUNCE_MILLIS + 100)
        runCurrent()

        pushed shouldBe listOf(KomgaPushRequest(BOOK_URL, 3, session))
    }

    @Test
    fun `leaving the current chapter flushes immediately, bypassing the debounce`() = runTest {
        val pushed = mutableListOf<KomgaPushRequest>()
        val coordinator = KomgaPagePushCoordinator(backgroundScope, DEBOUNCE_MILLIS) { pushed += it }
        val session = KomgaChapterSession()
        val request = KomgaPushRequest(BOOK_URL, 1, session)

        // A page change queues a debounced push, then chapter exit flushes before it would fire.
        coordinator.request(request)
        coordinator.flush(request)

        pushed shouldBe listOf(request)
    }

    @Test
    fun `app backgrounding flushes immediately, bypassing the debounce`() = runTest {
        val pushed = mutableListOf<KomgaPushRequest>()
        val coordinator = KomgaPagePushCoordinator(backgroundScope, DEBOUNCE_MILLIS) { pushed += it }
        val session = KomgaChapterSession()
        val request = KomgaPushRequest(BOOK_URL, 1, session)

        // Same immediate-flush path as leaving the chapter, triggered by backgrounding instead.
        coordinator.request(request)
        coordinator.flush(request)

        pushed shouldBe listOf(request)
    }
}
