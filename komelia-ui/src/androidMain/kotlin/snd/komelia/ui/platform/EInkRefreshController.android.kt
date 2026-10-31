package snd.komelia.ui.platform

import android.os.Build
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import snd.komelia.settings.model.EInkRefreshMode
import kotlin.coroutines.resume

@Composable
actual fun rememberEInkRefreshController(): EInkRefreshController {
    val view = LocalView.current
    return remember(view) {
        BigmeEInkApi.initialize(view.context)
        if (BigmeEInkApi.isSupported.value) BigmeEInkRefreshController(view)
        else NoOpEInkRefreshController
    }
}

private class BigmeEInkRefreshController(private val view: View) : EInkRefreshController {
    override val isSupported = BigmeEInkApi.isSupported
    override val isTouchActive = AndroidWindowInputState.isTouchActive
    private var frameSubmitted = false

    override suspend fun awaitFrameSubmission() = withContext(Dispatchers.Main.immediate) {
        frameSubmitted = false
        if (!view.isAttachedToWindow || !view.hasWindowFocus() || !view.isShown) return@withContext
        Log.i("KomeliaEInk", "Waiting for final frame; hardwareAccelerated=${view.isHardwareAccelerated}")
        // Frame callbacks run BEFORE rendering. On API 29+ wait for the render thread
        // to submit a buffer, not merely for another Choreographer vsync.
        frameSubmitted = withTimeoutOrNull(1000) {
            suspendCancellableCoroutine { continuation ->
                val observer = view.viewTreeObserver
                val callback = Runnable {
                    if (continuation.isActive) continuation.resume(true)
                }
                if (Build.VERSION.SDK_INT >= 29 && view.isHardwareAccelerated) {
                    observer.registerFrameCommitCallback(callback)
                    continuation.invokeOnCancellation {
                        view.post {
                            if (observer.isAlive) observer.unregisterFrameCommitCallback(callback)
                        }
                    }
                    view.invalidate()
                } else {
                    // Best effort for older/software-rendered Android after two Compose frames.
                    view.postOnAnimation(callback)
                    continuation.invokeOnCancellation { view.removeCallbacks(callback) }
                }
            }
        } == true
        Log.i("KomeliaEInk", "Final frame submitted=$frameSubmitted")
    }

    override suspend fun refresh(mode: EInkRefreshMode) = withContext(Dispatchers.Main.immediate) {
        if (frameSubmitted && !isTouchActive.value && view.isAttachedToWindow && view.hasWindowFocus() && view.isShown) {
            BigmeEInkApi.refresh(mode)
        }
        // Repeated clean cycles belong to the same submitted frame. The scheduler
        // cancels the remaining cycles when content or input changes.
    }
}
