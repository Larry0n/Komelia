package snd.komelia.ui.reader.image.common

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import snd.komelia.image.KomeliaImage
import snd.komelia.image.ReaderImage
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class EInkRefreshCoordinatorTest {
    @Test
    fun doubleSpreadWaitsForBothBitmapsAndLatestDrawnPainter() = runTest {
        var count = 0
        val refresh = EInkRefreshCoordinator(this, { true }, {}, { count++ })
        val left = Image(1)
        val right = Image(2)
        right.isReadyForRefresh = false
        refresh.registerImage("left", left)
        refresh.registerImage("right", right)
        refresh.onImagePositioned("left", Rect(0f, 0f, 100f, 100f))
        refresh.onImagePositioned("right", Rect(100f, 0f, 200f, 100f))
        refresh.onImageDrawn("left", left.painter.value!!)
        refresh.onImageDrawn("right", right.painter.value!!)
        advanceTimeBy(500)
        assertEquals(0, count)
        right.isReadyForRefresh = true
        right.painter.value = TestPainter()
        advanceTimeBy(500)
        assertEquals(0, count) // new bitmap exists but Compose has not drawn it yet
        refresh.onImageDrawn("right", right.painter.value!!)
        advanceTimeBy(121)
        assertEquals(1, count)
        refresh.dispose()
    }

    @Test
    fun identicalRedrawDoesNotCauseARefreshLoop() = runTest {
        var count = 0
        val refresh = EInkRefreshCoordinator(this, { true }, {}, { count++ })
        val image = Image(1)
        val bounds = Rect(0f, 0f, 100f, 100f)
        refresh.registerImage("page", image)
        refresh.onImagePositioned("page", bounds)
        refresh.onImageDrawn("page", image.painter.value!!)
        advanceTimeBy(121)
        assertEquals(1, count)
        repeat(10) {
            refresh.onImagePositioned("page", bounds)
            refresh.onImageDrawn("page", image.painter.value!!)
            advanceTimeBy(121)
        }
        assertEquals(1, count)
        refresh.dispose()
    }

    @Test
    fun offscreenPrefetchCannotTriggerOrBlockRefresh() = runTest {
        var count = 0
        val refresh = EInkRefreshCoordinator(this, { true }, {}, { count++ })
        val visible = Image(1)
        val prefetched = Image(2).apply { isReadyForRefresh = false }
        refresh.registerImage("visible", visible)
        refresh.registerImage("prefetched", prefetched)
        refresh.onImageDrawn("prefetched", prefetched.painter.value!!)
        advanceTimeBy(500)
        assertEquals(0, count)
        refresh.onImagePositioned("visible", Rect(0f, 0f, 100f, 100f))
        refresh.onImageDrawn("visible", visible.painter.value!!)
        advanceTimeBy(121)
        assertEquals(1, count)
        refresh.dispose()
    }

    private class TestPainter : Painter() {
        override val intrinsicSize = Size(100f, 100f)
        override fun DrawScope.onDraw() = Unit
    }

    private class Image(page: Int) : ReaderImage {
        override val pageId = ReaderImage.PageId("book", page)
        override val originalSize = MutableStateFlow<IntSize?>(IntSize(100, 100))
        override val displaySize = MutableStateFlow<IntSize?>(IntSize(100, 100))
        override val currentSize = MutableStateFlow<IntSize?>(IntSize(100, 100))
        override val painter = MutableStateFlow<Painter?>(TestPainter())
        override val error = MutableStateFlow<Throwable?>(null)
        override var isReadyForRefresh = true
        override fun requestUpdate(maxDisplaySize: IntSize, zoomFactor: Float, visibleDisplaySize: IntRect) = Unit
        override suspend fun getOriginalImageSize() = Result.success(IntSize(100, 100))
        override suspend fun getOriginalImage(): Result<KomeliaImage> = kotlin.error("Not used")
        override fun close() = Unit
    }
}
