package snd.komelia.ui.reader.image.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.oshai.kotlinlogging.KotlinLogging

/** Main-thread only. All reader operations share one pending refresh. */
internal class EInkRefreshScheduler(
    private val scope: CoroutineScope,
    private val isReady: () -> Boolean,
    private val awaitFinalFrame: suspend () -> Unit,
    private val refresh: suspend () -> Unit,
    private val settleDelayMillis: Long = 0,
    private val refreshCount: Int = 1,
) {
    private val logger = KotlinLogging.logger("KomeliaEInk")
    private val blockers = mutableSetOf<Any>()
    private var pending: Job? = null
    private var dirty = false
    private var generation = 0L
    private var disposed = false

    fun requestRefresh() {
        if (disposed) return
        dirty = true
        generation++
        schedule()
    }

    fun setBlocked(owner: Any, blocked: Boolean, discardPending: Boolean = false) {
        if (blocked) {
            blockers.add(owner)
            pending?.cancel()
            if (discardPending) dirty = false
        } else if (blockers.remove(owner)) {
            schedule()
        }
    }

    private fun schedule() {
        pending?.cancel()
        if (disposed || !dirty || blockers.isNotEmpty()) return
        val requestedGeneration = generation
        pending = scope.launch {
            var waits = 0
            // The image pipeline coalesces work for 50 ms and panel updates for 100 ms.
            // This quiet window merges their notifications; it is NOT a rendering fence.
            do {
                delay(120)
                if (++waits == 20) logger.info { "Still waiting for reader/image readiness" }
            } while (!isReady())
            logger.info { "Reader and image ready; awaiting final frame" }
            awaitFinalFrame()
            // Buffer submission does not imply the E-Ink driver has displayed it.
            // New content, touches, menus and disposal cancel this wait as well.
            delay(settleDelayMillis)
            if (requestedGeneration == generation && blockers.isEmpty() && isReady()) {
                dirty = false
                repeat(refreshCount.coerceIn(1, 5)) { index ->
                    if (index > 0) delay(maxOf(1000L, settleDelayMillis))
                    if (requestedGeneration != generation || blockers.isNotEmpty() || !isReady()) return@launch
                    logger.info { "Full refresh ${index + 1}/$refreshCount" }
                    refresh()
                }
            } else if (dirty && blockers.isEmpty()) {
                schedule()
            }
        }
    }

    fun dispose() {
        disposed = true
        dirty = false
        pending?.cancel()
        blockers.clear()
    }
}
