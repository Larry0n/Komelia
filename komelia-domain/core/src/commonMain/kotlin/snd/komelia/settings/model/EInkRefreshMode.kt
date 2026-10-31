package snd.komelia.settings.model

/** Only arguments observed in Bigme's own firmware, without guessed waveform flags. */
enum class EInkRefreshMode(val argument: Int) {
    SYSTEM(0),
    GU16(132),
    // Bigme's Activity receiver for com.xrz.GlobalRefresh uses this argument.
    MANUAL(4)
}
