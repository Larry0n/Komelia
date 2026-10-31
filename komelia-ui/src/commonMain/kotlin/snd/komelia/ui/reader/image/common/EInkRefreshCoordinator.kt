package snd.komelia.ui.reader.image.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.CoroutineScope
import snd.komelia.image.ReaderImage

/** Tracks only composed, visible reader images. Prefetched pages cannot trigger refresh. */
class EInkRefreshCoordinator(
    scope: CoroutineScope,
    isReaderReady: () -> Boolean,
    awaitFinalFrame: suspend () -> Unit,
    refresh: suspend () -> Unit,
    settleDelayMillis: Long = 0,
    refreshCount: Int = 1,
) {
    private class ImageState(val image: ReaderImage?) {
        var bounds = Rect.Zero
        var drawnPainter: Painter? = null
    }

    private val images = mutableMapOf<Any, ImageState>()
    private val scheduler = EInkRefreshScheduler(
        scope = scope,
        isReady = {
            val visible = images.values.filter { !it.bounds.isEmpty }
            isReaderReady() && visible.isNotEmpty() && visible.all {
                val image = it.image
                image != null && image.isReadyForRefresh &&
                    it.drawnPainter != null && it.drawnPainter === image.painter.value
            }
        },
        awaitFinalFrame = awaitFinalFrame,
        refresh = refresh,
        settleDelayMillis = settleDelayMillis,
        refreshCount = refreshCount,
    )

    fun registerImage(owner: Any, image: ReaderImage?) {
        images[owner] = ImageState(image)
    }

    fun removeImage(owner: Any) {
        images.remove(owner)
    }

    fun onImagePositioned(owner: Any, bounds: Rect) {
        val state = images[owner] ?: return
        if (state.bounds == bounds) return
        val wasVisible = !state.bounds.isEmpty
        state.bounds = bounds
        if ((wasVisible || !bounds.isEmpty) && state.drawnPainter != null) requestRefresh()
    }

    fun onImageDrawn(owner: Any, painter: Painter) {
        val state = images[owner] ?: return
        if (state.drawnPainter === painter) return
        state.drawnPainter = painter
        if (!state.bounds.isEmpty) requestRefresh()
    }

    fun requestRefresh() = scheduler.requestRefresh()
    fun setBlocked(owner: Any, blocked: Boolean, discardPending: Boolean = false) =
        scheduler.setBlocked(owner, blocked, discardPending)

    fun dispose() {
        scheduler.dispose()
        images.clear()
    }
}

val LocalEInkRefreshCoordinator = staticCompositionLocalOf<EInkRefreshCoordinator?> { null }
