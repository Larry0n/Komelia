package snd.komelia.ui.reader.image.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class EInkRefreshSchedulerTest {
    @Test
    fun requestedRefreshesAreSpacedApart() = runTest {
        val times = mutableListOf<Long>()
        val scheduler = EInkRefreshScheduler(this, { true }, {}, { times += testScheduler.currentTime }, 500, 5)
        scheduler.requestRefresh()
        advanceTimeBy(6000)
        assertEquals(listOf(620L, 1620L, 2620L, 3620L, 4620L), times)
        scheduler.dispose()
    }

    @Test
    fun longSettlingDelayAlsoSpacesRepeatedRefreshes() = runTest {
        val times = mutableListOf<Long>()
        val scheduler = EInkRefreshScheduler(this, { true }, {}, { times += testScheduler.currentTime }, 1500, 2)
        scheduler.requestRefresh()
        advanceTimeBy(5000)
        assertEquals(listOf(1620L, 3120L), times)
        scheduler.dispose()
    }

    @Test
    fun touchingDuringRepeatCancelsRemainingRefreshes() = runTest {
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { true }, {}, { count++ }, 500, 5)
        scheduler.requestRefresh()
        advanceTimeBy(700)
        assertEquals(1, count)
        scheduler.setBlocked("touch", true)
        advanceTimeBy(5000)
        scheduler.setBlocked("touch", false)
        advanceTimeBy(5000)
        assertEquals(1, count)
        scheduler.dispose()
    }

    @Test
    fun newPageCancelsOldRepeatAndWaitsForNewFrame() = runTest {
        val frames = Channel<Unit>(Channel.UNLIMITED)
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { true }, { frames.receive() }, { count++ }, 500, 2)
        frames.send(Unit)
        scheduler.requestRefresh()
        advanceTimeBy(700)
        assertEquals(1, count)
        scheduler.requestRefresh()
        advanceTimeBy(2000)
        assertEquals(1, count)
        frames.send(Unit)
        runCurrent()
        advanceTimeBy(501)
        assertEquals(2, count)
        advanceTimeBy(1000)
        assertEquals(3, count)
        scheduler.dispose()
    }

    @Test
    fun disposalCancelsRemainingRefreshes() = runTest {
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { true }, {}, { count++ }, 500, 5)
        scheduler.requestRefresh()
        advanceTimeBy(700)
        scheduler.dispose()
        advanceTimeBy(6000)
        assertEquals(1, count)
    }

    @Test
    fun panelSettlingStartsAfterFrameSubmission() = runTest {
        val frames = Channel<Unit>(Channel.UNLIMITED)
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { true }, { frames.receive() }, { count++ }, 500)
        scheduler.requestRefresh()
        advanceTimeBy(1000)
        assertEquals(0, count)
        frames.send(Unit)
        runCurrent()
        advanceTimeBy(499)
        assertEquals(0, count)
        advanceTimeBy(2)
        assertEquals(1, count)
        scheduler.dispose()
    }

    @Test
    fun newPageDuringPanelSettlingRestartsWholeWait() = runTest {
        var count = 0
        var submitted = 0
        val scheduler = EInkRefreshScheduler(this, { true }, { submitted++ }, { count++ }, 500)
        scheduler.requestRefresh()
        advanceTimeBy(400)
        scheduler.requestRefresh()
        advanceTimeBy(619)
        assertEquals(0, count)
        assertEquals(2, submitted)
        advanceTimeBy(2)
        assertEquals(1, count)
        scheduler.dispose()
    }

    @Test
    fun touchDuringPanelSettlingCancelsUntilRelease() = runTest {
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { true }, {}, { count++ }, 500)
        scheduler.requestRefresh()
        advanceTimeBy(400)
        scheduler.setBlocked("touch", true)
        advanceTimeBy(2000)
        assertEquals(0, count)
        scheduler.setBlocked("touch", false)
        advanceTimeBy(619)
        assertEquals(0, count)
        advanceTimeBy(2)
        assertEquals(1, count)
        scheduler.dispose()
    }

    @Test
    fun readinessIsCheckedAgainAfterPanelSettling() = runTest {
        var ready = true
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { ready }, {}, { count++ }, 500)
        scheduler.requestRefresh()
        advanceTimeBy(400)
        ready = false
        advanceTimeBy(1000)
        assertEquals(0, count)
        ready = true
        advanceTimeBy(621)
        assertEquals(1, count)
        scheduler.dispose()
    }

    @Test
    fun duplicateRequestsWaitForFinalFrameAndRefreshOnce() = runTest {
        val frames = Channel<Unit>(Channel.UNLIMITED)
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { true }, { repeat(2) { frames.receive() } }, { count++ })
        repeat(3) { scheduler.requestRefresh() }
        advanceTimeBy(120)
        runCurrent()
        assertEquals(0, count)
        frames.send(Unit)
        runCurrent()
        assertEquals(0, count)
        frames.send(Unit)
        runCurrent()
        assertEquals(1, count)
        advanceTimeBy(1000)
        assertEquals(1, count)
        scheduler.dispose()
    }

    @Test
    fun heldTouchAndFlingMustBothEnd() = runTest {
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { true }, {}, { count++ })
        scheduler.setBlocked("touch", true)
        repeat(5) { scheduler.requestRefresh() }
        advanceTimeBy(1000)
        assertEquals(0, count)
        scheduler.setBlocked("fling", true)
        scheduler.setBlocked("touch", false)
        advanceTimeBy(1000)
        assertEquals(0, count)
        scheduler.setBlocked("fling", false)
        advanceTimeBy(121)
        assertEquals(1, count)
        scheduler.dispose()
    }

    @Test
    fun tapWithoutContentChangeDoesNotRefresh() = runTest {
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { true }, {}, { count++ })
        scheduler.setBlocked("touch", true)
        scheduler.setBlocked("touch", false)
        advanceTimeBy(1000)
        assertEquals(0, count)
        scheduler.dispose()
    }

    @Test
    fun waitsForSlowBitmapAndTiles() = runTest {
        var ready = false
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { ready }, {}, { count++ })
        scheduler.requestRefresh()
        advanceTimeBy(2400)
        assertEquals(0, count)
        ready = true
        advanceTimeBy(121)
        assertEquals(1, count)
        scheduler.dispose()
    }

    @Test
    fun newGestureCancelsRefreshDuringFrameWait() = runTest {
        val frames = Channel<Unit>(Channel.UNLIMITED)
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { true }, { frames.receive() }, { count++ })
        scheduler.requestRefresh()
        advanceTimeBy(121)
        scheduler.setBlocked("touch", true)
        frames.send(Unit)
        runCurrent()
        assertEquals(0, count)
        scheduler.requestRefresh()
        scheduler.setBlocked("touch", false)
        advanceTimeBy(121)
        assertEquals(1, count)
        scheduler.dispose()
    }

    @Test
    fun painterChangeDuringFrameWaitRestartsSettling() = runTest {
        val frames = Channel<Unit>(Channel.UNLIMITED)
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { true }, { frames.receive() }, { count++ })
        scheduler.requestRefresh()
        advanceTimeBy(121)
        scheduler.requestRefresh()
        frames.send(Unit)
        advanceTimeBy(119)
        assertEquals(0, count)
        advanceTimeBy(2)
        assertEquals(1, count)
        scheduler.dispose()
    }

    @Test
    fun readinessIsCheckedAgainAfterSubmission() = runTest {
        var ready = true
        var count = 0
        var waits = 0
        val scheduler = EInkRefreshScheduler(this, { ready }, {
            if (waits++ == 0) ready = false
        }, { count++ })
        scheduler.requestRefresh()
        advanceTimeBy(500)
        assertEquals(0, count)
        ready = true
        advanceTimeBy(121)
        assertEquals(1, count)
        scheduler.dispose()
    }

    @Test
    fun openingMenuDiscardsPendingRefresh() = runTest {
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { true }, {}, { count++ })
        scheduler.requestRefresh()
        scheduler.setBlocked("menu", true, discardPending = true)
        scheduler.setBlocked("menu", false)
        advanceTimeBy(1000)
        assertEquals(0, count)
        scheduler.dispose()
    }

    @Test
    fun leavingReaderCancelsPendingRefresh() = runTest {
        var count = 0
        val scheduler = EInkRefreshScheduler(this, { true }, {}, { count++ })
        scheduler.requestRefresh()
        advanceTimeBy(100)
        scheduler.dispose()
        scheduler.requestRefresh()
        advanceTimeBy(1000)
        assertEquals(0, count)
    }
}
