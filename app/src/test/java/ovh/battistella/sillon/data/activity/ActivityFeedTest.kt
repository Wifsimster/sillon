package ovh.battistella.sillon.data.activity

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ovh.battistella.sillon.data.plex.PlexHttpException
import ovh.battistella.sillon.data.repository.PlexRepository
import java.io.IOException

class ActivityFeedTest {
    private fun listen(user: String) = ActiveListen(
        id = "s-$user", listenerKey = "$user@phone", user = user, device = "Phone", title = "A", subtitle = null,
        artwork = null, state = ListenState.PLAYING, positionMs = 0, durationMs = 1, kind = ListenKind.MUSIC,
        transcoding = false, mine = false,
    )

    @Test
    fun `a shared user is told the activity is not theirs to see`() = runTest {
        val repository = mockk<PlexRepository> {
            coEvery { activityAllowed() } returns false
            every { activityEvents() } returns emptyFlow()
        }

        assertEquals(ActivityState.NotAllowed, ActivityFeed(repository).load())
        assertEquals(listOf(ActivityState.Loading, ActivityState.NotAllowed), ActivityFeed(repository).watch().toList())
    }

    @Test
    fun `a refusal from the server reads as not allowed, a failure as unreachable`() = runTest {
        val refused = mockk<PlexRepository> { coEvery { activityAllowed() } throws PlexHttpException(403, "Forbidden") }
        val down = mockk<PlexRepository> { coEvery { activityAllowed() } throws IOException("no route") }

        assertEquals(ActivityState.NotAllowed, ActivityFeed(refused).load())
        assertEquals(ActivityState.Unreachable, ActivityFeed(down).load())
    }

    @Test
    fun `the list is read again on a timer`() = runTest {
        val answers = ArrayDeque(listOf(emptyList(), listOf(listen("alice"))))
        val repository = mockk<PlexRepository> {
            coEvery { activityAllowed() } returns true
            coEvery { activeListens() } answers { answers.removeFirstOrNull() ?: listOf(listen("alice")) }
            every { activityEvents() } returns emptyFlow()
        }

        val states = ActivityFeed(repository).watch(pollMs = 1_000).take(3).toList()

        assertEquals(
            listOf(ActivityState.Loading, ActivityState.Live(emptyList()), ActivityState.Live(listOf(listen("alice")))),
            states,
        )
    }

    @Test
    fun `a play event from the server refreshes the list at once`() = runTest {
        val events = MutableSharedFlow<Unit>()
        val answers = ArrayDeque(listOf(emptyList(), listOf(listen("ben"))))
        val repository = mockk<PlexRepository> {
            coEvery { activityAllowed() } returns true
            coEvery { activeListens() } answers { answers.removeFirstOrNull() ?: listOf(listen("ben")) }
            every { activityEvents() } returns events
        }
        val seen = mutableListOf<ActivityState>()
        val job = launch { ActivityFeed(repository).watch(pollMs = 600_000, settleMs = 100, restMs = 50).collect { seen += it } }
        advanceTimeBy(60)
        runCurrent()
        assertEquals(ActivityState.Live(emptyList()), seen.last())

        events.emit(Unit)
        advanceTimeBy(200)
        runCurrent()

        assertEquals(ActivityState.Live(listOf(listen("ben"))), seen.last())
        job.cancel()
    }
}
