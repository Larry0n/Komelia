package snd.komelia.ui.platform

import android.util.Log
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Main-thread window input, before Compose gesture consumption or node replacement. */
object AndroidWindowInputState {
    private val active = MutableStateFlow(false)
    val isTouchActive: StateFlow<Boolean> = active
    private var pressedPointers = 0

    fun onTouchEvent(event: MotionEvent) {
        val remaining = when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 0
            MotionEvent.ACTION_POINTER_UP -> event.pointerCount - 1
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> event.pointerCount
            else -> return
        }
        setPressedPointers(remaining)
    }

    fun reset() = setPressedPointers(0)

    private fun setPressedPointers(count: Int) {
        if (pressedPointers == count) return
        pressedPointers = count
        active.value = count > 0
        Log.i("KomeliaEInk", "Window pressed pointers=$count")
    }
}
