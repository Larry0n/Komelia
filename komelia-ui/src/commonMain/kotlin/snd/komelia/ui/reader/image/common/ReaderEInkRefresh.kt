package snd.komelia.ui.reader.image.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.drop
import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komelia.settings.model.ReaderType
import snd.komelia.ui.platform.rememberEInkRefreshController
import snd.komelia.ui.reader.image.ReaderState
import snd.komelia.ui.reader.image.ScreenScaleState
import snd.komelia.ui.reader.image.continuous.ContinuousReaderState
import snd.komelia.ui.reader.image.paged.PagedReaderState
import snd.komelia.ui.reader.image.panels.PanelsReaderState
import snd.komelia.ui.reader.image.settings.EInkHardwareSettingsState
import snd.komelia.ui.reader.image.settings.LocalEInkHardwareSettings

@Composable
fun ReaderEInkRefresh(
    state: ReaderState,
    paged: PagedReaderState,
    continuous: ContinuousReaderState,
    panels: PanelsReaderState?,
    scale: ScreenScaleState,
    overlayVisible: Boolean,
    content: @Composable () -> Unit,
) {
    val controller = rememberEInkRefreshController()
    val supported by controller.isSupported.collectAsState()
    val enabled by state.hardwareEInkRefresh.collectAsState()
    val mode by state.hardwareEInkRefreshMode.collectAsState()
    val delayMillis by state.hardwareEInkRefreshDelay.collectAsState()
    val refreshCount by state.hardwareEInkRefreshCount.collectAsState()
    val readerType by state.readerType.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(supported, enabled, mode, delayMillis, refreshCount) {
        KotlinLogging.logger("KomeliaEInk").info {
            "Reader refresh supported=$supported enabled=$enabled mode=$mode delay=${delayMillis}ms count=$refreshCount"
        }
    }
    val coordinator = remember(controller, supported, enabled, mode, delayMillis, refreshCount, readerType, scale) {
        if (!supported || !enabled) null else EInkRefreshCoordinator(
            scope = scope,
            isReaderReady = {
                !controller.isTouchActive.value && scale.activeAnimations.value == 0 && when (readerType) {
                    ReaderType.PAGED -> {
                        val expected = paged.pageSpreads.value.getOrNull(paged.currentSpreadIndex.value)
                        paged.imageUpdateTransformation.value == scale.transformation.value &&
                            paged.transitionPage.value == null && !expected.isNullOrEmpty() &&
                            paged.currentSpread.value.pages.map { it.metadata } == expected
                    }
                    ReaderType.PANELS -> panels != null &&
                        panels.imageUpdateTransformation.value == scale.transformation.value &&
                        panels.transitionPage.value == null &&
                        panels.currentPage.value?.metadata ==
                        panels.pageMetadata.value.getOrNull(panels.currentPageIndex.value.page)
                    ReaderType.CONTINUOUS -> !continuous.lazyListState.isScrollInProgress &&
                        continuous.areVisibleImageRequestsCurrent()
                }
            },
            awaitFinalFrame = {
                // The first callback may precede recomposition/layout/draw of the change.
                withFrameNanos { }
                withFrameNanos { }
                controller.awaitFrameSubmission()
            },
            refresh = { controller.refresh(mode) },
            settleDelayMillis = delayMillis,
            refreshCount = refreshCount,
        )
    }
    DisposableEffect(coordinator) { onDispose { coordinator?.dispose() } }
    val overlayBlocker = remember { Any() }
    SideEffect { coordinator?.setBlocked(overlayBlocker, overlayVisible, discardPending = true) }
    LaunchedEffect(coordinator, scale) {
        val refresh = coordinator ?: return@LaunchedEffect
        scale.transformation.drop(1).collect { refresh.requestRefresh() }
    }
    LaunchedEffect(coordinator, scale) {
        val refresh = coordinator ?: return@LaunchedEffect
        val owner = Any()
        scale.activeAnimations.collect { refresh.setBlocked(owner, it > 0) }
    }
    LaunchedEffect(coordinator, controller) {
        val refresh = coordinator ?: return@LaunchedEffect
        val owner = Any()
        controller.isTouchActive.collect { refresh.setBlocked(owner, it) }
    }
    LaunchedEffect(coordinator, readerType) {
        val refresh = coordinator ?: return@LaunchedEffect
        if (readerType != ReaderType.CONTINUOUS) return@LaunchedEffect
        val list = continuous.lazyListState
        snapshotFlow {
            list.layoutInfo.visibleItemsInfo.map { Triple(it.key, it.offset, it.size) }
        }.drop(1).collect { refresh.requestRefresh() }
    }
    CompositionLocalProvider(
        LocalEInkRefreshCoordinator provides coordinator,
        LocalEInkHardwareSettings provides EInkHardwareSettingsState(
            supported = supported,
            enabled = enabled,
            mode = mode,
            delayMillis = delayMillis,
            refreshCount = refreshCount,
            onEnabledChange = state::onHardwareEInkRefreshChange,
            onModeChange = state::onHardwareEInkRefreshModeChange,
            onDelayChange = state::onHardwareEInkRefreshDelayChange,
            onCountChange = state::onHardwareEInkRefreshCountChange,
        ),
        content = content,
    )
}

/** Observe the initial pass so even a held finger with no motion blocks a pending refresh. */
fun Modifier.eInkTouchTracking(coordinator: EInkRefreshCoordinator?): Modifier {
    if (coordinator == null) return this
    return pointerInput(coordinator) {
        val owner = Any()
        try {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                coordinator.setBlocked(owner, true)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                coordinator.setBlocked(owner, false)
            }
        } finally {
            coordinator.setBlocked(owner, false)
        }
    }
}
