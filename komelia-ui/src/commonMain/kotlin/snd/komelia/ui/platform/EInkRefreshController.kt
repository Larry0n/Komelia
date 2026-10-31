package snd.komelia.ui.platform

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import snd.komelia.settings.model.EInkRefreshMode

interface EInkRefreshController {
    val isSupported: StateFlow<Boolean>
    val isTouchActive: StateFlow<Boolean>

    /** Wait for the platform to submit the final frame; cancellation must not refresh. */
    suspend fun awaitFrameSubmission()
    suspend fun refresh(mode: EInkRefreshMode)
}

object NoOpEInkRefreshController : EInkRefreshController {
    override val isSupported: StateFlow<Boolean> = MutableStateFlow(false)
    override val isTouchActive: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun awaitFrameSubmission() = Unit
    override suspend fun refresh(mode: EInkRefreshMode) = Unit
}

@Composable
expect fun rememberEInkRefreshController(): EInkRefreshController
